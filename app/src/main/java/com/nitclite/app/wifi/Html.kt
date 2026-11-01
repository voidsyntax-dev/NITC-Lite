package com.nitclite.app.wifi

import java.net.URL
import java.net.URLEncoder

/** Just enough HTML reading to find and fill a captive-portal login form. */
object Html {
    data class Input(val type: String, val name: String, val value: String)
    data class Form(val action: URL, val method: String, val inputs: List<Input>) {
        val hasPassword get() = inputs.any { it.type == "password" }
    }

    private val I = RegexOption.IGNORE_CASE
    private val FORM = Regex("<form\\b([^>]*)>(.*?)(?:</form>|$)", setOf(I, RegexOption.DOT_MATCHES_ALL))
    private val INPUT = Regex("<(input|button)\\b([^>]*)>", I)
    private val REDIRECTS = listOf(
        Regex("""location\.replace\(\s*["']([^"']+)["']""", I),
        Regex("""(?:window\.|document\.|top\.|self\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']""", I),
        Regex("""http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["']?\s*\d+\s*;\s*url\s*=\s*([^"'>\s]+)""", I),
    )
    private val USERISH = Regex("user|login|uid|email|name|id", I)

    fun attr(tag: String, name: String): String? {
        val m = Regex("(?:^|\\s)$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", I).find(tag) ?: return null
        return unescape(m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: "")
    }

    fun inputs(s: String): List<Input> = INPUT.findAll(s).mapNotNull {
        val isButton = it.groupValues[1].equals("button", ignoreCase = true)
        val t = it.groupValues[2]
        val name = attr(t, "name") ?: return@mapNotNull null
        val type = (attr(t, "type") ?: if (isButton) "submit" else "text").lowercase()
        Input(type, name, attr(t, "value") ?: "")
    }.toList()

    fun forms(html: String, base: URL): List<Form> {
        val found = FORM.findAll(html).map { m ->
            val a = attr(m.groupValues[1], "action")
            Form(
                if (a.isNullOrBlank()) base else URL(base, a),
                (attr(m.groupValues[1], "method") ?: "get").lowercase(),
                inputs(m.groupValues[2]),
            )
        }.toList()
        if (found.isNotEmpty()) return found
        val loose = inputs(html)
        return if (loose.any { it.type == "password" }) listOf(Form(base, "post", loose)) else emptyList()
    }

    fun redirect(html: String): String? {
        for (r in REDIRECTS) r.find(html)?.let { return unescape(it.groupValues[1]) }
        return null
    }

    /** URL-encoded body for [f] with the credentials filled in. */
    fun fill(f: Form, user: String, pass: String): String {
        val textual = setOf("text", "email", "tel")
        var userIdx = f.inputs.indexOfFirst { it.type in textual && USERISH.containsMatchIn(it.name) }
        if (userIdx < 0) userIdx = f.inputs.indexOfFirst { it.type in textual }
        val parts = ArrayList<String>()
        var submitted = false
        f.inputs.forEachIndexed { i, inp ->
            val v = when {
                i == userIdx -> user
                inp.type == "password" -> pass
                inp.type == "radio" || inp.type == "button" || inp.type == "reset" || inp.type == "file" -> return@forEachIndexed
                inp.type == "checkbox" -> inp.value.ifEmpty { "on" }
                inp.type == "submit" || inp.type == "image" -> {
                    if (submitted) return@forEachIndexed
                    submitted = true
                    inp.value
                }
                else -> inp.value
            }
            parts += enc(inp.name) + "=" + enc(v)
        }
        return parts.joinToString("&")
    }

    fun looksFailed(body: String): Boolean {
        val text = body.replace(Regex("<[^>]*>"), " ")
        return Regex("invalid|incorrect|failed|wrong password|authentication fail|not valid", I).containsMatchIn(text)
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x2F;", "/").replace("&#47;", "/").replace("&lt;", "<").replace("&gt;", ">")
}
