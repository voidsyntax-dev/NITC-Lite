package com.nitclite.app.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import org.json.JSONObject
import java.time.LocalDate
import kotlin.concurrent.thread

object Backup {
    fun fileName() = "nitclite-${LocalDate.now()}.json"

    fun exportJson(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(Json.encode(Store.data).toString(2).toByteArray(Charsets.UTF_8))
        }
        Store.update { it.copy(lastBackup = System.currentTimeMillis()) }
        "Backup saved"
    } catch (e: Exception) {
        "Backup failed: ${e.message}"
    }

    /** Spreadsheet-friendly copy: a summary block, then every class. */
    fun exportCsv(ctx: Context, uri: Uri): String = try {
        val d = Store.data
        val stats = Attendance.stats(d)
        val byId = d.courses.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("Course code,Course,Attended,Total,Percent\n")
        d.courses.forEach { c ->
            val s = stats[c.id]!!
            sb.append("${csv(c.code)},${csv(c.name)},${s.attended},${s.total},${"%.1f".format(s.pct)}\n")
        }
        sb.append("\nDate,Time,Course code,Course,Status\n")
        d.records.entries.mapNotNull { (k, v) -> Session.parse(k)?.let { it to v } }
            .sortedWith(compareBy({ it.first.date }, { it.first.start }))
            .forEach { (s, v) ->
                val c = byId[s.courseId] ?: return@forEach
                val status = when (v) { PRESENT -> "Present"; ABSENT -> "Absent"; else -> "Off" }
                sb.append("${s.date},${"%02d:%02d".format(s.start / 60, s.start % 60)},${csv(c.code)},${csv(c.name)},$status\n")
            }
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        "CSV saved"
    } catch (e: Exception) {
        "Export failed: ${e.message}"
    }

    fun import(ctx: Context, uri: Uri): String = try {
        val txt = ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
        val o = JSONObject(txt)
        require(o.has("courses") && o.has("records")) { "not an NITC Lite backup" }
        val cur = Store.data
        Store.replace(Json.decode(o).copy(backupDir = cur.backupDir, lastAutoBackup = cur.lastAutoBackup))
        "Restored ${Store.data.courses.size} courses and ${Store.data.records.size} records"
    } catch (e: Exception) {
        "Restore failed: ${e.message}"
    }

    /** Once a day, drop a dated copy into the folder the user picked. */
    fun auto(ctx: Context) {
        val d = Store.data
        val dir = d.backupDir ?: return
        val today = LocalDate.now().toString()
        if (d.lastAutoBackup == today || d.courses.isEmpty()) return
        val json = Json.encode(d).toString(2)
        val app = ctx.applicationContext
        thread {
            try {
                val tree = Uri.parse(dir)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(app.contentResolver, parent, "application/json", "nitclite-$today.json")
                    ?: return@thread
                app.contentResolver.openOutputStream(doc)!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Handler(Looper.getMainLooper()).post {
                    Store.update { it.copy(lastAutoBackup = today, lastBackup = System.currentTimeMillis()) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun csv(s: String) = if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
