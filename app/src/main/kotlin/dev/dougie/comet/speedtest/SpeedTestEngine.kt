package dev.dougie.comet.speedtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class SpeedTestConfig(
    val pingSamples: Int = 10,
    /** Parallel TCP connections per throughput phase. */
    val streams: Int = 5,
    val phaseDurationMs: Long = 10_000,
    /** Slow-start period excluded from the result. */
    val warmupMs: Long = 2_000,
    val tickMs: Long = 100,
    val downloadChunkMb: Int = 100,
    val uploadChunkBytes: Long = 25L * 1024 * 1024,
) {
    init {
        require(streams in 1..8)
        require(warmupMs < phaseDurationMs)
    }
}

/** Parses user input into a base URL ending in "/"; null if it is not an http(s) URL. */
fun parseServerUrl(input: String): HttpUrl? {
    val url = input.trim().toHttpUrlOrNull() ?: return null
    return if (url.encodedPath.endsWith("/")) url else url.newBuilder().addPathSegment("").build()
}

/**
 * Talks to a LibreSpeed backend: `empty.php` (ping + upload sink) and `garbage.php` (download).
 */
class SpeedTestEngine(
    private val client: OkHttpClient = defaultClient(),
    private val config: SpeedTestConfig = SpeedTestConfig(),
) {
    private val uploadPayload = Random.nextBytes(64 * 1024) // incompressible

    fun run(server: HttpUrl): Flow<TestState> = channelFlow {
        var state = TestState(phase = Phase.Ping)
        val publish: suspend (TestState) -> Unit = { state = it; send(it) }
        publish(state)
        try {
            val ping = measurePing(server) { progress, medianSoFar ->
                publish(state.copy(progress = progress, pingMs = medianSoFar))
            }
            publish(state.copy(phase = Phase.Download, pingMs = ping.first, jitterMs = ping.second, progress = 0f))

            val down = measureThroughput(Direction.Download, server) { mbps, progress ->
                publish(state.copy(liveMbps = mbps, progress = progress))
            }
            publish(state.copy(phase = Phase.Upload, downloadMbps = down, liveMbps = 0.0, progress = 0f))

            val up = measureThroughput(Direction.Upload, server) { mbps, progress ->
                publish(state.copy(liveMbps = mbps, progress = progress))
            }
            publish(state.copy(phase = Phase.Done, uploadMbps = up, liveMbps = 0.0, progress = 1f))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            publish(state.copy(phase = Phase.Failed, liveMbps = 0.0, error = e.message ?: "Network error"))
        }
    }

    /** Returns (median RTT ms, jitter ms). The first request, which pays for DNS/TLS, is discarded. */
    private suspend fun measurePing(
        server: HttpUrl,
        onProgress: suspend (Float, Double) -> Unit,
    ): Pair<Double, Double> {
        val samples = ArrayList<Double>()
        repeat(config.pingSamples + 1) { i ->
            val call = client.newCall(get(server, "empty.php?r=${nonce()}"))
            val start = System.nanoTime()
            val arrived = call.await { System.nanoTime() }
            if (i > 0) {
                samples += (arrived - start) / 1e6
                onProgress(samples.size / config.pingSamples.toFloat(), Stats.median(samples))
            }
        }
        return Stats.median(samples) to Stats.jitter(samples)
    }

    private suspend fun measureThroughput(
        direction: Direction,
        server: HttpUrl,
        onTick: suspend (liveMbps: Double, progress: Float) -> Unit,
    ): Double = coroutineScope {
        val bytes = AtomicLong()
        val errors = ConcurrentLinkedQueue<IOException>()
        val workers = List(config.streams) {
            launch {
                while (isActive) {
                    try {
                        when (direction) {
                            Direction.Download -> downloadOnce(server, bytes)
                            Direction.Upload -> uploadOnce(server, bytes)
                        }
                    } catch (e: IOException) {
                        if (isActive) errors += e
                        return@launch
                    }
                }
            }
        }

        val meter = ThroughputMeter(config.warmupMs)
        val start = System.nanoTime()
        while (true) {
            delay(config.tickMs)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            meter.record(elapsedMs, bytes.get())
            onTick(meter.liveMbps(), (elapsedMs.toFloat() / config.phaseDurationMs).coerceAtMost(1f))
            if (elapsedMs >= config.phaseDurationMs || workers.all { it.isCompleted }) break
        }
        workers.forEach { it.cancel() }

        if (bytes.get() == 0L) throw errors.peek() ?: IOException("No data transferred")
        meter.result() ?: throw IOException("Test too short to measure")
    }

    private suspend fun downloadOnce(server: HttpUrl, bytes: AtomicLong) {
        val request = get(server, "garbage.php?ckSize=${config.downloadChunkMb}&r=${nonce()}")
        client.newCall(request).await { response ->
            val source = response.body.source()
            val sink = Buffer()
            while (true) {
                val n = source.read(sink, 64 * 1024)
                if (n < 0) break
                sink.clear()
                bytes.addAndGet(n)
            }
        }
    }

    private suspend fun uploadOnce(server: HttpUrl, bytes: AtomicLong) {
        val request = Request.Builder()
            .url(server.resolve("empty.php?r=${nonce()}")!!)
            .header("Cache-Control", "no-cache")
            .post(CountingBody(bytes))
            .build()
        client.newCall(request).await { }
    }

    private inner class CountingBody(private val counter: AtomicLong) : RequestBody() {
        override fun contentType(): MediaType = "application/octet-stream".toMediaType()
        override fun contentLength() = config.uploadChunkBytes
        override fun writeTo(sink: BufferedSink) {
            var left = config.uploadChunkBytes
            while (left > 0) {
                val n = minOf(left, uploadPayload.size.toLong()).toInt()
                sink.write(uploadPayload, 0, n)
                counter.addAndGet(n.toLong())
                left -= n
            }
        }
    }

    private enum class Direction { Download, Upload }

    private fun get(server: HttpUrl, path: String) = Request.Builder()
        .url(server.resolve(path)!!)
        .header("Cache-Control", "no-cache")
        // Without this OkHttp negotiates gzip and transparently inflates, skewing byte counts.
        .header("Accept-Encoding", "identity")
        .build()

    private fun nonce() = Random.nextLong().toULong().toString(16)

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            // HTTP/2 would multiplex every "parallel" stream over one TCP connection.
            .protocols(listOf(Protocol.HTTP_1_1))
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 64 })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/** Runs the call, handling the response on OkHttp's thread; cancelling the coroutine cancels the call. */
private suspend fun <T> Call.await(handle: (Response) -> T): T = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    if (!it.isSuccessful) throw IOException("Server returned ${it.code}")
                    handle(it)
                }
            }
            result.fold(cont::resume, cont::resumeWithException)
        }
    })
}
