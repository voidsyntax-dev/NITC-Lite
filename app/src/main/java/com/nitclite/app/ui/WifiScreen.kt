package com.nitclite.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nitclite.app.wifi.Portal
import com.nitclite.app.wifi.WifiPrefs
import com.nitclite.app.wifi.WifiWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WifiScreen() {
    val ctx = LocalContext.current
    val prefs = remember { WifiPrefs(ctx) }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(prefs.user) }
    var pass by remember { mutableStateOf(prefs.pass) }
    var showPass by remember { mutableStateOf(false) }
    var networks by remember { mutableStateOf(prefs.networks) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var ssid by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Checking…") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf(prefs.log) }
    val lastMsg by WifiPrefs.lastMessage

    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    var hasLocation by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var hasNotif by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        hasNotif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        refresh++
    }

    LaunchedEffect(refresh, lastMsg) {
        status = "Checking…"
        val (s, st) = withContext(Dispatchers.IO) { Portal.ssid(ctx) to Portal.status(ctx) }
        ssid = s
        status = st
        log = prefs.log
    }

    fun save() {
        prefs.user = user
        prefs.pass = pass
        prefs.networks = networks.ifBlank { WifiPrefs.DEFAULT_NETWORKS }
    }

    fun signIn() {
        save()
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { Portal.run(ctx.applicationContext, Portal.Mode.MANUAL) }
            busy = false
            refresh++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item {
            ScreenTitle("Campus WiFi", "Hostel & academic auto sign-in") {
                HeaderButton(Icons.Filled.Refresh, "Refresh") { refresh++ }
            }
        }
        item {
            val online = status == "Online"
            Block {
                Text(
                    if (ssid != null) "CONNECTED TO" else "NETWORK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ssid ?: if (status == "Not on WiFi") "No WiFi" else "Name hidden",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (online) PresentC else if (status.startsWith("Sign-in")) OffC else MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
                if (ssid != null && !prefs.matches(ssid!!)) {
                    Text(
                        "Not in your campus list, so the app leaves it alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { signIn() }, enabled = !busy && user.isNotBlank() && pass.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Signing in…" else "Sign in now")
                }
            }
        }

        item { SectionLabel("Credentials") }
        item {
            Block {
                OutlinedTextField(
                    user, { user = it }, label = { Text("Username") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show") } },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Same login works for hostel and academic WiFi. Stored only on this phone and never included in backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { save(); signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save & connect") }
            }
        }

        item { SectionLabel("Automatic") }
        item {
            Block {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto sign-in", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Signs in the moment you join a campus network, even with the app closed, and re-checks every 15 min.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.enabled = it; WifiWatch.sync(ctx) })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    networks, { networks = it },
                    label = { Text("Campus network names") },
                    supportingText = { Text("Comma separated. A WiFi counts as campus if its name contains any of these.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { save(); refresh++ }) { Text("Save list") }
            }
        }

        if (!hasLocation || !hasNotif) {
            item {
                Block {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(OffC, 7.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("PERMISSIONS NEEDED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (!hasLocation) {
                        Text("Allow location to read the WiFi name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Android hides the network name without it. Then the app only signs in on login pages hosted inside the campus network.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!hasNotif) {
                        Text("Allow notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        Text("To tell you when a background sign-in succeeds or fails.", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                        permLauncher.launch(perms.toTypedArray())
                    }) { Text("Grant") }
                }
            }
        }

        item {
            Block {
                Text("Quick Settings tile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pull down the notification shade, tap the pencil, and drag \"Campus WiFi\" in. One tap signs you in from anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (log.isNotEmpty()) {
            item { SectionLabel("Activity") }
            item {
                Group {
                    log.take(12).forEachIndexed { i, line ->
                        val (time, msg) = line.split("  ·  ", limit = 2).let { if (it.size == 2) it[0] to it[1] else "" to line }
                        GroupRow(first = i == 0) {
                            Column {
                                Text(msg, style = MaterialTheme.typography.bodyMedium)
                                if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
