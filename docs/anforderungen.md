# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-10-18 – erster Entwurf, wird iterativ verfeinert.

## 1. Vision

Eine Android-App, die Erinnerungen **extrem effektiv** macht: Ein Reminder gilt erst
als erfüllt, wenn die Aufgabe nachweislich erledigt (oder bewusst verschoben) wurde.
Dazu kombiniert die App Eskalation, Nachweise, Kontext-Trigger und psychologische
Verstärkung (positiv wie negativ).

Keine Consumer-App: sehr kleine, spezialisierte Zielgruppe, die sich bewusst für
"strenge" Mechanismen entscheidet. Störende/invasive Mechanismen sind gewollt,
solange der Nutzer sie selbst konfiguriert.

## 2. Ausbaustufen

| Stufe | Inhalt | Technik |
|---|---|---|
| **V1** | Einzelnutzer (Kevin), komplett lokal | Kein Backend, Sideload-APK |
| V2 | Abgleich mit Zielen / Prioritäten, Auswertungen | lokal |
| V3 | Mehrere Personen, gemeinsame Reminder, gegenseitige Bestätigung | Backend nötig |

"Alignment" umfasst langfristig alle drei Bedeutungen:
1. **Erledigung erzwingen** (V1-Fokus)
2. **Abgleich mit Zielen** (V2)
3. **Abstimmung mehrerer Personen** (V3)

## 3. Mechanismen (Sammlung, noch nicht priorisiert)

### 3.1 Auslösung (Trigger)
- T1 Zeit: einmalig, wiederkehrend, Zeitfenster ("zwischen 14 und 16 Uhr")
- T2 Ort: Betreten/Verlassen eines Geofence
- T3 Kontext: WLAN verbunden/getrennt, Bluetooth-Gerät (Auto, Kopfhörer), Ladegerät
- T4 Kalender: vor/nach Terminen
- T5 Aktivität: z. B. Gerät entsperrt, bestimmte App geöffnet

### 3.2 Wahrnehmung sicherstellen (Audio / Visual)
- W1 Eskalationsstufen: Notification → Ton → lauter → Vollbild-Alarm → Dauerschleife
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören"
- W3 Overlay über anderen Apps
- W4 Vibrationsmuster, ggf. Wear-OS-Uhr
- W5 Sprachausgabe (TTS) mit Aufgabentext

### 3.3 Erledigung nachweisen
- N1 Einfaches Quittieren (bewusst erschwert: z. B. Wischgeste halten, Text abtippen)
- N2 Foto-Nachweis
- N3 QR-/NFC-Tag scannen (Tag am Ort der Aufgabe, z. B. Medikamentenschrank)
- N4 Ortsnachweis (am richtigen Ort quittieren)
- N5 Quiz/Rechenaufgabe (gegen "im Halbschlaf wegdrücken")
- N6 Snooze nur begrenzt / mit Begründung / mit Kosten

### 3.4 Verstärkung (Psychologie)
- P1 Positiv: Streaks, Punkte, Level, variable Belohnungen, Fortschrittsanzeige
- P2 Negativ: Streak-Verlust, Strafpunkte, Sperren von Ablenkungs-Apps bis erledigt
- P3 Commitment: Vorab-Selbstverpflichtung, Begründung ("Warum ist mir das wichtig?")
- P4 Implementation Intentions ("Wenn X, dann tue ich Y")
- P5 Soziale Verpflichtung: Dritte Person wird bei Nicht-Erledigung informiert
- P6 Reflexion: Wöchentlicher Rückblick, Muster erkennen (wann scheitere ich?)

## 4. Technische Machbarkeit (Android, Stand 2026)

Großer Vorteil: **Sideload statt Play Store** → die meisten Play-Richtlinien
(eingeschränkte Berechtigungen) gelten nicht. Berechtigungen müssen aber trotzdem
vom Nutzer in den Systemeinstellungen erteilt werden.

| Mechanismus | Machbar? | Android-Mittel | Anmerkung |
|---|---|---|---|
| T1 Zeit, exakt | ✅ | `AlarmManager.setAlarmClock()` | Umgeht Doze; Berechtigung `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` |
| T2 Geofence | ✅ (mit Latenz) | Play Services Geofencing | Hintergrund-Standort nötig; Auslösung oft 1–5 min verzögert |
| T3 WLAN/BT/Laden | ✅ teilweise | Broadcasts, `ConnectivityManager` Callbacks | Im Hintergrund nur mit Foreground-Service zuverlässig |
| T4 Kalender | ✅ | `CalendarContract` | Leseberechtigung |
| T5 App geöffnet | ⚠️ | `UsageStatsManager` / Accessibility-Service | Funktioniert, aber akkuintensiv bzw. heikle Berechtigung |
| W1/W2 Vollbild, DND-Bypass | ✅ | Full-Screen-Intent, Alarm-Audiostream | `USE_FULL_SCREEN_INTENT` manuell erlauben |
| W3 Overlay | ✅ | `SYSTEM_ALERT_WINDOW` | manuell erlauben |
| W4 Wear OS | ✅ | Notifications spiegeln sich automatisch; eigene Watch-App optional | |
| N2 Foto | ✅ | CameraX | |
| N3 QR / NFC | ✅ | ML Kit Barcode, NFC-API | NFC-Tags kosten ein paar Cent |
| P2 Apps sperren | ✅ / ⚠️ | Accessibility-Service oder Overlay-Blocker | Echte Sperre (Lock-Task) nur als Device Owner – sehr invasiv |
| P5 Dritte informieren (ohne Backend) | ✅ | SMS (`SEND_SMS`), Telegram-Bot, ntfy, E-Mail | Ohne Backend gut lösbar |
| Zuverlässigkeit allg. | ⚠️ | Akku-Optimierung deaktivieren | Hersteller-Akkumanagement (Samsung, Xiaomi …) ist das größte Risiko |

**Fazit:** Alle bisher genannten Mechanismen sind für V1 lokal umsetzbar.
Hauptrisiken sind Zuverlässigkeit im Hintergrund (herstellerabhängig) und
Geofence-Latenz – beides am Zielgerät früh testen.

### Vorgeschlagener Tech-Stack
- Kotlin, Jetpack Compose, Room (lokale DB), WorkManager + AlarmManager
- minSdk 31 (Android 12), targetSdk aktuell
- Build: Gradle; Entwicklung in Android Studio

### Entwicklungsumgebung (geprüft 2026-10-18)
- ❌ Kein Android SDK / Android Studio gefunden
- ❌ Kein JDK im PATH
- → Android Studio installieren (bringt JDK + SDK mit), Gerät per USB-Debugging verbinden

## 5. Offene Fragen

1. Welches Zielgerät (Hersteller, Android-Version)? Gibt es eine Smartwatch?
2. Welche konkreten Reminder-Typen nutzt du heute bzw. wo scheiterst du aktuell?
   (z. B. Medikamente, Sport, Arbeitsaufgaben, Pausen, Anrufe) – die echten
   Fehlschläge bestimmen, welche Mechanismen V1 braucht.
3. Wie "hart" darf negative Verstärkung sein? (Apps sperren? Dritte informieren?)
4. Wer wäre die dritte Person für P5 und über welchen Kanal?
5. Firmenhandy mit MDM oder Privatgerät? (MDM kann Sideload/Berechtigungen blockieren)
6. Datenhaltung: rein lokal ok, oder Backup (z. B. Export-Datei) gewünscht?
