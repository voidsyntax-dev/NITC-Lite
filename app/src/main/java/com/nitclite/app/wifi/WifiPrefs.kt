package com.nitclite.app.wifi

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** WiFi settings. Kept in their own prefs file, which is excluded from cloud backup. */
class WifiPrefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("wifi", Context.MODE_PRIVATE)

    var user: String
        get() = p.getString("user", "")!!
        set(v) = p.edit().putString("user", v.trim()).apply()
    var pass: String
        get() = p.getString("pass", "")!!
        set(v) = p.edit().putString("pass", v).apply()
    var enabled: Boolean
        get() = p.getBoolean("enabled", true)
        set(v) = p.edit().putBoolean("enabled", v).apply()
    var networks: String
        get() = p.getString("networks", DEFAULT_NETWORKS)!!
        set(v) = p.edit().putString("networks", v).apply()
    var learnedHost: String?
        get() = p.getString("learnedHost", null)
        set(v) = p.edit().putString("learnedHost", v).apply()
    var learnedPage: String?
        get() = p.getString("learnedPage", null)
        set(v) = p.edit().putString("learnedPage", v).apply()

    val log: List<String> get() = p.getString("log", "")!!.split('\n').filter { it.isNotBlank() }

    fun addLog(msg: String) {
        val line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, h:mm a")) + "  ·  " + msg
        p.edit().putString("log", (listOf(line) + log).take(30).joinToString("\n")).apply()
        val main = Handler(Looper.getMainLooper())
        main.post { lastMessage.value = msg }
    }

    fun matches(ssid: String): Boolean =
        networks.split(',').map { it.trim() }.filter { it.isNotEmpty() }.any { ssid.contains(it, ignoreCase = true) }

    companion object {
        const val DEFAULT_NETWORKS = "MBH, NITC, Academic, Hostel"
        val lastMessage = mutableStateOf<String?>(null)
    }
}
