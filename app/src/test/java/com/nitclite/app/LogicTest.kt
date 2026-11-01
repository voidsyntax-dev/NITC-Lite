package com.nitclite.app

import com.nitclite.app.data.*
import com.nitclite.app.wifi.Html
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.time.LocalDate

class LogicTest {
    private val mon = LocalDate.of(2026, 10, 26) // a Monday
    private val chem = Course("c1", "CH3002E", "Chemical Technology", "C1")
    private val pic = Course("c2", "CH3001E", "Process Instrumentation", "D1")

    private fun data(vararg c: Course) = AppData(courses = c.toList(), semStart = "2026-10-01", finalizedThrough = "2026-10-25")

    @Test fun timetableMatchesInstituteSlots() {
        val s = Attendance.sessions(data(chem, pic.copy(plus = true)), mon)
        // Monday: C1 at 11:00, D1+ at 12:00
        assertEquals(listOf(11 * 60 to "c1", 12 * 60 to "c2"), s.map { it.start to it.courseId })
        val wed = Attendance.sessions(data(chem, pic), mon.plusDays(2))
        assertEquals(listOf(9 * 60 to "c1", 10 * 60 to "c2"), wed.map { it.start to it.courseId })
        assertTrue(Attendance.sessions(data(chem), mon.plusDays(5)).isEmpty()) // Saturday
    }

    @Test fun plusHourIsOffByDefault() {
        assertTrue(Attendance.sessions(data(pic), mon).isEmpty())
        assertEquals(3, Slots.meetings(pic).size)
        assertEquals(4, Slots.meetings(pic.copy(plus = true)).size)
    }

    @Test fun customWeeklyTimes() {
        // A1 normally Mon 9, Thu 11, Fri 10. Drop Friday, move Thursday to 2 PM.
        val a1 = Course("c3", "MA1001E", "Maths", "A1", custom = listOf(SlotTime(1, 540, 50), SlotTime(4, 840, 50)))
        val d = data(a1)
        assertEquals(listOf(540), Attendance.sessions(d, mon).map { it.start })
        assertEquals(listOf(840), Attendance.sessions(d, mon.plusDays(3)).map { it.start })
        assertTrue(Attendance.sessions(d, mon.plusDays(4)).isEmpty())
        assertEquals(d, Json.decode(Json.encode(d)))
    }

    @Test fun perSubjectRequirement() {
        val lab = chem.copy(threshold = 70)
        assertEquals(70, lab.required(80))
        assertEquals(80, pic.required(80))
        val s = Stat(7, 10, 0)
        assertEquals(0, s.need(lab.required(80)))
        assertEquals(5, s.need(pic.required(80))) // (7+5)/(10+5) = 0.8
        val d = data(lab, pic)
        assertEquals(d, Json.decode(Json.encode(d)))
    }

    @Test fun messRotation() {
        val sun = LocalDate.of(2026, 11, 1)
        // PG2 serves cycle day 1 on Sunday 1 Nov 2026 (from the official menu app).
        assertEquals(1, Mess.cycleDay(sun, 1))
        assertEquals(2, Mess.cycleDay(sun.plusDays(1), 1))
        assertEquals(7, Mess.cycleDay(sun.plusDays(6), 1))
        assertEquals(1, Mess.cycleDay(sun.plusDays(7), 1))
        // Another mess on cycle day 3 on Sunday is on day 4 Monday and wraps after 7.
        assertEquals(4, Mess.cycleDay(sun.plusDays(1), 3))
        assertEquals(2, Mess.cycleDay(sun.plusDays(6), 3))
        assertEquals("Masala Dosa", Mess.day("PG2", 1, sun)[0].items.first())
        assertEquals("Chicken Biriyani / Veg Biriyani", Mess.day("PG2", 1, sun.plusDays(1))[1].items.first())
        // E Mess goes by weekday.
        assertEquals("Paav Bhaji (Toasted)", Mess.day(Mess.VEG, 1, sun)[0].items.first())
        assertEquals("Masala Dosa", Mess.day(Mess.VEG, 1, sun.plusDays(1))[0].items.first())
        // After dinner, the next meal is tomorrow's breakfast.
        val (day, meal) = Mess.current("PG2", 1, sun.atTime(22, 0))
        assertEquals(sun.plusDays(1), day)
        assertEquals("Breakfast", meal.name)
        assertEquals("Lunch", Mess.current("PG2", 1, sun.atTime(12, 30)).second.name)
        // Home card: breakfast up to 9:00, lunch from 9:01 to 2:00, then straight to dinner.
        assertEquals("Breakfast", Mess.current("PG2", 1, sun.atTime(9, 0), mainOnly = true).second.name)
        assertEquals("Lunch", Mess.current("PG2", 1, sun.atTime(9, 1), mainOnly = true).second.name)
        assertEquals("Dinner", Mess.current("PG2", 1, sun.atTime(14, 1), mainOnly = true).second.name)
        assertEquals("Snacks", Mess.current("PG2", 1, sun.atTime(14, 1)).second.name)
        val d = data(chem).copy(mess = "MBH2-1", messSunday = 4)
        assertEquals(d, Json.decode(Json.encode(d)))
    }

