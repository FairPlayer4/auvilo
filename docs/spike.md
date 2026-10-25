# Technischer Spike auf dem S24 Ultra

Ziel: Bevor die eigentliche App gebaut wird, prüfen, ob die kritischen Mechanismen
auf dem echten Gerät zuverlässig funktionieren – vor allem unter Samsungs
Akkumanagement und über mehrere Tage.

## 1. Verbindung Handy ↔ PC

### Am Handy (einmalig)
1. **Entwickleroptionen freischalten:** Einstellungen → Telefoninfo →
   Softwareinformationen → 7× auf **Buildnummer** tippen (PIN bestätigen).
2. Einstellungen → **Entwickleroptionen** → **USB-Debugging** einschalten.

### Variante A: USB-Kabel (bevorzugt, falls es ohne Treiber klappt)
1. Handy mit einem **Datenkabel** anschließen (manche Ladekabel können keine Daten).
2. Am Handy erscheint "USB-Debugging zulassen?" → **Immer von diesem Computer
   zulassen** → Zulassen.
3. Am PC: `adb devices` muss das Gerät als `device` zeigen.

Risiko: Windows braucht für die ADB-Schnittstelle einen Treiber. Neuere Geräte
melden sich oft so, dass Windows den Standardtreiber (WinUSB) selbst lädt. Wenn
nicht, bräuchte der Samsung-USB-Treiber Adminrechte → dann Variante B.

### Variante B: WLAN (Wireless Debugging, ohne Treiber)
1. Handy und PC im **selben Netz** (Alternative, falls das Firmen-WLAN Geräte
   voneinander abschottet: PC mit dem Hotspot des Handys verbinden).
2. Entwickleroptionen → **Drahtloses Debugging** einschalten → **Gerät mit
   Kopplungscode koppeln**. Das Handy zeigt IP:Port und einen 6-stelligen Code.
3. Am PC: `adb pair <IP>:<Port> <Code>`, danach `adb connect <IP>:<Port des
   Hauptbildschirms>`.
4. Die Kopplung bleibt bestehen; nach Neustart des Handys nur erneut `adb connect`.

## 2. Was über adb gemacht wird

- **Nur lesen:** Geräte-Infos (Android-Version, One-UI-Version, Akku- und
  Doze-Status, ob On-Device-KI/AICore vorhanden ist).
- **Spike-App installieren** und wieder deinstallieren.
- **Logs** der Spike-App auslesen (`logcat`, eigene Protokolldatei der App).
- Optional: Berechtigungen der Spike-App per Befehl setzen statt durch die
  Einstellungen zu klicken (exakte Alarme, Vollbild, Akku "Nicht eingeschränkt").

Keine anderen Apps, Daten oder Einstellungen werden angefasst. USB-/Drahtloses
Debugging kann nach dem Spike wieder ausgeschaltet werden.

## 3. Spike-App

Kleine Test-App (nicht die spätere App), die protokolliert, was wann passiert:

| Test | Prüft | Erfolgskriterium |
|---|---|---|
| Exakter Alarm (`setAlarmClock`) | mehrmals täglich, auch nachts im Doze | Auslösung ≤ 1 min verspätet |
| Vollbild über Sperrbildschirm | Full-Screen-Intent bei gesperrtem, lautlosem Handy | Vollbild erscheint, auch bei "Nicht stören" |
| Vibrationsmuster | lange/ungewöhnliche Muster, lautlos-Modus | Vibration spürbar unterscheidbar |
| Alarmton (Alarm-Audiostream) | 18–21 Uhr laut trotz Lautlos | Ton hörbar |
| Wiederholte Benachrichtigung | bis zur Reaktion | läuft weiter, bis bestätigt |
| Geofence "zuhause" | Ankunft/Verlassen über mehrere Tage | Auslösung, Verzögerung gemessen |
| "Um 19 Uhr zuhause?" | Standort/Geofence-Status zum Zeitpunkt | korrekte Entscheidung |
| Neustart | Alarme nach Reboot neu geplant | keine Alarme verloren |
| Samsung-Akkumanagement | App mehrere Tage nicht öffnen | keine Alarme verloren |

Laufzeit: ca. 3–7 Tage normale Nutzung, danach Logs auswerten.

## 4. Die Spike-App (`spike/`)

Gebaut am 2026-10-18 mit AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1 (Wrapper),
compileSdk/minSdk/targetSdk 37. Paket `com.avlreminder.spike`, App-Name "AVL Spike".

