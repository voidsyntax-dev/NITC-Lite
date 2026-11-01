# NITC Lite

Android client for NIT Calicut students: attendance tracking with threshold projections, a deadline tracker, shuttle and mess timetables, and automatic sign-in to the campus WiFi captive portal.

- **Language:** Kotlin 
- **UI:** Jetpack Compose, Material 3
- **Platform:** `minSdk` 26, `compileSdk` / `targetSdk` 35
- **Persistence:** single JSON document, no database
- **Build:** Gradle Kotlin DSL, Compose compiler plugin, JVM target 17

## Architecture

```
MainActivity (Compose root, bottom nav, lifecycle hooks)
    │
    ├── ui/*            Stateless composables reading Store.data
    │
    ├── data/           Pure Kotlin domain logic (no Android types except Context in Backup)
    │   ├── Store        Singleton state holder: Compose snapshot state + atomic file writes
    │   ├── Model        Immutable AppData, Course, Task, Extra; hand-written JSON codec
    │   ├── Attendance   Session expansion, per-course stats, skip/need math
    │   ├── Slots        Institute slot grid → weekly SlotTime list
    │   ├── Mess         Static menu tables, rotation calendar, current-meal selection
    │   ├── Buses        Static shuttle timetable
    │   └── Backup       JSON/CSV export, SAF-based import and daily auto-backup
    │
    └── wifi/           Captive-portal client and Android triggers
        ├── Portal       Orchestrates probe → login → verify; vendor-specific login paths
        ├── Http         HttpURLConnection bound to a specific Network, manual redirects, cookie jar
        ├── Html         Form discovery, field filling, redirect and failure detection
        ├── Background   NetworkCallback (PendingIntent), CheckJob, BootReceiver, LoginTile
        └── WifiPrefs    SharedPreferences("wifi"): credentials, enable flag, learned portal, log
```

There is no ViewModel layer. `Store.data` is a `mutableStateOf<AppData>`, so composables recompose when it changes. Every mutation goes through `Store.update { old -> new }`, which replaces the immutable snapshot and enqueues a write.

## Data model

`AppData` is persisted as `filesDir/data.json` (schema `{"app":"nitc-lite","version":1,...}`). Notable fields:

| Field | Type | Notes |
| --- | --- | --- |
| `courses` | `List<Course>` | `slot`, `plus`, `custom: List<SlotTime>?`, `threshold: Int?`, prior attended/total for imported history |
| `records` | `Map<String, Char>` | Key `"yyyy-MM-dd\|courseId\|startMin\|dur"` → `P` / `A` / `C` |
| `extras` | `List<Extra>` | One-off or rescheduled classes |
| `overrides` | `Map<String, Int>` | Date → weekday (1..5) whose timetable applies that day |
| `tasks` | `List<Task>` | `type`, optional `courseId`, `due`, `time`, `done` |
| `finalizedThrough` | ISO date | Watermark: every day ≤ this date is settled |
| `semStart` | ISO date | Attendance counting starts here |
| `mess`, `messSunday` | `String?`, `Int` | Mess name and the cycle day it serves on Sunday (1..7) |

Session identity is the record key itself, so a class is uniquely `(date, course, start, duration)`. Changing a course's slot does not rewrite history for settled days.

### Persistence

`Store.persist()` snapshots `data`, serializes it on a single-thread executor, and writes via `android.util.AtomicFile` (`startWrite` / `finishWrite` / `failWrite`), so a crash mid-write cannot corrupt the file. On decode failure the store falls back to an empty `AppData()` rather than crashing.

### End-of-day finalization

`Store.finalizeDays()` runs on every resume, on each minute tick in `Root`, and at init. It walks from `finalizedThrough + 1` up to yesterday (capped at 400 iterations) and `putIfAbsent`s `PRESENT` for every scheduled session that has no record. Settled days are then excluded from timetable generation, so later edits to the timetable cannot retroactively change them. `setOverride` on a settled date rebuilds that day's records against the new timetable, keeping existing marks by key.

## Attendance computation

`Attendance.sessions(data, date)` returns the union of:

1. Timetable sessions for `weekdayFor(date)` (the override, or the real weekday), only if the date is after `finalizedThrough` and not before `semStart`;
2. Recorded keys matching the date prefix (settled days come entirely from here);
3. `Extra` entries for the date.

Sessions are deduplicated by key and sorted by start time.

`Attendance.stats` tallies `P`, `A` and `C` per course. Then:

```
attended = priorAttended + present
total    = priorTotal + present + absent      // cancelled classes are excluded
pct      = attended / total × 100
```

For threshold `th` (fraction `t = th / 100`):

- classes that can be skipped: `max(0, floor(attended / t − total))`
- classes that must be attended in a row to recover: `ceil((t·total − attended) / (1 − t))`

Both use a small epsilon (`1e-9`) to absorb floating-point error at exact boundaries.

## Campus WiFi sign-in

### Triggers

