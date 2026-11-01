package com.nitclite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nitclite.app.data.Buses
import com.nitclite.app.data.Trip
import java.time.LocalDateTime

@Composable
fun BusScreen(now: LocalDateTime) {
    var fromEast by rememberSaveable { mutableStateOf(false) }
    var showPast by rememberSaveable { mutableStateOf(false) }
    val trips = if (fromEast) Buses.fromEast else Buses.fromMbh
    val weekend = now.dayOfWeek.value >= 6
    val nowMin = if (weekend) -1 else now.hour * 60 + now.minute
    val upcoming = trips.filter { it.min >= nowMin }
    val past = trips.filter { it.min < nowMin }
    val next = upcoming.firstOrNull()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item { ScreenTitle("Buses", "Campus shuttle · weekdays") }
        item { Segmented(listOf("From MBH", "From East Campus"), if (fromEast) 1 else 0) { fromEast = it == 1 } }

        if (weekend) {
            item {
                Block {
                    Text("No service today", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Buses don't run on Saturday or Sunday. Below is the weekday schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (next != null) {
            item { NextBus(next, nowMin) }
        } else {
            item {
                Block {
                    Text("No more buses today", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "First bus tomorrow at ${fmtTime(trips.first().min)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val rest = if (weekend) upcoming else upcoming.drop(1)
        if (rest.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionLabel(if (weekend) "Schedule" else "Later today") }
                    Legend(fromEast)
                }
            }
            item { TripGroup(rest, if (weekend) null else nowMin, faded = false) }
        }
        if (past.isNotEmpty()) {
            item {
                TextButton(onClick = { showPast = !showPast }) {
                    Text(if (showPast) "Hide earlier buses" else "Show ${past.size} earlier buses")
                }
            }
            if (showPast) item { TripGroup(past, null, faded = true) }
        }
    }
}

@Composable
private fun Legend(fromEast: Boolean) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(BusC, 7.dp)
        Spacer(Modifier.width(5.dp))
        Text("Bus 1/2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!fromEast) {
            Spacer(Modifier.width(12.dp))
            Dot(AcC, 7.dp)
            Spacer(Modifier.width(5.dp))
            Text("A/C", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextBus(t: Trip, nowMin: Int) {
    val color = if (t.ac) AcC else BusC
    Block {
        Text("NEXT BUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtClock(t.min), style = MaterialTheme.typography.displaySmall.merge(Tabular))
            Spacer(Modifier.width(6.dp))
            Text(amPm(t.min), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Pill(fmtIn(t.min - nowMin), color, solid = true)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 8.dp)
            Spacer(Modifier.width(8.dp))
            Text("${t.bus} → ${t.to}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TripGroup(trips: List<Trip>, nowMin: Int?, faded: Boolean) {
    val alpha = if (faded) 0.5f else 1f
    Group {
        trips.forEachIndexed { i, t ->
            val color = if (t.ac) AcC else BusC
            GroupRow(first = i == 0) {
                Text(
                    fmtTime(t.min),
                    style = MaterialTheme.typography.titleSmall.merge(Tabular),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    modifier = Modifier.width(84.dp),
                )
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = alpha)))
                Spacer(Modifier.width(12.dp))
                Text(t.to, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), modifier = Modifier.weight(1f))
                Text(t.bus, style = MaterialTheme.typography.labelLarge, color = color.copy(alpha = alpha))
                if (nowMin != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        fmtIn(t.min - nowMin).removePrefix("in "),
                        style = MaterialTheme.typography.labelLarge.merge(Tabular),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 52.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}
