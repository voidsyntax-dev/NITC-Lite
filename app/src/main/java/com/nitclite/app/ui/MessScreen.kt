package com.nitclite.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nitclite.app.data.Meal
import com.nitclite.app.data.Mess
import com.nitclite.app.data.Store
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessScreen(now: LocalDateTime, onBack: () -> Unit) {
    val d = Store.data
    val today = now.toLocalDate()
    var dayStr by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(dayStr)
    val mess = d.mess

    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item {
            Column {
                HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                ScreenTitle("Mess menu", mess?.let { Mess.label(it) })
            }
        }
        item {
            Block {
                Text("Your mess", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mess.names.forEach { m ->
                        FilterChip(selected = mess == m, onClick = { Store.update { it.copy(mess = m) } }, label = { Text(Mess.label(m)) })
                    }
                }
                if (mess != null && mess != Mess.VEG) {
                    Spacer(Modifier.height(8.dp))
                    Text("Cycle day on Sunday", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "PG2 and the MBH messes share one 7-day menu, each on a different day. Pick the cycle day your mess serves on Sunday.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..7) {
                            FilterChip(selected = d.messSunday == i, onClick = { Store.update { it.copy(messSunday = i) } }, label = { Text("$i") })
                        }
                    }
                    Text(
                        "Today is cycle day ${Mess.cycleDay(today, d.messSunday)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (mess == null) {
            item { Text("Pick your mess to see its menu.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { dayStr = day.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            relDay(day, today) + if (day == today || day == today.plusDays(1) || day == today.minusDays(1)) " · ${fmtDate(day)}" else "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (mess == Mess.VEG) Mess.label(mess) else "${Mess.label(mess)} · cycle day ${Mess.cycleDay(day, d.messSunday)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { dayStr = day.plusDays(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                }
            }
            val (curDay, cur) = Mess.current(mess, d.messSunday, now)
            items(Mess.day(mess, d.messSunday, day), key = { it.name }) { m ->
                MealCard(m, highlight = day == curDay && m.name == cur.name)
            }
            item {
                Text(
                    "Items with \"/\" are alternatives. The mess may change items.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MealCard(m: Meal, highlight: Boolean) {
    Block {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = MaterialTheme.typography.titleMedium)
            if (highlight) {
                Spacer(Modifier.width(8.dp))
                Pill("Now", OffC)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${fmtTime(m.start)} – ${fmtTime(m.end)}",
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        m.items.forEach { item ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(if (highlight) OffC else MaterialTheme.colorScheme.outline, 6.dp)
                Spacer(Modifier.width(12.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Also: ${m.common}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
