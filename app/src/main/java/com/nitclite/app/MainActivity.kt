package com.nitclite.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nitclite.app.data.Backup
import com.nitclite.app.data.Store
import com.nitclite.app.ui.*
import com.nitclite.app.wifi.WifiWatch
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        WifiWatch.sync(this)
    }
}

enum class Tab(val label: String) { Home("Home"), Attend("Attendance"), Tasks("Tasks"), Bus("Bus"), Wifi("WiFi") }

class MainActivity : ComponentActivity() {
    private val startTab = mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        if (intent?.getStringExtra("tab") == "wifi") startTab.value = Tab.Wifi
        setContent { AppTheme { Root(startTab) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("tab") == "wifi") startTab.value = Tab.Wifi
    }

    override fun onResume() {
        super.onResume()
        Store.finalizeDays()
        Backup.auto(this)
    }
}

@Composable
private fun Root(startTab: MutableState<Tab?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var settings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startTab.value) {
        startTab.value?.let { tab = it; settings = false; startTab.value = null }
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L + 50)
            value = LocalDateTime.now()
            Store.finalizeDays()
        }
    }

    var messOpen by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        BackHandler { settings = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsScreen(onBack = { settings = false })
        }
        return
    }
    if (messOpen) {
        BackHandler { messOpen = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MessScreen(now, onBack = { messOpen = false })
        }
        return
    }
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(iconFor(t), contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Home -> HomeScreen(now, go = { tab = it }, openSettings = { settings = true }, openMess = { messOpen = true })
                Tab.Attend -> AttendanceScreen(now, openSettings = { settings = true })
                Tab.Tasks -> TasksScreen(now)
                Tab.Bus -> BusScreen(now)
                Tab.Wifi -> WifiScreen()
            }
        }
    }
}

private fun iconFor(t: Tab): ImageVector = when (t) {
    Tab.Home -> Icons.Filled.Home
    Tab.Attend -> Icons.Filled.DateRange
    Tab.Tasks -> AppIcons.Task
    Tab.Bus -> AppIcons.Bus
    Tab.Wifi -> AppIcons.Wifi
}
