package com.nitclite.app.data

/** min: minutes since midnight. ac: the A/C bus (to Architecture). */
data class Trip(val min: Int, val bus: String, val to: String, val ac: Boolean)

/** Campus shuttle, weekdays only. */
object Buses {
    val fromMbh: List<Trip> = parse(
        "7:20 1,7:30 2,7:40 1,7:50 2,8:00 1,8:10 2,8:30 1,8:40 2,8:50 1,8:55 A,9:00 2,9:10 1,9:30 A,9:35 2," +
            "9:40 1,9:50 A,9:55 2,10:00 1,10:05 2,10:50 1,10:55 2,11:50 1,12:10 2,12:20 1,12:30 2,12:35 A," +
            "12:40 1,12:50 2,12:55 A,1:00 1,1:10 2,1:20 1,1:35 A,1:40 2,1:50 1,1:55 A,2:00 2,2:05 1,2:55 2," +
            "3:00 1,3:55 A,4:10 2,4:20 1,4:35 A,4:40 2,4:50 1,4:55 A,5:00 2,5:10 1,5:20 2,5:30 1,5:40 2," +
            "5:50 1,6:00 2",
        to = "East Campus", acTo = "Architecture",
    )

    val fromEast: List<Trip> = parse(
        "7:20 2,7:30 1,7:40 2,7:50 1,8:00 2,8:20 1,8:30 2,8:40 1,8:50 2,9:00 1,9:25 2,9:30 1,9:40 2,9:50 1," +
            "9:55 2,10:40 1,10:45 2,11:40 1,12:00 2,12:10 1,12:20 2,12:20 1,12:40 2,12:50 1,1:00 2,1:10 1," +
            "1:30 2,1:40 1,1:50 2,1:55 1,2:45 2,2:50 1,4:00 2,4:10 1,4:30 2,4:40 1,4:50 2,5:00 1,5:10 2," +
            "5:20 1,5:30 2,5:40 1,5:50 2",
        to = "MBH", acTo = "MBH",
    )

    private fun parse(s: String, to: String, acTo: String): List<Trip> = s.split(',').map { e ->
        val (t, b) = e.trim().split(' ')
        val (h, m) = t.split(':').map { it.toInt() }
        val hour = if (h < 7) h + 12 else h
        val ac = b == "A"
        Trip(hour * 60 + m, if (ac) "A/C" else "Bus $b", if (ac) acTo else to, ac)
    }
}
