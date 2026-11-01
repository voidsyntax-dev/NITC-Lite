package com.nitclite.app.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.nitclite.app.MainActivity
import com.nitclite.app.R
import java.net.Inet4Address
import java.net.URL

/**
 * Campus WiFi sign-in.
 * 1. Only acts on WiFi whose name matches the campus list (MBH…, NITC…). If Android hides the name
 *    (no location permission), it only trusts login pages on campus-style hosts (private IPs, nitc.ac.in).
 * 2. Probes generate_204. A redirect or an HTML page means a login portal sits in the way.
 * 3. Reads the portal's login form (Fortinet, Sophos/Cyberoam, or generic), fills it and submits.
 * 4. Probes again to confirm we are online.
 */
object Portal {
    @Volatile private var lastAuto = 0L

    enum class Mode { MANUAL, ON_CONNECT, PERIODIC }

    fun wifiNetwork(ctx: Context): Network? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    fun ssid(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        val s = wm?.connectionInfo?.ssid ?: return null
        if (s == WifiManager.UNKNOWN_SSID || s.isBlank() || s == "<unknown ssid>") return null
        return s.trim('"')
    }

    /** Quick read-only check for the WiFi screen. */
    fun status(ctx: Context): String {
        val net = wifiNetwork(ctx) ?: return "Not on WiFi"
        return when (val p = Http(net).probe()) {
            is Http.Probe.Online -> "Online"
            is Http.Probe.Captive -> "Sign-in needed (${p.url.host})"
            is Http.Probe.Failed -> "No internet (${p.why})"
        }
    }