- **`NetReceiver`:** registered through `ConnectivityManager.registerNetworkCallback` with a `PendingIntent` targeting a `BroadcastReceiver`, so it fires for WiFi connections even when the process is dead. It calls `goAsync()`, waits 800 ms for the link to settle, then runs `Portal.run(ON_CONNECT)`. A 10-second debounce in `Portal` prevents repeat runs.
- **`CheckJob`:** a persisted `JobScheduler` job at a 15-minute period, to detect portal session expiry while still connected.
- **`LoginTile`:** Quick Settings tile calling `Portal.run(MANUAL)` off the main thread.
- **`BootReceiver`:** re-registers the callback and job on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.

### Flow

`Portal.run` is `@Synchronized`:

1. Resolve the WiFi `Network` from `allNetworks`. Bail if it isn't WiFi or the SSID does not match `MBH…` / `NITC…`.
2. Probe `http://connectivitycheck.gstatic.com/generate_204`. A `204` means online. A `3xx`, a JS redirect, or an HTML page with a form means a captive portal.
3. If the probe fails at the transport level, retry once after 1.5 s. Then try the portal by IP: `http://1.1.1.1/` (no DNS required, and pfSense-style portals intercept it). If that also fails, search the default gateway and DNS servers on ports 8002, 8000 and 80, plus the last learned portal URL.
4. If the SSID is hidden (no location permission), trust the login host only if it is private RFC 1918 space, `*.nitc.ac.in`, or the previously learned host.
5. Walk the portal for up to six hops (`login(...)`):
   - **Sophos/Cyberoam** (`:8090`, `httpclient.html`): `POST /login.xml` with `mode=191`, parse `<message><![CDATA[…]]>`.
   - **Generic (incl. Fortinet-style pages):** find a form with a password field, fill it with `Html.fill`, submit it. Follow `<meta refresh>` / `location.href` redirects with `Html.redirect`. A password-less form is submitted as an "accept terms" step.
   - `Html.looksFailed` checks the response body for rejection text.
6. Re-probe. On success, persist the portal URL and host to `WifiPrefs` (`learnedPage`, `learnedHost`), call `reportNetworkConnectivity(net, true)`, and notify.

### HTTP client

`Http` uses `HttpURLConnection` bound to the specific `Network` (`net.openConnection`), so requests stay on WiFi even when mobile data is the default route. Redirects are followed manually (`instanceFollowRedirects = false`) so each hop is observable. A per-host cookie jar is kept in memory and is not persisted.

`trustAll` is set only after the network has been identified as campus. It swaps in a permissive `TrustManager` and hostname verifier, because portal certificates are typically self-signed. Connect timeout is 6 s, read timeout 8 s.

## Backups

| Format | Writer | Contents |
| --- | --- | --- |
| JSON | `Backup.exportJson` | Full `AppData` in the schema above, pretty-printed (indent 2) |
| CSV | `Backup.exportCsv` | Per-course summary block, blank line, then one row per record with date, time, code, name, status |
| Auto | `Backup.auto` | Once per calendar day, written through SAF `DocumentsContract.createDocument` into the user-chosen tree; `lastAutoBackup` guards against duplicates |

Import validates that the JSON contains `courses` and `records`, then calls `Store.replace`, keeping the local `backupDir` and `lastAutoBackup`.

## Build

Requirements: JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew test                 # JVM unit tests (LogicTest)
./gradlew assembleRelease      # minified and resource-shrunk; uses release signing if configured
```

Windows: replace `./gradlew` with `gradlew.bat`.

### Release signing

`app/build.gradle.kts` reads `keystore.properties` from the project root. If present, the `release` config uses it; otherwise release builds use the debug keystore.

```properties
storeFile=nitclite.jks
storePassword=<password>
keyAlias=<alias>
keyPassword=<password>
```

Keep `keystore.properties` and the `.jks` file out of version control.

## Tests

`app/src/test/.../LogicTest.kt` (JUnit 4, with `org.json` on the JVM classpath) covers:

- timetable expansion against the institute slot grid, the opt-in `+` hour, and custom weekly times;
- per-subject thresholds, stats and skip/recovery math (`statsAndBunkMath`, `recordsDriveStats`);
- mess rotation (`messRotation`), new-semester reset, and one-record-per-class keys;
- JSON round-trip and bus timetable parsing;
- portal HTML handling: Fortinet-style forms, pfSense forms and JS redirects.

End-of-day finalization in `Store` and the WiFi login flow against live hardware are not covered.

## Permissions

| Permission | Used by |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | `Http`, `Portal`, network callback |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | `WifiManager.connectionInfo.ssid` returns `<unknown ssid>` without it |
| `POST_NOTIFICATIONS` | Sign-in result notifications (API 33+) |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` |

`usesCleartextTraffic="true"` is set because captive portals are served over plain HTTP. `allowBackup` is on, with separate rules in `backup_rules.xml` and `data_rules.xml`.

## Known limitations

- Credentials are stored in `SharedPreferences` in plaintext. Application-private, but not encrypted at rest.
- `trustAll` disables certificate validation for requests to the portal host.
- Bus and mess data are hard-coded in `Buses.kt` and `Mess.kt` and must be edited in source.
- Vendor handling is limited to Sophos/Cyberoam (`login.xml`). Everything else, including Fortinet and pfSense, goes through the generic password-form path, which depends on the form's field names and submit behavior.

## Disclaimer

Unofficial student project. Not affiliated with NIT Calicut.
