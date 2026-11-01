package com.nitclite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitclite.app.Tab
import com.nitclite.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(now: LocalDateTime, go: (Tab) -> Unit, openSettings: () -> Unit, openMess: () -> Unit) {
    val d = Store.data
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val sessions = remember(d, today) { Attendance.sessions(d, today) }
    val stats = remember(d) { Attendance.stats(d) }
    val courses = remember(d) { d.courses.associateBy { it.id } }
    val current = sessions.firstOrNull { nowMin >= it.start && nowMin < it.start + it.dur }
    var extra by remember { mutableStateOf(false) }
    if (extra) ExtraClassDialog(today, d.courses) { extra = false }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item {
            ScreenTitle(greeting(now.hour), today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) {
                HeaderButton(Icons.Filled.Settings, "Settings", openSettings)
            }
        }
        item {
            TopCards(
                now, d, sessions, courses, stats,
                openAttendance = { if (d.courses.isEmpty()) openSettings() else go(Tab.Attend) },
                openBus = { go(Tab.Bus) },
                openMess = openMess,
            )
        }

        if (d.courses.isEmpty()) {
            item {
                Block(color = MaterialTheme.colorScheme.primaryContainer, onClick = openSettings) {
                    Text("Set up your courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add each course with its slot (A1, C2, F, PA1…). Your timetable and attendance build themselves.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            item { SectionLabel("Today's classes", "+ Extra class", onTrailing = { extra = true }) }
            if (sessions.isNotEmpty()) {
                items(sessions, key = { it.key }) { s ->
                    SessionCard(s, courses[s.courseId], d.records[s.key], stats[s.courseId], d.threshold, past = false, highlight = s == current)
                }
            } else {
                item {
                    Text(
                        "No classes on the timetable today. If one happens, add it with + Extra class.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionLabel("Due soon", "All tasks", onTrailing = { go(Tab.Tasks) }) }
        item { TasksGlance(d, today, courses) { go(Tab.Tasks) } }

        if (d.courses.isNotEmpty()) {
            val overall = Attendance.overall(stats.values)
            val worst = d.courses.minByOrNull { (stats[it.id]?.pct ?: 100.0) - it.required(d.threshold) }
            val anyBelow = d.courses.any { (stats[it.id]?.pct ?: 100.0) < it.required(d.threshold) }
            item { SectionLabel("Attendance") }
            item {
                Block(onClick = { go(Tab.Attend) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring((overall.pct / 100).toFloat(), if (anyBelow) AbsentC else PresentC, 56.dp, 6.dp, "${overall.pct.toInt()}%", MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${overall.attended} of ${overall.total} attended", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (worst != null) {
                                val ws = stats[worst.id]!!
                                Text(
                                    "Closest to the line: ${worst.code.ifBlank { worst.name }} at ${ws.pct.toInt()}% (needs ${worst.required(d.threshold)}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ws.pct < worst.required(d.threshold)) AbsentC else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night"
}

/** Full-width cards, one at a time: swipe between attendance, bus and mess. */
@Composable
private fun TopCards(
    now: LocalDateTime,
    d: AppData,
    sessions: List<Session>,
    courses: Map<String, Course>,
    stats: Map<String, Stat>,
    openAttendance: () -> Unit,
    openBus: () -> Unit,
    openMess: () -> Unit,
) {
    val pager = rememberPagerState { 3 }
    // One fixed height for every card and state, so nothing below jumps when a card's content changes.
    val height = 216.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column {
        HorizontalPager(pager, Modifier.height(height), pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { page ->
            when (page) {
                0 -> AttendanceCard(now, d, sessions, courses, stats, openAttendance)
                1 -> BusCard(now, openBus)
                else -> MessCard(now, d, openMess)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (pager.currentPage == i) 18.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, title: String?, accent: Color, onOpen: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Block(Modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open", tint = accent, modifier = Modifier.rotate(-45f)) }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun TimeChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Muted(s: String) =
    Text(s, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Today's first unmarked class, markable right here; marking it brings up the next one. */
@Composable
private fun AttendanceCard(
    now: LocalDateTime,
    d: AppData,
    sessions: List<Session>,
    courses: Map<String, Course>,
    stats: Map<String, Stat>,
    onOpen: () -> Unit,
) {
    val unmarked = sessions.filter { d.records[it.key] == null && courses[it.courseId] != null }
    val first = unmarked.firstOrNull()
    val accent = if (first != null) AbsentC else PresentC
    when {
        d.courses.isEmpty() -> InfoCard("ATTENDANCE", "Set up your courses", MaterialTheme.colorScheme.primary, onOpen) {
            Muted("Add your courses and their slots to start tracking.")
        }
        sessions.isEmpty() -> InfoCard("ATTENDANCE", "No classes today", accent, onOpen) {
            Muted("If a class happens, add it with + Extra class below.")
        }
        first == null -> InfoCard("ATTENDANCE", "All marked", accent, onOpen) {
            Muted("Nothing left to mark today · ${sessions.size} classes done.")
        }
        else -> {
            val c = courses[first.courseId]!!
            val left = unmarked.size
            InfoCard("MARK ATTENDANCE · $left LEFT TODAY", c.title, accent, onOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeChip("${fmtTime(first.start)} – ${fmtTime(first.start + first.dur)}")
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOf(c.code, c.room).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    stats[c.id]?.let { Pill("${it.pct.toInt()}%", pctColor(it.pct, c.required(d.threshold))) }
                }
                Spacer(Modifier.height(12.dp))
                StatusButtons(null) { if (it != null) Store.mark(first, it) }
            }
        }
    }
}

@Composable
private fun BusCard(now: LocalDateTime, onOpen: () -> Unit) {
    val nowMin = now.hour * 60 + now.minute
    val weekend = now.dayOfWeek.value >= 6
    val mbh = Buses.fromMbh.filter { it.min >= nowMin }
    val east = Buses.fromEast.filter { it.min >= nowMin }
    when {
        weekend -> InfoCard("NEXT BUS", "No buses today", BusC, onOpen) {
            Muted("Buses run on weekdays. First bus Monday 7:20 AM from MBH.")
        }
        mbh.isEmpty() && east.isEmpty() -> InfoCard("NEXT BUS", "Done for today", BusC, onOpen) {
            Muted("First bus tomorrow at 7:20 AM.")
        }
        else -> InfoCard("NEXT BUS", "Campus shuttle", if (mbh.firstOrNull()?.ac == true) AcC else BusC, onOpen) {
            Row {
                BusColumn("From MBH", mbh, nowMin, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                BusColumn("From East Campus", east, nowMin, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BusColumn(from: String, trips: List<Trip>, nowMin: Int, modifier: Modifier) {
    Column(modifier) {
        Text(from, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        val t = trips.firstOrNull()
        if (t == null) {
            Text("No more today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            return@Column
        }
        val color = if (t.ac) AcC else BusC
        Text(fmtTime(t.min), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(if (t.ac) "A/C" else t.bus, color)
            Spacer(Modifier.width(6.dp))
            Pill(fmtIn(t.min - nowMin).removePrefix("in "), color, solid = true)
        }
        val then = trips.drop(1).take(2)
        if (then.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Then " + then.joinToString(", ") { fmtClock(it.min) + if (it.ac) " A/C" else "" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The main meal for right now: breakfast till 9, lunch till 2, then dinner. No snacks here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessCard(now: LocalDateTime, d: AppData, onOpen: () -> Unit) {
    val mess = d.mess
    if (mess == null) {
        InfoCard("MESS MENU", "Pick your mess", OffC, onOpen) { Muted("Choose your mess to see what's cooking.") }
        return
    }
    val (day, meal) = Mess.current(mess, d.messSunday, now, mainOnly = true)
    val title = if (day != now.toLocalDate()) "Tomorrow's ${meal.name.lowercase()}" else meal.name
    InfoCard("${Mess.label(mess).uppercase()} · ${fmtTime(meal.start)} – ${fmtTime(meal.end)}", title, OffC, onOpen) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxLines = 3,
        ) {
            meal.items.forEach { item ->
                Text(
                    item,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TasksGlance(d: AppData, today: LocalDate, courses: Map<String, Course>, onClick: () -> Unit) {
    val upcoming = d.tasks.filter { !it.done }.sortedWith(compareBy({ it.due }, { it.time ?: 0 })).take(3)
    Block(onClick = onClick) {
        if (upcoming.isEmpty()) {
            Text("Nothing pending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Add assignments, tests and exams in Tasks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else upcoming.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TaskLine(t, courses[t.courseId ?: ""], today)
        }
    }
}