    @Synchronized
    fun run(ctx: Context, mode: Mode): String {
        val prefs = WifiPrefs(ctx)
        val manual = mode == Mode.MANUAL
        if (mode == Mode.ON_CONNECT) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAuto < 10_000) return "Skipped"
            lastAuto = now
        }
        val net = wifiNetwork(ctx) ?: return finish(ctx, prefs, "Not connected to WiFi", log = manual)
        val ssid = ssid(ctx)
        if (ssid != null && !prefs.matches(ssid)) return finish(ctx, prefs, "\"$ssid\" isn't in your campus network list", log = manual)
        if (prefs.user.isBlank() || prefs.pass.isBlank()) {
            return finish(ctx, prefs, "Add your WiFi username and password first", log = true, notify = !manual && mode != Mode.PERIODIC)
        }

        val http = Http(net)
        var probe = http.probe()
        if (probe is Http.Probe.Failed) {
            Thread.sleep(1500)
            probe = http.probe()
        }
        val start: URL
        var body: String? = null
        when (probe) {
            is Http.Probe.Online -> return finish(ctx, prefs, "Online" + (ssid?.let { " on $it" } ?: ""), log = manual)
            is Http.Probe.Captive -> { start = probe.url; body = probe.body }
            is Http.Probe.Failed -> {
                // Both probes were swallowed; look for the login page directly (last good page, then the router).
                val found = findPortal(ctx, net, http, prefs)
                    ?: return finish(ctx, prefs, "No response from the network (${probe.why}) and no login page found on the router", log = manual)
                start = found.first
                body = found.second
            }
        }
        if (ssid == null && !trusted(start.host, prefs)) {
            return finish(ctx, prefs, "Login page ${start.host} isn't a known campus portal. Allow location access so the app can read the WiFi name.", log = true)
        }

        http.trustAll = true
        val (page, problem) = try {
            login(http, start, body, prefs)
        } catch (e: Exception) {
            null to "Login page didn't load (${e.javaClass.simpleName})"
        }
        Thread.sleep(1200)
        if (http.probe() is Http.Probe.Online) {
            if (page != null) {
                prefs.learnedPage = page.toString()
                prefs.learnedHost = page.host
            }
            try { ctx.getSystemService(ConnectivityManager::class.java).reportNetworkConnectivity(net, true) } catch (_: Exception) {}
            return finish(ctx, prefs, "Signed in" + (ssid?.let { " to $it" } ?: ""), log = true, notify = !manual, ok = true)
        }
        return finish(ctx, prefs, problem ?: "Submitted, but still no internet. Check username and password.", log = true, notify = !manual)
    }

    /** Returns the page that held the login form, and a problem description if something looked wrong. */
    private fun login(http: Http, start: URL, startBody: String?, prefs: WifiPrefs): Pair<URL?, String?> {
        var url = start
        var html = startBody
        for (step in 0 until 6) {
            if (html == null) {
                val r = http.get(url)
                url = r.url
                html = r.body
            }
            val page = html ?: break
            if (isSophos(page, url)) return sophos(http, url, prefs)
            val forms = Html.forms(page, url)
            val pw = forms.firstOrNull { it.hasPassword }
            if (pw != null) {
                val r = http.submit(pw, Html.fill(pw, prefs.user, prefs.pass), url)
                return url to if (Html.looksFailed(r.body)) "Portal rejected the login. Check username and password." else null
            }
            val next = Html.redirect(page)
            if (next != null) {
                url = URL(url, next)
                html = null
                continue
            }
            // A form without a password field: usually an "accept terms" step before the real login.
            val any = forms.firstOrNull() ?: break
            val r = http.submit(any, Html.fill(any, prefs.user, prefs.pass), url)
            url = r.url
            html = r.body
        }
        return null to "Couldn't find a login form on ${start.host}"
    }

    private fun findPortal(ctx: Context, net: Network, http: Http, prefs: WifiPrefs): Pair<URL, String>? {
        val lp = ctx.getSystemService(ConnectivityManager::class.java).getLinkProperties(net)
        val hosts = LinkedHashSet<String>()
        lp?.routes?.forEach { r -> (r.gateway as? Inet4Address)?.takeIf { !it.isAnyLocalAddress }?.hostAddress?.let { hosts += it } }
        lp?.dnsServers?.forEach { a -> (a as? Inet4Address)?.hostAddress?.let { hosts += it } }
        // pfSense serves its first zone on :8002 (http), others on :8000; plain port 80 for everything else.
        val urls = listOfNotNull(prefs.learnedPage) + hosts.flatMap { listOf("http://$it:8002/", "http://$it:8000/", "http://$it/") }
        for (u in urls) {
            try {
                val r = http.get(URL(u))
                if (isSophos(r.body, r.url) || Html.forms(r.body, r.url).any { it.hasPassword }) return r.url to r.body
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isSophos(page: String, url: URL) =
        url.port == 8090 || url.path.contains("httpclient", ignoreCase = true) || page.contains("httpclient.html", ignoreCase = true)

    private fun sophos(http: Http, page: URL, prefs: WifiPrefs): Pair<URL?, String?> {
        val endpoint = URL(page.protocol, page.host, page.port, "/login.xml")
        val body = "mode=191&username=${Html.enc(prefs.user)}&password=${Html.enc(prefs.pass)}" +
            "&a=${System.currentTimeMillis()}&producttype=0"
        val r = http.post(endpoint, body, page.toString())
        val msg = Regex("<message><!\\[CDATA\\[(.*?)]]></message>").find(r.body)?.groupValues?.get(1)
        val bad = msg != null && !msg.contains("signed in", ignoreCase = true) && !msg.contains("logged in", ignoreCase = true)
        return page to if (bad) "Portal says: $msg" else null
    }

    private fun trusted(host: String, prefs: WifiPrefs): Boolean =
        host == prefs.learnedHost || host.endsWith("nitc.ac.in") ||
            Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)").containsMatchIn(host)

    private fun finish(ctx: Context, prefs: WifiPrefs, msg: String, log: Boolean, notify: Boolean = false, ok: Boolean = false): String {
        if (log) prefs.addLog(msg)
        if (notify) notify(ctx, if (ok) "Campus WiFi connected" else "Campus WiFi sign-in failed", msg)
        return msg
    }

    private fun notify(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("wifi", "Campus WiFi", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("tab", "wifi"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, "wifi")
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        nm.notify(1, n)
    }
}
