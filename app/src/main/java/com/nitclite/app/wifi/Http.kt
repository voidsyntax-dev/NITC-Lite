package com.nitclite.app.wifi

import android.annotation.SuppressLint
import android.net.Network
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Tiny HTTP client bound to one network (so it works even when Android routes traffic over mobile data). */
class Http(private val net: Network) {
    /** Campus portals often use self-signed certificates; only switched on after the network is confirmed as campus. */
    var trustAll = false
    private val cookies = HashMap<String, MutableMap<String, String>>()

    class Resp(val url: URL, val code: Int, val body: String, val location: String?)

    sealed class Probe {
        object Online : Probe()
        class Captive(val url: URL, val body: String?) : Probe()
        class Failed(val why: String) : Probe()
    }

    fun request(url: URL, post: String? = null, referer: String? = null): Resp {
        val c = net.openConnection(url) as HttpURLConnection
        if (c is HttpsURLConnection && trustAll) {
            c.sslSocketFactory = looseTls
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        c.instanceFollowRedirects = false
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.useCaches = false
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) NITCLite/1.0")
        cookies[url.host]?.takeIf { it.isNotEmpty() }?.let { jar ->
            c.setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        referer?.let { c.setRequestProperty("Referer", it) }
        try {
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = c.responseCode
            c.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }.forEach { sc ->
                val kv = sc.substringBefore(';')
                val i = kv.indexOf('=')
                if (i > 0) cookies.getOrPut(url.host) { HashMap() }[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
            }
            val stream = try { if (code >= 400) c.errorStream else c.inputStream } catch (e: IOException) { null }
            val body = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return Resp(url, code, body, c.getHeaderField("Location"))
        } finally {
            c.disconnect()
        }
    }

    fun get(url: URL): Resp {
        var u = url
        repeat(6) {
            val r = request(u)
            if (r.code in 300..399 && r.location != null) u = URL(u, r.location) else return r
        }
        return request(u)
    }

    fun post(url: URL, body: String, referer: String): Resp {
        val r = request(url, body, referer)
        return if (r.code in 300..399 && r.location != null) get(URL(url, r.location)) else r
    }

    fun submit(f: Html.Form, body: String, referer: URL): Resp =
        if (f.method == "get") {
            val a = f.action.toString().substringBefore('?')
            get(URL("$a?$body"))
        } else post(f.action, body, referer.toString())

    fun probe(): Probe {
        val byName = try {
            val base = URL(PROBE)
            val r = request(base)
            when {
                r.code == 204 -> Probe.Online
                r.code in 300..399 && r.location != null -> Probe.Captive(URL(base, r.location), null)
                else -> {
                    val js = Html.redirect(r.body)
                    when {
                        js != null -> Probe.Captive(URL(base, js), null)
                        Html.forms(r.body, base).isNotEmpty() -> Probe.Captive(base, r.body)
                        else -> Probe.Failed("HTTP ${r.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Probe.Failed(e.javaClass.simpleName)
        }
        if (byName !is Probe.Failed) return byName
        // Before sign-in, DNS often fails (portal blocks it, or Private DNS is on). A request to a bare IP needs no DNS,
        // and portals like pfSense intercept it and redirect to their login page.
        return when (val byIp = probeByIp()) {
            is Probe.Failed -> Probe.Failed(byName.why)
            else -> byIp
        }
    }

    private fun probeByIp(): Probe = try {
        val base = URL(IP_PROBE)
        val r = request(base)
        val loc = r.location?.let { URL(base, it) }
        val js = Html.redirect(r.body)
        when {
            loc != null && loc.host == base.host -> Probe.Online // Cloudflare upgrading us to https: real internet
            loc != null -> Probe.Captive(loc, null)
            js != null -> Probe.Captive(URL(base, js), null)
            Html.forms(r.body, base).any { it.hasPassword } -> Probe.Captive(base, r.body)
            r.code in 200..299 -> Probe.Online
            else -> Probe.Failed("HTTP ${r.code}")
        }
    } catch (e: Exception) {
        Probe.Failed(e.javaClass.simpleName)
    }

    companion object {
        const val PROBE = "http://connectivitycheck.gstatic.com/generate_204"
        const val IP_PROBE = "http://1.1.1.1/"

        private val looseTls: SSLSocketFactory by lazy {
            @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }.socketFactory
        }
    }
}
