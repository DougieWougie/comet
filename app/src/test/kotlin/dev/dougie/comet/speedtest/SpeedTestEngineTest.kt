package dev.dougie.comet.speedtest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedTestEngineTest {
    private val zeros = ByteArray(2 * 1024 * 1024)
    private lateinit var server: MockWebServer
    private val config = SpeedTestConfig(pingSamples = 4, streams = 4, phaseDurationMs = 1_500, warmupMs = 300, downloadChunkMb = 1, uploadChunkBytes = 2L * 1024 * 1024)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path == "/garbage.php" -> {
                        val n = request.url.queryParameter("ckSize")!!.toLong() * 1024 * 1024
                        MockResponse.Builder().body(Buffer().write(zeros, 0, n.toInt())).build()
                    }
                    path == "/empty.php" -> MockResponse.Builder().build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() = server.close()

    @Test fun fullRunProducesAllMeasurements() = runBlocking {
        val states = SpeedTestEngine(config = config).run(server.url("/")).toList()
        val last = states.last()
        assertEquals(Phase.Done, last.phase)
        assertNotNull(last.pingMs); assertNotNull(last.jitterMs)
        assertTrue("download ${last.downloadMbps}", last.downloadMbps!! > 0)
        assertTrue("upload ${last.uploadMbps}", last.uploadMbps!! > 0)
        assertEquals(listOf(Phase.Ping, Phase.Download, Phase.Upload, Phase.Done), states.map { it.phase }.distinct())
    }

    @Test fun unreachableServerFails() = runBlocking {
        val url = server.url("/")
        server.close()
        val last = SpeedTestEngine(config = config).run(url).toList().last()
        assertEquals(Phase.Failed, last.phase)
        assertNotNull(last.error)
    }

    @Test fun serverErrorFails() = runBlocking {
        val last = SpeedTestEngine(config = config).run(server.url("/nope/")).toList().last()
        assertEquals(Phase.Failed, last.phase)
        assertTrue(last.error!!.contains("404"))
    }

    @Test fun serverUrlGetsTrailingSlash() {
        assertEquals("https://speed.example.com/x/", parseServerUrl("https://speed.example.com/x").toString())
        assertEquals(null, parseServerUrl("not a url"))
    }

    @Test fun serverUrlWithCredentialsIsRejected() {
        assertEquals(null, parseServerUrl("https://user:pass@speed.example.com/"))
        assertEquals(null, parseServerUrl("https://user@speed.example.com/"))
    }
}
