package dev.dougie.comet.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Asks ipinfo.io who we are. This necessarily discloses the device's public IP to a third party,
 * so it only ever runs from an explicit tap.
 */
class PublicIpLookup(private val client: OkHttpClient) {

    suspend fun lookup(): PublicIp = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ENDPOINT).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Lookup failed (${response.code})")
            parse(response.body.string())
        }
    }

    companion object {
        const val ENDPOINT = "https://ipinfo.io/json"
        const val HOST = "ipinfo.io"

        fun parse(json: String): PublicIp {
            val o = JSONObject(json)
            val org = o.optString("org").ifBlank { null }
            // org looks like "AS13335 Cloudflare, Inc."
            val asn = org?.takeIf { it.startsWith("AS") }?.substringBefore(' ')
            val isp = if (asn != null) org.substringAfter(' ', "").ifBlank { null } else org
            return PublicIp(
                ip = o.getString("ip"),
                isp = isp,
                asn = asn,
                city = o.optString("city").ifBlank { null },
                country = o.optString("country").ifBlank { null },
            )
        }
    }
}