    @Test fun newSemesterKeepsOnlyNonCourseData() {
        val old = data(chem).copy(
            records = mapOf("2026-10-26|c1|660|50" to ABSENT),
            extras = listOf(Extra("c1", "2026-10-27", 600, 50)),
            overrides = mapOf("2026-10-31" to 5),
            tasks = listOf(Task("t1", "Lab report", TaskType.ASSIGNMENT, "c1", "2026-11-03", null)),
            mess = "PG2", threshold = 75,
        )
        val today = LocalDate.of(2027, 1, 4)
        val fresh = old.newSemester(today)
        assertTrue(fresh.courses.isEmpty() && fresh.records.isEmpty() && fresh.extras.isEmpty() && fresh.overrides.isEmpty())
        assertEquals("2027-01-04", fresh.semStart)
        assertTrue(Attendance.sessions(fresh, today).isEmpty())
        assertTrue(Attendance.stats(fresh).isEmpty())
        assertEquals("PG2", fresh.mess)
        assertEquals(75, fresh.threshold)
        assertEquals(null, fresh.tasks.single().courseId)
    }

    @Test fun eachClassIsItsOwnRecord() {
        // Two classes of the same subject on one day stay separate: marking one leaves the other alone.
        val a = Session("c1", "2026-10-26", 660, 50)
        val b = Session("c1", "2026-10-26", 661, 50, extra = true)
        val d = data(chem).copy(records = mapOf(a.key to PRESENT, b.key to ABSENT), extras = listOf(Extra("c1", "2026-10-26", 661, 50)))
        assertEquals(2, Attendance.sessions(d, mon).count { it.courseId == "c1" })
        val st = Attendance.stats(d)["c1"]!!
        assertEquals(1, st.attended)
        assertEquals(2, st.total)
    }

    @Test fun saturdayFollowingFriday() {
        val d = data(chem.copy(plus = true)).copy(overrides = mapOf(mon.plusDays(5).toString() to 5))
        assertEquals(listOf(12 * 60), Attendance.sessions(d, mon.plusDays(5)).map { it.start }) // C1+ is Fri 12:00
    }

    @Test fun statsAndBunkMath() {
        val s = Stat(8, 10, 0)
        assertEquals(80.0, s.pct, 0.001)
        assertEquals(0, s.need(80))
        assertEquals(0, s.canSkip(80))
        val low = Stat(27, 39, 0)
        assertEquals(21, low.need(80)) // (27+21)/(39+21) = 0.8
        assertEquals(2, Stat(12, 13, 0).canSkip(80)) // 12/15 = 0.8
    }

    @Test fun recordsDriveStats() {
        val s1 = Session("c1", "2026-10-26", 660, 50)
        val d = data(chem.copy(priorAttended = 5, priorTotal = 6)).copy(records = mapOf(s1.key to ABSENT))
        val st = Attendance.stats(d)["c1"]!!
        assertEquals(5, st.attended)
        assertEquals(7, st.total)
    }

