# ResQTeam — Milestones 1 & 2

Rescue-operator companion app for the ResQMesh disaster communication system.
This is a **separate app** from `ResQMesh` (the civilian phone-mesh app) —
different package (`com.resqteam.app`), different transport (Bluetooth
Classic RFCOMM to an ESP32, not Nearby Connections between phones), different
purpose (dashboard for a rescue operator, not a distress button for a
civilian).

```
LoRa (future) → ESP32 → Bluetooth Classic → ResQTeam  (this project)
```

## Architecture

```
ui/                     DashboardScreen, IncidentDetailScreen, DashboardViewModel
 ↓
repository/
 └── IncidentRepository  parses raw lines, dedups by messageId, writes Room,
                          fires notifications for CRITICAL/HIGH incidents
 ↓
bluetooth/
 └── BluetoothGatewayManager  RFCOMM connect/read/retry loop to "ResQTeam-ESP32",
                               transport only — no parsing/business logic
 ↓
data/
 ├── ResQMessage.kt       wire packet model + validating JSON parser
 ├── IncidentEntity/Dao   Room persistence, priority-first sort
 └── OperatorIdManager    local RESQ-OP-xx identity for acknowledgements
notification/
 └── EmergencyNotificationManager   high-importance channel for new CRITICAL/HIGH
```

`BluetoothGatewayManager` never parses packets and `IncidentRepository` never
touches `BluetoothSocket` directly — kept separate per the spec so the LoRa
milestone can swap in a real multi-hop ESP32 without touching the UI/DB layers.

## What's implemented (Milestones 1–2 of the spec's priority order)

| Spec item | Status |
|---|---|
| ESP32 Bluetooth Classic bridge, sends test string + JSON packets | ✅ (`arduino/ResQTeam_ESP32_Bridge`) |
| ResQTeam Bluetooth connection, auto-reconnect, survives disconnect | ✅ |
| ResQMesh packet parsing + structural validation, invalid packets discarded | ✅ |
| Incident creation, dedup by `messageId` | ✅ |
| Priority-first sort (priority DESC, then newest) | ✅ |
| Incident details screen | ✅ |
| Critical/High notifications (high-importance channel) | ✅ |
| Location display (raw lat/lon, no map yet) | ✅ (map itself is a later "nice to have") |
| Local storage (Room), survives ESP32 disconnect | ✅ |
| Status workflow: NEW → ACKNOWLEDGED → RESPONDING → RESCUED/RESOLVED | ✅ |
| Operator ID (`RESQ-OP-xx`), stamped on acknowledgement | ✅ |

## Deliberately not built yet (per the spec's own "don't overbuild" rule)

Bottom nav (Incidents/Map/History/Settings tabs), filters, sorting options,
search, the map screen itself, packet-statistics/diagnostics screen, and the
LoRa/SX1278 receive path on the ESP32 are all "nice to have" / "future" in
the spec's own priority list (section 46) and come after this Bluetooth leg
is proven on real hardware. The single-screen dashboard + detail view here
covers every "MUST WORK" item (1–10) plus notifications.

## Files created

```
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.properties
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{strings,colors,themes}.xml
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/resqteam/app/MainActivity.kt
app/src/main/java/com/resqteam/app/bluetooth/BluetoothGatewayManager.kt
app/src/main/java/com/resqteam/app/data/ResQMessage.kt
app/src/main/java/com/resqteam/app/data/IncidentEntity.kt
app/src/main/java/com/resqteam/app/data/IncidentDao.kt
app/src/main/java/com/resqteam/app/data/AppDatabase.kt
app/src/main/java/com/resqteam/app/data/OperatorIdManager.kt
app/src/main/java/com/resqteam/app/notification/EmergencyNotificationManager.kt
app/src/main/java/com/resqteam/app/repository/IncidentRepository.kt
app/src/main/java/com/resqteam/app/ui/theme/Theme.kt
app/src/main/java/com/resqteam/app/ui/DashboardViewModel.kt
app/src/main/java/com/resqteam/app/ui/DashboardScreen.kt
app/src/main/java/com/resqteam/app/ui/IncidentDetailScreen.kt
arduino/ResQTeam_ESP32_Bridge/ResQTeam_ESP32_Bridge.ino
```

No existing files were modified — this is a brand-new project alongside
`ResQMesh-Phase1`.

## How to test (1 ESP32, 1 Android phone, API 26+)

1. Flash `arduino/ResQTeam_ESP32_Bridge/ResQTeam_ESP32_Bridge.ino` to an
   ESP32 dev board (any board with classic Bluetooth works — no SX1278
   needed for this milestone).
2. On the rescue phone, open Bluetooth settings and pair **ResQTeam-ESP32**
   once (classic SPP; no PIN on most stacks).
3. Open this project in Android Studio, let Gradle sync, run on the phone.
4. Grant the Bluetooth + notification permission prompts.
5. The dashboard's gateway card should move from "ResQTeam-ESP32 NOT
   PAIRED"/"CONNECTING…" to **GATEWAY CONNECTED**.
6. In the Arduino Serial Monitor (115200 baud), type `hello` + Enter.
   The dashboard should show a **"Test Packet Received"** banner with the
   raw string — this proves the Bluetooth Classic pipe end to end
   (spec Milestone 1).
7. Type `trapped`, `medical`, `evac`, `supply` (each on its own line).
   Each should appear as a new incident card, and the list should sort
   **TRAPPED/MEDICAL (CRITICAL) above EVACUATION (HIGH) above SUPPLIES
   (MEDIUM)** regardless of the order you sent them in (spec Milestone 3 /
   section 39).
8. Send `trapped` again with the Serial Monitor still open — same
   `sourceNodeId` will get a *new* random `messageId`, so to test dedup
   specifically, resend the exact same line the sketch just printed via
   `[BT TX]` — the incident count should not increase, only "Duplicate
   packets" on that incident's detail screen.
9. Kill the ESP32 (unplug/reset) — the app should show **GATEWAY
   DISCONNECTED** without crashing, and previously received incidents
   should remain visible. Reconnect and it should recover automatically
   within a few seconds, or tap **RECONNECT**.

### Common issues

- **Stuck on "ResQTeam-ESP32 NOT PAIRED"**: pair it from Android's own
  Bluetooth settings first — this app only looks at already-bonded devices,
  it doesn't do discovery/pairing UI itself.
- **"BLUETOOTH PERMISSION NEEDED"**: on Android 12+, Settings → Apps →
  ResQTeam → Permissions → Nearby devices must be granted.
- **No notification for `trapped`/`medical`**: check the notification
  permission was granted (Android 13+) and that the app's notification
  channel isn't muted in system settings.

## Next milestone

Add the SX1278 LoRa receive path on the ESP32 (spec sections 41–44), keeping
it in a separate module from `SerialBT` forwarding, so the exact same
Android code above starts receiving real over-the-air ResQMesh packets
instead of Serial Monitor test commands.
