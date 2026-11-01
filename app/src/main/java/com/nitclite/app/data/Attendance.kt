package com.nitclite.app.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class Session(val courseId: String, val date: String, val start: Int, val dur: Int, val extra: Boolean = false) {
    val key get() = "$date|$courseId|$start|$dur"

    companion object {
        fun parse(k: String): Session? {
            val p = k.split('|')
            if (p.size != 4) return null
            return Session(p[1], p[0], p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: 50)
        }
    }
}

data class Stat(val attended: Int, val total: Int, val off: Int) {
    val pct: Double get() = if (total == 0) 100.0 else attended * 100.0 / total

    /** Classes in a row you must attend to climb back to the threshold. */
    fun need(th: Int): Int {
        val t = th / 100.0
        if (total == 0 || pct >= th) return 0
        return ceil((t * total - attended) / (1 - t) - 1e-9).toInt()
    }

    /** Classes you can miss and still stay at or above the threshold. */
    fun canSkip(th: Int): Int = max(0, floor(attended / (th / 100.0) - total + 1e-9).toInt())
}

object Attendance {
    fun weekdayFor(d: AppData, date: LocalDate): Int = d.overrides[date.toString()] ?: date.dayOfWeek.value

    /** Every class on [date]: the timetable (for unsettled days) plus anything recorded, plus extra classes. */
    fun sessions(d: AppData, date: LocalDate): List<Session> {
        val ds = date.toString()
        val ids = d.courses.mapTo(HashSet()) { it.id }
        val out = LinkedHashMap<String, Session>()
        if (ds > d.finalizedThrough && ds >= d.semStart) {
            val dow = weekdayFor(d, date)
            for (c in d.courses) for (t in Slots.meetings(c)) {
                if (t.dow == dow) Session(c.id, ds, t.start, t.dur).let { out[it.key] = it }
            }
        }
        for (k in d.records.keys) {
            if (k.length > ds.length && k.startsWith(ds) && k[ds.length] == '|') {
                Session.parse(k)?.takeIf { it.courseId in ids }?.let { out[k] = it }
            }
        }
        for (e in d.extras) if (e.date == ds && e.courseId in ids) {
            Session(e.courseId, ds, e.start, e.dur, extra = true).let { out[it.key] = it }
        }
        return out.values.sortedBy { it.start }
    }

    fun stats(d: AppData): Map<String, Stat> {
        val p = HashMap<String, Int>()
        val a = HashMap<String, Int>()
        val c = HashMap<String, Int>()
        for ((k, v) in d.records) {
            val id = k.split('|').getOrNull(1) ?: continue
            val m = when (v) { PRESENT -> p; ABSENT -> a; else -> c }
            m[id] = (m[id] ?: 0) + 1
        }
        return d.courses.associate { co ->
            val pp = p[co.id] ?: 0
            val aa = a[co.id] ?: 0
            co.id to Stat(co.priorAttended + pp, co.priorTotal + pp + aa, c[co.id] ?: 0)
        }
    }

    fun overall(stats: Collection<Stat>) = Stat(stats.sumOf { it.attended }, stats.sumOf { it.total }, stats.sumOf { it.off })
}
