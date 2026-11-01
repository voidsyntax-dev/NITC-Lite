package com.nitclite.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nitclite.app.data.*
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val d = Store.data
    var editing by remember { mutableStateOf<Course?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pickSem by remember { mutableStateOf(false) }
    var customTh by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    if (customTh) {
        NumberDialog("Required attendance", d.threshold, "%", 1..100, onDismiss = { customTh = false }) { t ->
            Store.update { it.copy(threshold = t) }
        }
    }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) toast(Backup.exportJson(ctx, uri))
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) toast(Backup.exportCsv(ctx, uri))
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmRestore = uri }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Store.update { it.copy(backupDir = uri.toString(), lastAutoBackup = null) }
            Backup.auto(ctx)
            toast("Auto-backup on")
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item {
            Column {
                HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                ScreenTitle("Settings")
            }
        }

        item { SectionLabel("Courses", "Add", onTrailing = { creating = true }) }
        if (d.courses.isEmpty()) {
            item {
                Block {
                    Text("No courses yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add each course with its slot from the institute timetable. Classes then appear on the right days automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { creating = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add course") }
                }
            }
        }
        if (d.courses.isNotEmpty()) item {
            Group {
                d.courses.forEachIndexed { i, c ->
                    GroupRow(first = i == 0, onClick = { editing = c }) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(c.code, "Slot ${c.slot}" + if (c.plus && Slots.hasPlus(c.slot)) "+" else "", c.room, "needs ${c.required(d.threshold)}%")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                Slots.meetings(c).joinToString("  ·  ") { "${dayName(it.dow)} ${fmtClock(it.start)}" },
                                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionLabel("Attendance") }
        item {
            Block {
                Text("Default required attendance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "For subjects that don't set their own. Set a different % per subject in its course settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceRow(listOf(50 to "50%", 70 to "70%", 80 to "80%"), d.threshold, "${d.threshold}%", { t ->
                    Store.update { it.copy(threshold = t) }
                }) { customTh = true }
                Spacer(Modifier.height(10.dp))
                Text("Count classes from", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Days from here until yesterday that you never marked count as present. Use \"earlier attendance\" in a course for anything before you started using the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { pickSem = true }, modifier = Modifier.padding(top = 6.dp)) { Text(fmtDate(LocalDate.parse(d.semStart)) + " " + LocalDate.parse(d.semStart).year) }
            }
        }

        item { SectionLabel("Backup") }
        item {
            Block {
                Text(
                    if (d.lastBackup == 0L) "Never backed up" else "Last backup: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.lastBackup)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "The .json file restores everything (courses, attendance, tasks). Save it to Drive or send it to your laptop. The .csv opens in Excel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportJson.launch(Backup.fileName()) }) { Text("Save backup") }
                    OutlinedButton(onClick = { exportCsv.launch("attendance-${LocalDate.now()}.csv") }) { Text("Export CSV") }
                }
                TextButton(onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) { Text("Restore from backup…") }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Daily auto-backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (d.backupDir == null) "Pick a folder (e.g. a Google Drive or Downloads folder). A dated copy is saved there once a day when you open the app."
                    else "On. Saving a dated copy each day" + (d.lastAutoBackup?.let { " · last $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { pickFolder.launch(null) }) { Text(if (d.backupDir == null) "Choose folder" else "Change folder") }
                    if (d.backupDir != null) TextButton(onClick = { Store.update { it.copy(backupDir = null) } }) { Text("Turn off") }
                }
            }
        }
        item { SectionLabel("New semester") }
        item {
            Block {
                Text("Start a new semester", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Deletes all courses and attendance and starts counting from today. Tasks, mess and settings stay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { confirmWipe = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, AbsentC.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AbsentC),
                ) { Text("Delete courses & attendance") }
            }
        }
        item {
            Text(
                "NITC Lite 1.0 · everything stays on this phone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    if (creating) CourseDialog(null) { creating = false }
    editing?.let { c -> CourseDialog(c) { editing = null } }
    if (pickSem) DateDialog(LocalDate.parse(d.semStart), onDismiss = { pickSem = false }) { Store.setSemStart(it) }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Start a new semester?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This permanently deletes ${d.courses.size} courses and ${d.records.size} attendance records. It can't be undone.",
                    )
                    Text(
                        if (d.lastBackup == 0L) "You have never saved a backup." else "Last backup: " +
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(d.lastBackup)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { exportJson.launch(Backup.fileName()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save a backup first")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.newSemester()
                    confirmWipe = false
                    toast("New semester started")
                }) { Text("Delete everything", color = AbsentC) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Replace current data?") },
            text = { Text("Everything in the app will be replaced with the backup's contents.") },
            confirmButton = { TextButton(onClick = { toast(Backup.import(ctx, uri)); confirmRestore = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

/** Pick the day, start and length of one weekly class. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetingDialog(initial: SlotTime?, onDismiss: () -> Unit, onSave: (SlotTime) -> Unit) {
    var dow by remember { mutableIntStateOf(initial?.dow ?: 1) }
    var start by remember { mutableIntStateOf(initial?.start ?: (9 * 60)) }
    var dur by remember { mutableIntStateOf(initial?.dur ?: 50) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add class time" else "Change class time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Day", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 1..6) FilterChip(selected = dow == d, onClick = { dow = d }, label = { Text(dayName(d)) })
                }
                Text("Starts", style = MaterialTheme.typography.labelLarge)
                StartChoice(start) { start = it }
                Text("Length", style = MaterialTheme.typography.labelLarge)
                LengthChoice(dur) { dur = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(SlotTime(dow, start, dur)); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseDialog(existing: Course?, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var slot by remember { mutableStateOf(existing?.slot ?: "") }
    var plus by remember { mutableStateOf(existing?.plus ?: false) }
    var custom by remember { mutableStateOf(existing?.custom) }
    var editIdx by remember { mutableStateOf<Int?>(null) } // -1 = adding a new class time
    val weekly = custom ?: Slots.base(slot)
    var priorA by remember { mutableStateOf(existing?.priorAttended?.takeIf { it > 0 }?.toString() ?: "") }
    var priorT by remember { mutableStateOf(existing?.priorTotal?.takeIf { it > 0 }?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var req by remember { mutableIntStateOf(existing?.required(Store.data.threshold) ?: Store.data.threshold) }
    var customReq by remember { mutableStateOf(false) }
    if (customReq) NumberDialog("Required attendance", req, "%", 1..100, onDismiss = { customReq = false }) { req = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add course" else "Edit course") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Code (e.g. CH3002E)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(room, { room = it }, label = { Text("Room (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Required attendance", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(listOf(50 to "50%", 70 to "70%", 80 to "80%"), req, "$req%", { req = it }) { customReq = true }
                Text("Slot", style = MaterialTheme.typography.labelLarge)
                for ((group, slots) in Slots.groups) {
                    Text(group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        slots.forEach { s ->
                            FilterChip(selected = slot == s, onClick = { if (slot != s) { slot = s; custom = null } }, label = { Text(s) })
                        }
                    }
                }
                if (slot.isNotBlank()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "Weekly classes · ${weekly.size + if (plus) Slots.plusTimes(slot).size else 0}/week",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        if (custom == null) "Standard $slot timings. Tap one to change it." else "Customised for this course.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    weekly.forEachIndexed { i, t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { editIdx = i }.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dayName(t.dow), Modifier.width(44.dp), fontWeight = FontWeight.SemiBold)
                            Text("${fmtTime(t.start)} – ${fmtTime(t.start + t.dur)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { custom = weekly.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Filled.Close, "Delete class time", Modifier.size(18.dp))
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { editIdx = -1 }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add class time")
                        }
                        if (custom != null) TextButton(onClick = { custom = null }) { Text("Reset to $slot") }
                    }
                    if (Slots.hasPlus(slot)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("$slot+ hour", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    Slots.plusTimes(slot).joinToString { "${dayName(it.dow)} ${fmtTime(it.start)}" } + " · only if your course uses it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = plus, onCheckedChange = { plus = it })
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                Text("Earlier attendance (before using this app)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        priorA, { priorA = it.filter(Char::isDigit) }, label = { Text("Attended") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        priorT, { priorT = it.filter(Char::isDigit) }, label = { Text("Total") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete course", color = AbsentC) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = slot.isNotBlank() && (code.isNotBlank() || name.isNotBlank()), onClick = {
                val t = priorT.toIntOrNull() ?: 0
                Store.upsertCourse(
                    Course(
                        existing?.id ?: Store.newId(), code.trim(), name.trim(), slot, plus, room.trim(),
                        (priorA.toIntOrNull() ?: 0).coerceAtMost(t), t, custom, req,
                    ),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    editIdx?.let { i ->
        MeetingDialog(weekly.getOrNull(i), onDismiss = { editIdx = null }) { t ->
            custom = if (i >= 0) weekly.mapIndexed { j, x -> if (j == i) t else x } else weekly + t
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.title}?") },
            text = { Text("Its attendance history is removed too.") },
            confirmButton = { TextButton(onClick = { Store.deleteCourse(existing.id); confirmDelete = false; onDismiss() }) { Text("Delete", color = AbsentC) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
