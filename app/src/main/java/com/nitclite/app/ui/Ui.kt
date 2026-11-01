package com.nitclite.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitclite.app.data.ABSENT
import com.nitclite.app.data.OFF
import com.nitclite.app.data.PRESENT
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------------------------
// Design tokens. One accent (ink blue), neutral surfaces, and status colours used only for meaning.
// ---------------------------------------------------------------------------------------------

val PresentC = Color(0xFF2F855A)
val AbsentC = Color(0xFFD64545)
val OffC = Color(0xFFB7791F)
val BusC = Color(0xFF2F7D6D)
val AcC = Color(0xFF4A67B8)

/** Screen gutters and gaps, shared so every screen lines up. */
val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
val Gap = 12.dp
private val CardShape = RoundedCornerShape(18.dp)

private val Light = lightColorScheme(
    primary = Color(0xFF3B5BDB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFD), onPrimaryContainer = Color(0xFF1B2C78),
    secondaryContainer = Color(0xFFE8EDFD), onSecondaryContainer = Color(0xFF1B2C78),
    background = Color(0xFFF6F6F4), surface = Color(0xFFF6F6F4),
    surfaceContainerLowest = Color(0xFFF6F6F4), surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F0ED), surfaceContainerHigh = Color(0xFFEAEAE6),
    surfaceContainerHighest = Color(0xFFE2E2DD),
    onSurface = Color(0xFF17171A), onSurfaceVariant = Color(0xFF6B6B73),
    outline = Color(0xFFCFCFC9), outlineVariant = Color(0xFFE6E6E1),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9DB2FF), onPrimary = Color(0xFF13205A),
    primaryContainer = Color(0xFF1F2B5E), onPrimaryContainer = Color(0xFFDCE3FF),
    secondaryContainer = Color(0xFF1F2B5E), onSecondaryContainer = Color(0xFFDCE3FF),
    background = Color(0xFF0E0E10), surface = Color(0xFF0E0E10),
    surfaceContainerLowest = Color(0xFF0E0E10), surfaceContainerLow = Color(0xFF18181B),
    surfaceContainer = Color(0xFF1E1E22), surfaceContainerHigh = Color(0xFF26262B),
    surfaceContainerHighest = Color(0xFF2E2E34),
    onSurface = Color(0xFFECECEF), onSurfaceVariant = Color(0xFF9A9AA3),
    outline = Color(0xFF3A3A42), outlineVariant = Color(0xFF2A2A30),
)

private fun type(): Typography {
    val t = Typography()
    return t.copy(
        headlineMedium = t.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = t.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = t.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
        labelSmall = t.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = remember { type() }, content = content)

/** Times and numbers line up when every digit has the same width. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

// ---- icons not in material-icons-core ----
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

object AppIcons {
    val Bus by lazy {
        icon(
            "bus",
            "M4,16c0,0.88 0.39,1.67 1,2.22V20c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-1h8v1c0,0.55 0.45,1 1,1h1c0.55,0 1,-0.45 1,-1v-1.78c0.61,-0.55 1,-1.34 1,-2.22V6c0,-3.5 -3.58,-4 -8,-4s-8,0.5 -8,4v10zM7.5,17c-0.83,0 -1.5,-0.67 -1.5,-1.5S6.67,14 7.5,14s1.5,0.67 1.5,1.5S8.33,17 7.5,17zM16.5,17c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5zM18,11H6V6h12v5z",
        )
    }
    val Wifi by lazy {
        icon(
            "wifi",
            "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        )
    }
    val Task by lazy {
        icon(
            "task",
            "M19,3h-4.18C14.4,1.84 13.3,1 12,1s-2.4,0.84 -2.82,2H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3zM12,3c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1S11.45,3 12,3zM10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9L10,17z",
        )
    }
}

// ---- formatting ----
fun fmtTime(min: Int): String {
    val h = min / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, min % 60, if (h < 12) "AM" else "PM")
}

fun fmtClock(min: Int): String {
    val h = min / 60
    return "%d:%02d".format(if (h % 12 == 0) 12 else h % 12, min % 60)
}

fun amPm(min: Int) = if (min / 60 < 12) "AM" else "PM"

fun fmtIn(min: Int): String = when {
    min <= 0 -> "now"
    min < 60 -> "in $min min"
    min % 60 == 0 -> "in ${min / 60}h"
    else -> "in ${min / 60}h ${min % 60}m"
}

fun dayName(dow: Int): String = java.time.DayOfWeek.of(dow).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
fun fmtDate(d: LocalDate): String = dayFmt.format(d)

fun relDay(d: LocalDate, today: LocalDate): String = when (d.toEpochDay() - today.toEpochDay()) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    else -> fmtDate(d)
}

fun pctColor(pct: Double, th: Int): Color = when {
    pct >= th + 5 -> PresentC
    pct >= th -> OffC
    else -> AbsentC
}

fun statusColor(c: Char?): Color = when (c) {
    PRESENT -> PresentC
    ABSENT -> AbsentC
    OFF -> OffC
    else -> Color.Gray
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

/** The one card style: white surface, hairline border, generous padding. */
@Composable
fun Block(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** A card that holds a list of rows separated by hairlines. Use [GroupRow] inside. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape),
        content = content,
    )
}