    @Test fun jsonRoundTrip() {
        val d = data(chem).copy(
            records = mapOf("2026-10-26|c1|660|50" to PRESENT),
            tasks = listOf(Task("t1", "Lab report", TaskType.ASSIGNMENT, "c1", "2026-11-03", 540)),
        )
        assertEquals(d, Json.decode(Json.encode(d)))
    }

    @Test fun busTimesParse() {
        assertEquals(7 * 60 + 20, Buses.fromMbh.first().min)
        assertEquals(18 * 60, Buses.fromMbh.last().min)
        assertTrue(Buses.fromMbh.zipWithNext().all { (a, b) -> a.min <= b.min })
        assertTrue(Buses.fromEast.zipWithNext().all { (a, b) -> a.min <= b.min })
        assertEquals("Architecture", Buses.fromMbh.first { it.ac }.to)
        assertTrue(Buses.fromEast.none { it.ac })
    }

    @Test fun fortinetStyleForm() {
        val html = """
            <html><body><form action="/" method="post">
            <input type="hidden" name="4Tredir" value="http://www.google.com/">
            <input type="hidden" name="magic" value="0a1b2c3d">
            <input type="text" name="username" id="ft_un">
            <input type="password" name="password" id="ft_pd">
            <button type="submit">Continue</button>
            </form></body></html>
        """
        val base = URL("https://10.0.0.1:1003/fgtauth?0a1b2c3d")
        val f = Html.forms(html, base).single()
        assertEquals("https://10.0.0.1:1003/", f.action.toString())
        val body = Html.fill(f, "b240463ch", "p@ss&word")
        assertEquals("4Tredir=http%3A%2F%2Fwww.google.com%2F&magic=0a1b2c3d&username=b240463ch&password=p%40ss%26word", body)
    }

    @Test fun pfSenseForm() {
        val html = """
            <form method="post" action="http://172.20.28.1:8002/index.php?zone=mbh2">
              <input name="auth_user" class="form-control" type="text" placeholder="User">
              <input name="auth_pass" class="form-control" type="password" placeholder="Password">
              <input name="redirurl" type="hidden" value="http://connectivitycheck.gstatic.com/generate_204">
              <input name="zone" type="hidden" value="mbh2">
              <input name="accept" class="btn btn-primary btn-block" type="submit" value="Login">
            </form>
        """
        val f = Html.forms(html, URL("http://172.20.28.1:8002/index.php?zone=mbh2")).single()
        assertEquals("post", f.method)
        val body = Html.fill(f, "b240463ch", "secret")
        assertEquals(
            "auth_user=b240463ch&auth_pass=secret&redirurl=http%3A%2F%2Fconnectivitycheck.gstatic.com%2Fgenerate_204&zone=mbh2&accept=Login",
            body,
        )
        // Same form with a <button> instead of an input submit still sends accept=Login.
        val withButton = html.replace(Regex("<input name=\"accept\"[^>]*>"), "<button name=\"accept\" value=\"Login\">Login</button>")
        assertTrue(Html.fill(Html.forms(withButton, URL("http://172.20.28.1:8002/")).single(), "u", "p").endsWith("&accept=Login"))
        assertFalse(Html.looksFailed("<html><body>Welcome! You are connected.</body></html>"))
        assertTrue(Html.looksFailed("<div class=\"alert\">Invalid credentials specified.</div>"))
    }

    @Test fun jsRedirectIsFollowed() {
        val html = """<html><script>window.location="https://10.0.0.1:1003/fgtauth?abc";</script></html>"""
        assertEquals("https://10.0.0.1:1003/fgtauth?abc", Html.redirect(html))
        val meta = """<meta http-equiv="refresh" content="0; url=http://192.168.1.1:8090/httpclient.html">"""
        assertEquals("http://192.168.1.1:8090/httpclient.html", Html.redirect(meta))
    }
}
