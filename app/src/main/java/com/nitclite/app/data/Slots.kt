package com.nitclite.app.data

/** One weekly meeting of a slot. dow: 1 = Monday .. 5 = Friday. Minutes since midnight. */
data class SlotTime(val dow: Int, val start: Int, val dur: Int, val plus: Boolean = false)

/** NITC institute slot grid (Institute Slots 1 and 2). */
object Slots {
    private fun h(dow: Int, hour: Int, hours: Int = 1, plus: Boolean = false) =
        SlotTime(dow, hour * 60, hours * 60 - 10, plus)

    val grid: Map<String, List<SlotTime>> = buildMap {
        put("A1", listOf(h(1, 9), h(4, 11), h(5, 10), h(3, 12, plus = true)))
        put("B1", listOf(h(1, 10), h(2, 9), h(5, 11), h(4, 12, plus = true)))
        put("C1", listOf(h(1, 11), h(2, 10), h(3, 9), h(5, 12, plus = true)))
        put("D1", listOf(h(2, 11), h(3, 10), h(4, 9), h(1, 12, plus = true)))
        put("E1", listOf(h(3, 11), h(4, 10), h(5, 9), h(2, 12, plus = true)))
        put("A2", listOf(h(1, 14), h(4, 16), h(5, 15), h(3, 13, plus = true)))
        put("B2", listOf(h(1, 15), h(2, 14), h(5, 16), h(4, 13, plus = true)))
        put("C2", listOf(h(1, 16), h(2, 15), h(3, 14), h(5, 13, plus = true)))
        put("D2", listOf(h(2, 16), h(3, 15), h(4, 14), h(1, 13, plus = true)))
        put("E2", listOf(h(3, 16), h(4, 15), h(5, 14), h(2, 13, plus = true)))
        put("F", listOf(h(1, 8), h(4, 8), h(2, 17), h(5, 17, plus = true)))
        put("G", listOf(h(3, 8), h(1, 17), h(4, 17)))
        put("H", listOf(h(2, 8), h(5, 8), h(3, 17)))
        "PQRST".forEachIndexed { i, l ->
            val d = i + 1
            put("${l}1", listOf(h(d, 14, 3)))
            put("${l}A1", listOf(h(d, 14, 2)))
            put("${l}B1", listOf(h(d, 16, 2)))
            put("${l}2", listOf(h(d, 9, 3)))
            put("${l}A2", listOf(h(d, 8, 2)))
            put("${l}B2", listOf(h(d, 10, 2)))
        }
    }

    val groups: List<Pair<String, List<String>>> = listOf(
        "Theory · Slots 1" to listOf("A1", "B1", "C1", "D1", "E1"),
        "Theory · Slots 2" to listOf("A2", "B2", "C2", "D2", "E2"),
        "Common" to listOf("F", "G", "H"),
        "Lab · Slots 1 (afternoon)" to "PQRST".flatMap { listOf("${it}1", "${it}A1", "${it}B1") },
        "Lab · Slots 2 (morning)" to "PQRST".flatMap { listOf("${it}2", "${it}A2", "${it}B2") },
    )

    /** The slot's regular weekly classes, without the + hour. */
    fun base(slot: String): List<SlotTime> = grid[slot].orEmpty().filter { !it.plus }

    fun plusTimes(slot: String): List<SlotTime> = grid[slot].orEmpty().filter { it.plus }

    fun hasPlus(slot: String) = plusTimes(slot).isNotEmpty()

    /** What actually happens each week for this course: its own times (or the slot's), plus the + hour if switched on. */
    fun meetings(c: Course): List<SlotTime> =
        ((c.custom ?: base(c.slot)) + if (c.plus) plusTimes(c.slot) else emptyList())
            .sortedWith(compareBy({ it.dow }, { it.start }))
}