Bauen: `cd spike && gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

### Was die App tut

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Steuerzentrale: Berechtigungen prüfen/erteilen, Schnelltest und Mehrtagestest starten/stoppen, Sofort-Tests, Zuhause setzen/prüfen, Protokoll anzeigen |
| `SpikeApp.kt` | Protokolliert jeden Prozessstart (zeigt, wann das System die App beendet hatte) |
| `TestPlan.kt` | Schnelltest: 60 min, alle 5 min. Mehrtagestest: feste Zeiten **02:00, 08:00, 13:00, 19:00, 20:30**; Modus nach Uhrzeit: 7–21 Uhr sichtbar, 18–21 Uhr laut, sonst still |
| `AlarmScheduler.kt` | Plant Alarme mit `setAlarmClock()` (Kette: jeder Plan-Alarm plant den nächsten); erkennt Alarme, die nie ausgelöst haben ("VERPASST") |
| `AlarmReceiver.kt` | Wird vom System geweckt: misst die **Verspätung**, protokolliert den Energiespar-Zustand, startet den Alarm |
| `AlarmService.kt` | Foreground-Service: Vollbild-Benachrichtigung, **eigenes Vibrationsmuster** in Schleife, Alarmton im Modus laut; Ende durch *Bestätigen*, *+5 min* oder nach 3 min ohne Reaktion |
| `AlarmActivity.kt` | Vollbild über dem Sperrbildschirm mit *Bestätigen* / *+5 min* |
| `HomeLocation.kt`, `GeofenceReceiver.kt` | Geofence "zuhause" (150 m) mit Ankunft/Verlassen; um 19:00 Prüfung "bin ich zuhause?" |
| `BootReceiver.kt` | Nach Neustart: Alarm und Geofence neu registrieren, verpasste Alarme melden |
| `SpikeLog.kt` | Protokoll in `files/spike-log.txt` (nur in der App, nichts verlässt das Handy) |

**Keine Netzwerk-Berechtigung**: Die App kann nichts ins Internet senden. Standort
bleibt im App-Speicher (Koordinaten "zuhause" + Protokolleinträge mit Distanz).

### Berechtigungen
- Automatisch bei Installation: exakte Alarme (`USE_EXACT_ALARM`), Vibration,
  Foreground-Service, Start nach Neustart.
- In der App zu erteilen: Benachrichtigungen, Vollbild, Akku "nicht optimieren",
  Standort genau + "immer erlauben".

## 5. Schnelltest: eine Stunde

Ziel: In einer Stunde genug Daten, um mit der Implementierung zu starten. Die echten
Samsung-Effekte über mehrere Tage (Schlafende Apps, Standby-Buckets) liefert danach der
Mehrtagestest. Doze und Akkumanagement **simulieren** wir per adb, statt Stunden zu warten.

Die App erzeugt im Schnelltest **alle 5 min einen Alarm** (12 Stück), mit wechselnder
Stufe: sichtbar → laut → still, jeweils mit Zuhause-Prüfung (Distanz im Protokoll).
Ein Alarm ohne Reaktion endet nach 3 min.

| Zeit | Wer | Was | Prüft |
|---|---|---|---|
| Vorher (~10 min, USB) | beide | Installieren, Berechtigungen, *Zuhause setzen*, 3 Sofort-Tests (still/sichtbar/laut) mit **gesperrtem, lautlosem** Handy | Vollbild, Vibrationsmuster, Alarmton trotz lautlos |
| 0 | du | *Schnelltest Start*, Handy sperren, liegen lassen | |
| 0–15 (USB) | ich (adb) | Akku "abgesteckt" simulieren, **Deep Doze erzwingen**; danach App in Bucket **restricted** | Alarme in Doze/restricted pünktlich? |
| 15–35 (ohne USB) | du | **Mind. 300 m weg** von zuhause, ~10 min draußen, zurück. Handy gesperrt in der Tasche. Einen Alarm bestätigen, einen ignorieren | Geofence verlassen/ankommen + Verzögerung, Alarm in der Tasche |
| 35–45 | du | Handy **neu starten**, einmal entsperren, App **nicht** öffnen | Alarme nach Neustart |
| 45–60 (USB) | ich (adb) | App-Prozess beenden (wie System), am Ende **Stopp erzwingen** (wie Samsung-Tiefschlaf) | Neustart des Prozesses, Erkennung verpasster Alarme |
| 60 | ich | Protokoll auslesen, Simulationen zurücksetzen, Mehrtagestest starten | |

adb-Befehle (alle umkehrbar, am Ende zurückgesetzt):
- `dumpsys battery unplug` / `reset` – Laden ausblenden, sonst gibt es kein Doze
- `dumpsys deviceidle force-idle` / `unforce` – Deep Doze sofort
- `am set-standby-bucket com.avlreminder.spike restricted` / `active`
- `am kill com.avlreminder.spike` – Prozess beenden wie bei Speichermangel
- `am force-stop com.avlreminder.spike` – löscht alle Alarme der App (schlimmster Fall)

Ergebnis danach: Was in der Stunde geklärt ist und was erst der Mehrtagestest zeigt.

### Ergebnis Schnelltest (2026-10-24, 15:30–16:30)

| Test | Ergebnis |
|---|---|
| Exakter Alarm (`setAlarmClock`) | ✅ **11 von 11** ausgelösten Alarmen mit **0 s Verspätung** – im leichten Doze, ohne Akku-Ausnahme, nach Prozess-Kill und nach Neustart |
| Vollbild über Sperrbildschirm | ✅ immer angezeigt (gesperrt, Klingelmodus Vibrieren); Vollbild-Berechtigung bei Sideload automatisch erteilt |
| Foreground-Service `systemExempted` | ✅ funktioniert auf Android 17 (Dauer-Vibration, Alarmton, Timeout nach 3 min) |
| Alarmton im Modus Vibrieren | ✅ klingelt über Wecker-Lautstärke (4/15); Samsung-Standardton wird lauter |
| Still | ✅ nur in der Benachrichtigungsleiste, kein Bildschirm, kein Ton |
| Bestätigen | ✅ über Vollbild und über Benachrichtigungs-Aktion |
| Geofence | ✅ Registrierung → "angekommen" nach 12 s; **Verlassen** bei ~300 m sofort; **Ankunft** mit ~1,5 min Verzögerung (Standort-Fix 85 s alt) |
| Zuhause-Prüfung im Hintergrund | ✅ Distanz korrekt (0–5 m zuhause, 326/347 m unterwegs), Genauigkeit 15–100 m |
| Neustart | ✅ BOOT_COMPLETED nach Entsperren, Alarm und Geofence neu registriert, nächster Alarm pünktlich |
| Prozess beendet (`am kill`) | ✅ System startet die App für den Alarm neu, pünktlich |
| Stopp erzwungen (`am force-stop`) | ⚠️ erwartungsgemäß: **alle Alarme und Geofence gelöscht**, Alarm fällt aus. Beim nächsten App-Start liefert Android BOOT_COMPLETED (Verhalten seit Android 15) → App erkennt den verpassten Alarm und stellt alles wieder her |
| Tiefer Doze / Standby-Bucket per adb | ❌ von Samsung blockiert (nur leichter Doze erzwingbar) → Mehrtagestest nötig |

**Folgerungen für die Implementierung**
- `setAlarmClock` + Foreground-Service `systemExempted` + Vollbild-Activity ist die
  richtige Basis – technisch trägt der Ansatz.
- Alle Reminder müssen in der **Datenbank** stehen; Alarme und Geofences werden bei
  Neustart **und** beim App-Start aus der DB neu aufgebaut (Force-Stop-Fall).
- Verpasste Reminder werden erkannt und gemeldet, nicht still übergangen (passt zu
  Leitprinzip 1).
- Ankunft zuhause kommt mit 1–2 min Verzögerung – für "bei Ankunft trainieren" gut genug.
- Offen für den Mehrtagestest: echter Tief-Doze in der Nacht, Samsungs
  "Schlafende Apps" nach Tagen ohne Öffnen, Akkuverbrauch durch Geofence.
- Zu klären: `setAlarmClock` zeigt den Reminder als "nächster Wecker" in Statusleiste
  und Sperrbildschirm. Ggf. nur für eskalierende Reminder nutzen, stille Reminder
  über `setExactAndAllowWhileIdle`.

### Zwischenstand Mehrtagestest (2026-10-25, 16:00)

Laufzeit seit 2026-10-24 16:35, knapp 1 Tag, App nicht geöffnet.

| Test | Ergebnis |
|---|---|
| Plan-Alarme | ✅ **5 von 5** (19:00, 20:30, 02:00, 08:00, 13:00), Verspätung 0–1 s |
| Nacht (02:00) | ✅ pünktlich trotz Energiesparmodus (`powerSave=true`) und "Nicht stören" (`dnd=2`) |
| Prozess vom System beendet | ✅ vor fast jedem Alarm neu gestartet – Android weckt die App zuverlässig |
| Vollbild | ✅ bei gesperrtem Handy (08:00, 13:00, 19:00); bei benutztem Handy Banner, bestätigt per Benachrichtigung |
| Geofence | ✅ Verlassen 09:31 / Ankunft 09:36 (Standort-Fix 117 s alt), Verlassen 13:54 – stimmt, Handy ist seitdem unterwegs |
| Standby-Bucket | `bucket=5` durchgehend, Samsung hat die App bisher nicht schlafen gelegt |

Noch offen bis zum Ende: Samsungs "Schlafende Apps" nach mehreren Tagen ohne
Öffnen, Ankunft nach längerer Abwesenheit, Akkuverbrauch.

### Ablauf auf dem Handy (Mehrtagestest)
1. Installieren: `adb install app-debug.apk`
2. App öffnen, alle Berechtigungen auf ✅ bringen.
3. Zuhause: Zuhause → *Aktuellen Standort als Zuhause setzen*.
4. Sofort-Tests (still / sichtbar / laut), jeweils Handy sperren und lautlos legen.
5. *Testplan Start* → 3–7 Tage normal nutzen, App möglichst **nicht** öffnen.
6. Protokoll auslesen: `adb shell run-as com.avlreminder.spike cat files/spike-log.txt`
7. Danach: `adb uninstall com.avlreminder.spike`
