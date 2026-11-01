package com.nitclite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nitclite.app.data.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AttendanceScreen(now: LocalDateTime, openSettings: () -> Unit) {
    val d = Store.data
    val today = now.toLocalDate()
    var selStr by rememberSaveable { mutableStateOf(today.toString()) }
    val sel = LocalDate.parse(selStr)
    val stats = remember(d) { Attendance.stats(d) }
    val courses = remember(d) { d.courses.associateBy { it.id } }
    val sessions = remember(d, sel) { Attendance.sessions(d, sel) }
    val overall = remember(stats) { Attendance.overall(stats.values) }
    var extraFor by remember { mutableStateOf<LocalDate?>(null) }
    var historyFor by remember { mutableStateOf<Course?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item { ScreenTitle("Attendance", "Unmarked counts as present at 11:59 PM") }

        if (d.courses.isEmpty()) {
            item {
                Block(color = MaterialTheme.colorScheme.primaryContainer, onClick = openSettings) {
                    Text("Add your courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Enter each course with its slot and the timetable fills itself in.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@LazyColumn
        }

        item {
            val below = d.courses.filter { (stats[it.id]?.pct ?: 100.0) < it.required(d.threshold) }
            OverallCard(overall, below.map { it.code.ifBlank { it.name } }, d.courses.size)
        }
        item { MonthCalendar(sel, today, d) { selStr = it.toString() } }
        item { DayHeader(sel, today, d, sessions.isNotEmpty(), onExtra = { extraFor = sel }) }
        items(sessions, key = { it.key }) { s ->
            SessionCard(s, courses[s.courseId], d.records[s.key], stats[s.courseId], d.threshold, past = sel < today)
        }
        item { SectionLabel("Subjects", "Tap for missed classes") }
        item {
            val sorted = d.courses.sortedBy { (stats[it.id]?.pct ?: 100.0) - it.required(d.threshold) }
            Group {
                sorted.forEachIndexed { i, c ->
                    GroupRow(first = i == 0, onClick = { historyFor = c }) {
                        CourseStatRow(c, stats[c.id]!!, d.threshold, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    extraFor?.let { date -> ExtraClassDialog(date, d.courses, onDismiss = { extraFor = null }) }
    historyFor?.let { c -> SubjectDialog(c, d, onDismiss = { historyFor = null }) }
}

@Composable
private fun OverallCard(overall: Stat, below: List<String>, subjects: Int) {
    Block {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring((overall.pct / 100).toFloat(), if (below.isEmpty()) PresentC else AbsentC, 76.dp, 7.dp, "${overall.pct.toInt()}%")
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("OVERALL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${overall.attended} of ${overall.total} attended",
                    style = MaterialTheme.typography.titleMedium.merge(Tabular),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (below.isEmpty()) PresentC else AbsentC, 7.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (below.isEmpty()) "All $subjects subjects on track"
                        else "${below.size} below requirement: ${below.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Month calendar. A red dot under a day means you missed at least one class that day. */
@Composable
private fun MonthCalendar(sel: LocalDate, today: LocalDate, d: AppData, onPick: (LocalDate) -> Unit) {
    val missedDays = remember(d.records) {
        d.records.entries.filter { it.value == ABSENT }.mapTo(HashSet()) { it.key.substringBefore('|') }
    }
    val first = sel.withDayOfMonth(1)
    val gridStart = first.with(DayOfWeek.MONDAY)
    val weeks = ((first.lengthOfMonth() + first.dayOfWeek.value - 2) / 7) + 1
    Block {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                sel.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + sel.year,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (sel != today) TextButton(onClick = { onPick(today) }) { Text("Today") }
            IconButton(onClick = { onPick(sel.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            IconButton(onClick = { onPick(sel.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        Row(Modifier.fillMaxWidth()) {
            for (dow in 1..7) {
                Text(
                    dayName(dow).take(1),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0..6) {
                    val day = gridStart.plusDays((w * 7 + i).toLong())
                    val inMonth = day.month == sel.month
                    val isSel = day == sel
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(day) }.padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .then(if (day == today && !isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium.merge(Tabular),
                                fontWeight = if (isSel || day == today) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSel -> MaterialTheme.colorScheme.onPrimary
                                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Dot(if (day.toString() in missedDays) AbsentC else Color.Transparent, 6.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeader(sel: LocalDate, today: LocalDate, d: AppData, hasClasses: Boolean, onExtra: () -> Unit) {
    val override = d.overrides[sel.toString()]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                relDay(sel, today) + if (sel == today || sel == today.plusDays(1) || sel == today.minusDays(1)) " · ${fmtDate(sel)}" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onExtra) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Extra class")
            }
        }
        if (hasClasses) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayChip("All present", PresentC) { Store.markDay(sel, PRESENT) }
                DayChip("All absent", AbsentC) { Store.markDay(sel, ABSENT) }
                DayChip("Holiday", OffC) { Store.markDay(sel, OFF) }
            }
        }
        val weekend = sel.dayOfWeek.value >= 6
        if (override != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Following ${DayOfWeek.of(override).getDisplayName(TextStyle.FULL, Locale.getDefault())} timetable", MaterialTheme.colorScheme.primary)
                IconButton(onClick = { Store.setOverride(sel, null) }) { Icon(Icons.Filled.Close, "Clear", Modifier.size(18.dp)) }
            }
        } else if (weekend || !hasClasses) {
            Block {
                Text(
                    if (weekend) "No classes on ${sel.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}" else "No classes",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Working day? Pick the timetable it follows:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (dow in 1..5) {
                        AssistChip(
                            onClick = { Store.setOverride(sel, dow) },
                            label = { Text(DayOfWeek.of(dow).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SessionCard(s: Session, c: Course?, status: Char?, stat: Stat?, th: Int, past: Boolean, highlight: Boolean = false) {
    if (c == null) return
    var moving by remember { mutableStateOf(false) }
    if (moving) ExtraClassDialog(LocalDate.parse(s.date).plusDays(1), listOf(c), moving = s) { moving = false }
    Block(color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp)) {
                Text(fmtClock(s.start), style = MaterialTheme.typography.titleMedium.merge(Tabular))
                Text(amPm(s.start) + " · ${s.dur + 10}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(if (status == null) MaterialTheme.colorScheme.outlineVariant else statusColor(status)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOf(c.code, c.room, "Slot ${c.slot}").filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (s.extra) Pill("Extra", MaterialTheme.colorScheme.primary)
            else if (stat != null) Pill("${stat.pct.toInt()}%", pctColor(stat.pct, c.required(th)))
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }, Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, "More", Modifier.size(20.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rescheduled to another day…") }, onClick = { menu = false; moving = true })
                    if (s.extra) DropdownMenuItem(text = { Text("Remove this extra class") }, onClick = { menu = false; Store.removeExtra(s) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StatusButtons(status) { Store.mark(s, it) }
        if (status == null && past) {
            Spacer(Modifier.height(4.dp))
            Text("Unmarked: will count as present", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A subject's numbers: ring, name, attended/total and what it needs. */
@Composable
private fun CourseStatRow(c: Course, s: Stat, default: Int, modifier: Modifier = Modifier) {
    val th = c.required(default)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val col = pctColor(s.pct, th)
        Ring((s.pct / 100).toFloat(), col, 44.dp, 4.dp, "${s.pct.toInt()}", MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${c.code.ifBlank { "Slot " + c.slot }} · ${s.attended}/${s.total} · needs $th%" + if (s.off > 0) " · ${s.off} cancelled" else "",
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val need = s.need(th)
        Pill(if (need > 0) "+$need needed" else "Skip ${s.canSkip(th)}", if (need > 0) AbsentC else PresentC)
    }
}

/**
 * One-off class on any date. With [moving], it's a rescheduled class: the original is marked cancelled
 * and the new one is added, so attendance stays one-for-one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraClassDialog(date: LocalDate, courses: List<Course>, moving: Session? = null, onDismiss: () -> Unit) {
    var course by remember { mutableStateOf(moving?.courseId ?: courses.firstOrNull()?.id) }
    var day by remember { mutableStateOf(date) }
    var start by remember { mutableIntStateOf(moving?.start ?: (9 * 60)) }
    var dur by remember { mutableIntStateOf(moving?.dur ?: 50) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moving != null) "Class moved to…" else "Extra class") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (moving != null) {
                    Text(
                        "${fmtDate(LocalDate.parse(moving.date))} ${fmtTime(moving.start)} will be marked cancelled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Course", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        courses.forEach { c ->
                            FilterChip(selected = course == c.id, onClick = { course = c.id }, label = { Text(c.code.ifBlank { c.name }) })
                        }
                    }
                }
                Text("Day", style = MaterialTheme.typography.labelLarge)
                DayChoice(day) { day = it }
                Text("Starts", style = MaterialTheme.typography.labelLarge)
                StartChoice(start) { start = it }
                Text("Length", style = MaterialTheme.typography.labelLarge)
                LengthChoice(dur) { dur = it }
            }
        },
        confirmButton = {
            TextButton(enabled = course != null, onClick = {
                if (moving != null) Store.mark(moving, OFF)
                Store.addExtra(Extra(course!!, day.toString(), start, dur))
                onDismiss()
            }) { Text(if (moving != null) "Move" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One subject: its stats and the classes you missed. Fix a wrong mark from the calendar. */
@Composable
private fun SubjectDialog(c: Course, d: AppData, onDismiss: () -> Unit) {
    val stat = remember(d, c.id) { Attendance.stats(d)[c.id] } ?: return
    val rows = remember(d, c.id) {
        d.records.entries.filter { it.value == ABSENT }
            .mapNotNull { (k, v) -> Session.parse(k)?.takeIf { it.courseId == c.id }?.let { it to v } }
            .sortedWith(compareByDescending<Pair<Session, Char>> { it.first.date }.thenByDescending { it.first.start })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { CourseStatRow(c, stat, d.threshold) }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Missed classes · ${rows.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                if (rows.isEmpty()) item { Text("You haven't missed a class of this subject.", style = MaterialTheme.typography.bodySmall) }
                items(rows, key = { it.first.key }) { (s, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(AbsentC)
                        Spacer(Modifier.width(10.dp))
                        Text(fmtDate(LocalDate.parse(s.date)) + " " + LocalDate.parse(s.date).year, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(fmtTime(s.start), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
