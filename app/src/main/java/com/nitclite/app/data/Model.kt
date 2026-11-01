package com.nitclite.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

const val PRESENT = 'P'
const val ABSENT = 'A'
const val OFF = 'C'

data class Course(
    val id: String,
    val code: String,
    val name: String,
    val slot: String,
    val plus: Boolean = false,
    val room: String = "",
    val priorAttended: Int = 0,
    val priorTotal: Int = 0,
    /** Weekly class times chosen by the user; null means "follow the slot". The + hour is separate ([plus]). */
    val custom: List<SlotTime>? = null,
    /** Required attendance % for this subject; null = the app-wide default. */
    val threshold: Int? = null,
) {
    val title get() = name.ifBlank { code }

    fun required(default: Int) = threshold ?: default
}

data class Extra(val courseId: String, val date: String, val start: Int, val dur: Int)

enum class TaskType(val label: String) { ASSIGNMENT("Assignment"), TEST("Test"), EXAM("Exam"), OTHER("Other") }

data class Task(
    val id: String,
    val title: String,
    val type: TaskType,
    val courseId: String?,
    val due: String,
    val time: Int?,
    val notes: String = "",
    val done: Boolean = false,
)

/**
 * Everything the app knows, as one immutable value.
 * records: "date|courseId|start|dur" -> PRESENT / ABSENT / OFF.
 * Days up to [finalizedThrough] are settled: their unmarked classes were written as PRESENT.
 */
data class AppData(
    val courses: List<Course> = emptyList(),
    val records: Map<String, Char> = emptyMap(),
    val extras: List<Extra> = emptyList(),
    val overrides: Map<String, Int> = emptyMap(),
    val tasks: List<Task> = emptyList(),
    val semStart: String = LocalDate.now().toString(),
    val finalizedThrough: String = LocalDate.now().minusDays(1).toString(),
    val threshold: Int = 80,
    val lastBackup: Long = 0L,
    val backupDir: String? = null,
    val lastAutoBackup: String? = null,
    /** Your hostel mess (see [Mess.names]); null until chosen. */
    val mess: String? = null,
    /** For rotation messes: the cycle day (1..7) your mess serves on Sunday. */
    val messSunday: Int = 1,
)

/**
 * Fresh start for a new semester: drops every course and all attendance (records, extra classes,
 * timetable overrides) and starts counting from [today]. Tasks, mess and app settings are kept;
 * tasks lose their link to the old courses.
 */
fun AppData.newSemester(today: LocalDate): AppData = copy(
    courses = emptyList(),
    records = emptyMap(),
    extras = emptyList(),
    overrides = emptyMap(),
    tasks = tasks.map { it.copy(courseId = null) },
    semStart = today.toString(),
    finalizedThrough = today.minusDays(1).toString(),
)

object Json {
    fun encode(d: AppData): JSONObject = JSONObject().apply {
        put("app", "nitc-lite")
        put("version", 1)
        put("courses", JSONArray().apply {
            d.courses.forEach { c ->
                put(JSONObject().put("id", c.id).put("code", c.code).put("name", c.name).put("slot", c.slot)
                    .put("plus", c.plus).put("room", c.room)
                    .put("priorAttended", c.priorAttended).put("priorTotal", c.priorTotal)
                    .put("custom", c.custom?.let { list ->
                        JSONArray().apply { list.forEach { put(JSONObject().put("dow", it.dow).put("start", it.start).put("dur", it.dur)) } }
                    } ?: JSONObject.NULL)
                    .put("threshold", c.threshold ?: JSONObject.NULL))
            }
        })
        put("records", JSONObject().apply { d.records.forEach { (k, v) -> put(k, v.toString()) } })
        put("extras", JSONArray().apply {
            d.extras.forEach { e ->
                put(JSONObject().put("courseId", e.courseId).put("date", e.date).put("start", e.start).put("dur", e.dur))
            }
        })
        put("overrides", JSONObject().apply { d.overrides.forEach { (k, v) -> put(k, v) } })
        put("tasks", JSONArray().apply {
            d.tasks.forEach { t ->
                put(JSONObject().put("id", t.id).put("title", t.title).put("type", t.type.name)
                    .put("courseId", t.courseId ?: JSONObject.NULL).put("due", t.due)
                    .put("time", t.time ?: JSONObject.NULL).put("notes", t.notes).put("done", t.done))
            }
        })
        put("semStart", d.semStart)
        put("finalizedThrough", d.finalizedThrough)
        put("threshold", d.threshold)
        put("lastBackup", d.lastBackup)
        put("backupDir", d.backupDir ?: JSONObject.NULL)
        put("lastAutoBackup", d.lastAutoBackup ?: JSONObject.NULL)
        put("mess", d.mess ?: JSONObject.NULL)
        put("messSunday", d.messSunday)
    }

    fun decode(o: JSONObject): AppData {
        val def = AppData()
        val rec = o.optJSONObject("records")
        val records = HashMap<String, Char>()
        rec?.keys()?.forEach { k -> rec.optString(k).firstOrNull()?.let { records[k] = it } }
        val ov = o.optJSONObject("overrides")
        val overrides = HashMap<String, Int>()
        ov?.keys()?.forEach { k -> overrides[k] = ov.optInt(k) }
        return AppData(
            courses = o.optJSONArray("courses").objs().map {
                Course(
                    it.getString("id"), it.optString("code"), it.optString("name"), it.optString("slot"),
                    it.optBoolean("plus", false), it.optString("room"),
                    it.optInt("priorAttended"), it.optInt("priorTotal"),
                    if (it.isNull("custom")) null
                    else it.optJSONArray("custom").objs().map { m -> SlotTime(m.getInt("dow"), m.getInt("start"), m.optInt("dur", 50)) },
                    if (it.isNull("threshold")) null else it.optInt("threshold"),
                )
            },
            records = records,
            extras = o.optJSONArray("extras").objs().map {
                Extra(it.getString("courseId"), it.getString("date"), it.getInt("start"), it.optInt("dur", 50))
            },
            overrides = overrides,
            tasks = o.optJSONArray("tasks").objs().map {
                Task(
                    it.getString("id"), it.optString("title"),
                    runCatching { TaskType.valueOf(it.optString("type")) }.getOrDefault(TaskType.OTHER),
                    it.str("courseId"), it.getString("due"),
                    if (it.isNull("time")) null else it.optInt("time"),
                    it.optString("notes"), it.optBoolean("done"),
                )
            },
            semStart = o.str("semStart") ?: def.semStart,
            finalizedThrough = o.str("finalizedThrough") ?: def.finalizedThrough,
            threshold = o.optInt("threshold", 80),
            lastBackup = o.optLong("lastBackup"),
            backupDir = o.str("backupDir"),
            lastAutoBackup = o.str("lastAutoBackup"),
            mess = o.str("mess"),
            messSunday = o.optInt("messSunday", 1).coerceIn(1, 7),
        )
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

    private fun JSONArray?.objs(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
