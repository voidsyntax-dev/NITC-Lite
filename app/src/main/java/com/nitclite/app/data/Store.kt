package com.nitclite.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors

/** Single source of truth. Lives in memory, written to one JSON file on every change. */
object Store {
    private var file: AtomicFile? = null
    private val io = Executors.newSingleThreadExecutor()

    var data by mutableStateOf(AppData())
        private set

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = AtomicFile(File(ctx.filesDir, "data.json"))
        file = f
        data = try {
            Json.decode(JSONObject(String(f.readFully(), Charsets.UTF_8)))
        } catch (e: Exception) {
            AppData()
        }
        finalizeDays()
    }

    private fun persist() {
        val f = file ?: return
        val snap = data
        io.execute {
            val bytes = Json.encode(snap).toString().toByteArray(Charsets.UTF_8)
            var out: FileOutputStream? = null
            try {
                out = f.startWrite()
                out.write(bytes)
                f.finishWrite(out)
            } catch (e: Exception) {
                if (out != null) f.failWrite(out)
            }
        }
    }

    fun update(fn: (AppData) -> AppData) {
        data = fn(data)
        persist()
    }

    fun replace(d: AppData) {
        data = d
        persist()
        finalizeDays()
    }

    /** End-of-day rule: every past class nobody marked counts as present. */
    fun finalizeDays(today: LocalDate = LocalDate.now()) {
        val d = data
        val end = today.minusDays(1)
        var day = LocalDate.parse(d.finalizedThrough).plusDays(1)
        if (day.isAfter(end)) return
        val recs = HashMap(d.records)
        var n = 0
        while (!day.isAfter(end) && n++ < 400) {
            for (s in Attendance.sessions(d, day)) recs.putIfAbsent(s.key, PRESENT)
            day = day.plusDays(1)
        }
        update { it.copy(records = recs, finalizedThrough = end.toString()) }
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ---- attendance ----

    fun mark(s: Session, status: Char?) = update { d ->
        val r = HashMap(d.records)
        if (status == null) {
            if (s.date > d.finalizedThrough) r.remove(s.key)
        } else r[s.key] = status
        d.copy(records = r)
    }

    fun markDay(date: LocalDate, status: Char) = update { d ->
        val r = HashMap(d.records)
        Attendance.sessions(d, date).forEach { r[it.key] = status }
        d.copy(records = r)
    }

    fun addExtra(e: Extra) = update { it.copy(extras = it.extras + e) }

    fun removeExtra(s: Session) = update { d ->
        d.copy(
            extras = d.extras.filterNot { it.courseId == s.courseId && it.date == s.date && it.start == s.start },
            records = d.records - s.key,
        )
    }

    /** Make a weekend (or any day) follow another weekday's timetable, or clear with null. */
    fun setOverride(date: LocalDate, dow: Int?) = update { d ->
        val ds = date.toString()
        val ov = HashMap(d.overrides)
        if (dow == null) ov.remove(ds) else ov[ds] = dow
        var next = d.copy(overrides = ov)
        if (ds <= d.finalizedThrough) {
            // Settled day: rebuild its records from the new timetable.
            val r = HashMap(d.records.filterKeys { !it.startsWith("$ds|") })
            val probe = next.copy(records = emptyMap(), finalizedThrough = date.minusDays(1).toString())
            Attendance.sessions(probe, date).forEach { r[it.key] = d.records[it.key] ?: PRESENT }
            next = next.copy(records = r)
        }
        next
    }

    // ---- courses ----

    fun upsertCourse(c: Course) = update { d ->
        val list = if (d.courses.any { it.id == c.id }) d.courses.map { if (it.id == c.id) c else it } else d.courses + c
        d.copy(courses = list)
    }

    fun deleteCourse(id: String) = update { d ->
        d.copy(
            courses = d.courses.filterNot { it.id == id },
            records = d.records.filterKeys { it.split('|').getOrNull(1) != id },
            extras = d.extras.filterNot { it.courseId == id },
            tasks = d.tasks.map { if (it.courseId == id) it.copy(courseId = null) else it },
        )
    }

    fun setSemStart(date: LocalDate) {
        update { d ->
            val ds = date.toString()
            val prev = date.minusDays(1).toString()
            d.copy(
                semStart = ds,
                finalizedThrough = minOf(d.finalizedThrough, prev),
                records = d.records.filterKeys { it >= ds },
            )
        }
        finalizeDays()
    }

    fun newSemester(today: LocalDate = LocalDate.now()) = update { it.newSemester(today) }

    // ---- tasks ----

    fun upsertTask(t: Task) = update { d ->
        val list = if (d.tasks.any { it.id == t.id }) d.tasks.map { if (it.id == t.id) t else it } else d.tasks + t
        d.copy(tasks = list)
    }

    fun deleteTask(id: String) = update { d -> d.copy(tasks = d.tasks.filterNot { it.id == id }) }
}