/** One row of a [Group]; draws the divider above itself unless it's the first. */
@Composable
fun GroupRow(first: Boolean, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    if (!first) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (sub != null) {
                Text(sub.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        action?.invoke()
    }
}

/** Round, quiet icon button used in screen headers. */
@Composable
fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = if (onTrailing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrailing).padding(horizontal = 6.dp, vertical = 4.dp) else Modifier,
            )
        }
    }
}

/** Small tag. Tinted by default; [solid] for the one thing on a card that should pop. */
@Composable
fun Pill(text: String, color: Color, solid: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.merge(Tabular),
        fontWeight = FontWeight.SemiBold,
        color = if (solid) Color.White else color,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (solid) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Ring(
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    label: String? = null,
    labelStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(s))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, style = labelStyle.merge(Tabular), fontWeight = FontWeight.SemiBold)
    }
}

/** Present / Absent / Cancelled as one segmented control. Tapping the selected segment clears it. */
@Composable
fun StatusButtons(status: Char?, onSet: (Char?) -> Unit) {
    val opts = remember { listOf(Triple(PRESENT, "Present", PresentC), Triple(ABSENT, "Absent", AbsentC), Triple(OFF, "Cancelled", OffC)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((c, label, col) in opts) {
            val sel = status == c
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) col else Color.Transparent)
                    .clickable { onSet(if (sel) null else c) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Neutral two-or-more way switch (e.g. bus direction). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Up to three suggestions, then "Custom". When the current value isn't a suggestion, the Custom chip shows it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(presets: List<Pair<T, String>>, selected: T, customText: String, onPick: (T) -> Unit, onCustom: () -> Unit) {
    val isCustom = presets.none { it.first == selected }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.take(3).forEach { (v, label) -> FilterChip(selected = selected == v, onClick = { onPick(v) }, label = { Text(label) }) }
        FilterChip(selected = isCustom, onClick = onCustom, label = { Text(if (isCustom) customText else "Custom") })
    }
}

/** Quick number entry for the Custom chip. */
@Composable
fun NumberDialog(title: String, initial: Int, suffix: String, range: IntRange, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                suffix = { Text(suffix) },
                isError = value == null || value !in range,
                supportingText = { Text("${range.first}–${range.last}") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = value != null && value in range, onClick = { onPick(value!!); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Class length chips: 1h, 2h, Custom (minutes). A "1h" class is 50 minutes. */
@Composable
fun LengthChoice(dur: Int, onChange: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    ChoiceRow(listOf(50 to "1h", 110 to "2h"), dur, "$dur min", onChange) { custom = true }
    if (custom) NumberDialog("Class length", dur, "min", 10..300, onDismiss = { custom = false }, onPick = onChange)
}

/** Start time chips: 9, 10, 11, Custom. */
@Composable
fun StartChoice(start: Int, onChange: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    ChoiceRow(listOf(540 to "9:00", 600 to "10:00", 660 to "11:00"), start, fmtTime(start), onChange) { custom = true }
    if (custom) TimeDialog(start, onDismiss = { custom = false }, onPick = onChange)
}

/** Date chips: Today, Tomorrow, Custom. */
@Composable
fun DayChoice(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    var custom by remember { mutableStateOf(false) }
    ChoiceRow(listOf(today to "Today", today.plusDays(1) to "Tomorrow"), day, fmtDate(day), onChange) { custom = true }
    if (custom) DateDialog(day, onDismiss = { custom = false }, onPick = onChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(st) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val st = rememberTimePickerState(initial / 60, initial % 60, false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(st.hour * 60 + st.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimeInput(st) },
    )
}
