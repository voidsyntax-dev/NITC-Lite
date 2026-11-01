package com.nitclite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nitclite.app.data.*
import java.time.LocalDate
import java.time.LocalDateTime

fun typeColor(t: TaskType): Color = when (t) {
    TaskType.EXAM -> AbsentC
    TaskType.TEST -> OffC
    TaskType.ASSIGNMENT -> AcC
    TaskType.OTHER -> BusC
}

@Composable
fun TaskLine(t: Task, c: Course?, today: LocalDate) {
    val due = LocalDate.parse(t.due)
    val days = due.toEpochDay() - today.toEpochDay()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOfNotNull(c?.let { it.code.ifBlank { it.name } },relDay(due, today) + (t.time?.let { " · " + fmtTime(it) } ?: ""))
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (!t.done && days < 0) AbsentC else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(t.type.label, typeColor(t.type))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(now: LocalDateTime) {
    val d = Store.data
    val today = now.toLocalDate()
    val courses = remember(d) { d.courses.associateBy { it.id } }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var creating by remember { mutableStateOf(false) }

    val shown = d.tasks.filter { filter == null || it.type.name == filter }
    val pending = shown.filter { !it.done }.sortedWith(compareBy({ it.due }, { it.time ?: 0 }))
    val done = shown.filter { it.done }.sortedByDescending { it.due }
    val groups = pending.groupBy {
        val days = LocalDate.parse(it.due).toEpochDay() - today.toEpochDay()
        when {
            days < 0 -> "Overdue"
            days == 0L -> "Today"
            days <= 7 -> "This week"
            else -> "Later"
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            item { ScreenTitle("Tasks", "${pending.size} pending") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    TaskType.entries.forEach { t ->
                        FilterChip(selected = filter == t.name, onClick = { filter = if (filter == t.name) null else t.name }, label = { Text(t.label + "s") })
                    }
                }
            }
            if (pending.isEmpty() && done.isEmpty()) {
                item {
                    Block {
                        Text("Nothing here yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tap Add to save an assignment, test or exam with its due date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            for (g in listOf("Overdue", "Today", "This week", "Later")) {
                val list = groups[g] ?: continue
                item(key = "h$g") { SectionLabel(g, "${list.size}") }
                item(key = "g$g") { TaskGroup(list, courses, today) { editing = it } }
            }
            if (done.isNotEmpty()) {
                item(key = "hdone") { SectionLabel("Done", "${done.size}") }
                item(key = "gdone") { TaskGroup(done, courses, today) { editing = it } }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, "Add task") }
    }

    if (creating) TaskDialog(null, d.courses, today) { creating = false }
    editing?.let { t -> TaskDialog(t, d.courses, today) { editing = null } }
}

/** One card per section; each task is a row with a round check on the left. */
@Composable
private fun TaskGroup(list: List<Task>, courses: Map<String, Course>, today: LocalDate, onOpen: (Task) -> Unit) {
    Group {
        list.forEachIndexed { i, t ->
            GroupRow(first = i == 0, onClick = { onOpen(t) }) {
                val ring = if (t.done) PresentC else MaterialTheme.colorScheme.outline
                Box(
                    Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, ring, CircleShape)
                        .background(if (t.done) PresentC else Color.Transparent)
                        .clickable { Store.upsertTask(t.copy(done = !t.done)) },
                    contentAlignment = Alignment.Center,
                ) { if (t.done) Icon(Icons.Filled.Check, "Done", Modifier.size(14.dp), tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    TaskLine(t, courses[t.courseId ?: ""], today)
                    if (t.notes.isNotBlank()) {
                        Text(t.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDialog(existing: Task?, courses: List<Course>, today: LocalDate, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: TaskType.ASSIGNMENT) }
    var course by remember { mutableStateOf(existing?.courseId) }
    var due by remember { mutableStateOf(existing?.due?.let { LocalDate.parse(it) } ?: today.plusDays(1)) }
    var time by remember { mutableStateOf(existing?.time) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New task" else "Edit task") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                }
                if (courses.isNotEmpty()) {
                    Text("Course", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = course == null, onClick = { course = null }, label = { Text("None") })
                        courses.forEach { c ->
                            FilterChip(selected = course == c.id, onClick = { course = c.id }, label = { Text(c.code.ifBlank { c.name }) })
                        }
                    }
                }
                Text("Due", style = MaterialTheme.typography.labelLarge)
                DayChoice(due) { due = it }
                Text("Time", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf<Pair<Int?, String>>(null to "Any time", 23 * 60 + 59 to "11:59 PM"), time, time?.let { fmtTime(it) } ?: "", { time = it }) {
                    pickTime = true
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (existing != null) {
                    TextButton(onClick = { Store.deleteTask(existing.id); onDismiss() }) { Text("Delete task", color = AbsentC) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                Store.upsertTask(
                    Task(existing?.id ?: Store.newId(), title.trim(), type, course, due.toString(), time, notes.trim(), existing?.done ?: false),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickTime) TimeDialog(time ?: (9 * 60), onDismiss = { pickTime = false }) { time = it }
}

@Composable
fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
