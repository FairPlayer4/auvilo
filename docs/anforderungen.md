# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-10-18 – zweiter Entwurf, wird iterativ verfeinert.

## 1. Vision

Eine Android-App, die Erinnerungen **extrem effektiv** macht: Ein Reminder gilt erst
als erfüllt, wenn die Aufgabe nachweislich erledigt (oder bewusst verschoben) wurde.
Dazu kombiniert die App Eskalation, Nachweise, Kontext-Trigger und psychologische
Verstärkung – **mit Schwerpunkt auf positiver Verstärkung**.

Keine Consumer-App: sehr kleine, spezialisierte Zielgruppe, die sich bewusst für
"strenge" Mechanismen entscheidet. Störende/invasive Mechanismen sind gewollt,
solange der Nutzer sie selbst konfiguriert.

## 2. Ausgangslage (Problem)

- Heutige Reminder sind schwach und haben keine Bestätigung.
- Sie werden nur erledigt, wenn es **sofort möglich** ist **und** die Motivation reicht.
- Es gibt keine Prüfung, ob ein Reminder tatsächlich abgeschlossen wurde –
  "wegklicken und vergessen" ist der Normalfall.
- Kritisch sind vor allem **anstrengende, wiederkehrende Dinge** (regelmäßiges
  Training, geplante Haushaltsaufgaben), die an manchen Tagen schwerfallen.

## 3. Leitprinzipien

1. **Kein Reminder verschwindet still.** Jeder Reminder endet bewusst: erledigt
   (Ziel oder Minimum), verschoben oder bewusst ausgelassen.
2. **Positiv vor negativ.** Negative Verstärkung nur begrenzt und taktisch, wenn
   sinnvoll (Regeln dafür sind noch zu erarbeiten).
3. **Lieber klein als gar nicht.** An schweren Tagen zählt das Minimalziel.
4. **Nachweis statt Klick.** Bestätigung braucht einen echten Beleg (Foto, Ort, Audio).
5. **Überblick vor Überraschung.** Vorab-Reminder, damit nichts unvorbereitet kommt.

## 4. Ausbaustufen

| Stufe | Inhalt | Technik |
|---|---|---|
| **V1** | Einzelnutzer, komplett lokal, Kernmechanismen | Kein Backend, Sideload-APK |
| V2 | Galaxy-Watch-Integration, Abgleich mit Zielen, Auswertungen, lokale KI | lokal |
| V3 | Mehrere Personen, gemeinsame Reminder, KI über Backend | Backend nötig |

"Alignment" umfasst langfristig: **Erledigung erzwingen** (V1), **Abgleich mit
Zielen** (V2), **Abstimmung mehrerer Personen** (V3).

## 5. Mechanismen

Legende: **[V1]** = Kandidat für die erste Version, [später] = danach.
Die Einordnung ist vorläufig.

### 5.1 Auslösung (Trigger)
- T1 Zeit: einmalig, wiederkehrend, Zeitfenster ("zwischen 14 und 16 Uhr") **[V1]**
- T2 Ort: Betreten/Verlassen eines Geofence [später]
- T3 Kontext: WLAN, Bluetooth-Gerät (Auto, Kopfhörer), Ladegerät [später]
- T4 Kalender: vor/nach Terminen [später]
- T5 Aktivität: z. B. Gerät entsperrt, bestimmte App geöffnet [später]
- T6 **Vorab-Reminder** (Pre-Reminder): z. B. am Vorabend oder 1 h vorher ankündigen,
  damit man sich vorbereiten kann (Sporttasche packen o. Ä.) **[V1]**

### 5.2 Wahrnehmung sicherstellen (Audio / Visual)
- W1 Eskalationsstufen: Notification → Ton → lauter → Vollbild-Alarm → Dauerschleife **[V1]**
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören" **[V1]**
- W3 Overlay über anderen Apps [später]
- W4 Vibrationsmuster, Galaxy Watch [später, V2]
- W5 Sprachausgabe (TTS) mit Aufgabentext [später]

### 5.3 Erledigung nachweisen
- N1 Einfaches Quittieren, bewusst erschwert – nur für unwichtige Reminder
- N2 **Foto-Nachweis**, nur mit der App-Kamera aufgenommen (nicht aus der Galerie),
  mit Zeitstempel **[V1]**
- N4 **Ortsnachweis**: Standort beim Nachweis wird gespeichert und optional gegen
  einen erwarteten Ort geprüft (kombinierbar mit Foto) **[V1]**
- N7 **Audio-Nachweis**: kurze Sprachaufnahme "was habe ich erledigt" **[V1]**
- N3 QR-/NFC-Tag scannen [später]
- N5 Quiz/Rechenaufgabe gegen "im Halbschlaf wegdrücken" [später]
- N8 KI-Prüfung des Nachweises (Foto passt zur Aufgabe? Audio transkribieren und
  zusammenfassen) [später]

### 5.4 Ziel, Minimum und Rescheduling
- Z1 **Zielwert und Minimalziel** pro Reminder (z. B. "45 min Training" / "10 min
  Training"). Erledigung des Minimums zählt als Erfolg, aber unterscheidbar. **[V1]**
- Z2 **Gutes Rescheduling**: Verschieben ist eine bewusste Entscheidung mit neuem
  konkretem Zeitpunkt – kein endloses Snooze. **[V1]**
- N6 Snooze begrenzt / mit Begründung **[V1]**

### 5.5 Verstärkung (Psychologie)
**Positiv (Schwerpunkt):**
- P7 **Motivations-Audio vom eigenen Ich**: Bei der Erstellung des Reminders eine
  Botschaft aufnehmen, die zum Zeitpunkt der Ausführung abgespielt werden kann **[V1]**
- P3 Commitment: Begründung "Warum ist mir das wichtig?" beim Anlegen **[V1]**
- P1 Streaks, Fortschrittsanzeige, Statistik; Punkte/Level/variable Belohnungen [später]
- P4 Wenn-dann-Vorsätze ("Wenn X, dann tue ich Y") [später]
- P6 Reflexion: Wochenrückblick, Muster erkennen [später]

**Negativ (begrenzt, taktisch):**
- P2 Streak-Verlust sichtbar machen; Ablenkungs-Apps sperren [später, Regeln offen]
- P5 Dritte Person informieren [V3]

## 6. Zielplattform und Technik

### 6.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V2
- **Entscheidung:** minSdk = targetSdk = 37. Kein Rückwärts-Support nötig, da
  absehbar nur ein Nutzer. Moderne APIs und Energiespar-Mechanismen nutzen.

### 6.2 Technische Machbarkeit

Großer Vorteil: **Sideload statt Play Store** → die meisten Play-Richtlinien
(eingeschränkte Berechtigungen) gelten nicht. Berechtigungen müssen aber trotzdem
vom Nutzer in den Systemeinstellungen erteilt werden.

| Mechanismus | Machbar? | Android-Mittel | Anmerkung |
|---|---|---|---|
| T1 Zeit, exakt | ✅ | `AlarmManager.setAlarmClock()` | Umgeht Doze; exakte Alarme manuell erlauben |
| T2 Geofence | ✅ (mit Latenz) | Play Services Geofencing | Hintergrund-Standort nötig; Auslösung oft 1–5 min verzögert |
| T3 WLAN/BT/Laden | ✅ teilweise | Broadcasts, `ConnectivityManager` Callbacks | Im Hintergrund nur mit Foreground-Service zuverlässig |
| T4 Kalender | ✅ | `CalendarContract` | Leseberechtigung |
| T5 App geöffnet | ⚠️ | `UsageStatsManager` / Accessibility-Service | Funktioniert, aber akkuintensiv bzw. heikle Berechtigung |
| W1/W2 Vollbild, DND-Bypass | ✅ | Full-Screen-Intent, Alarm-Audiostream | Full-Screen-Intent manuell erlauben |
| W3 Overlay | ✅ | `SYSTEM_ALERT_WINDOW` | manuell erlauben |
| W4 Galaxy Watch | ✅ | Notifications spiegeln sich automatisch; eigene Wear-OS-App optional | V2 |
| N2 Foto | ✅ | CameraX (In-App-Aufnahme) | |
| N4 Ort | ✅ | Fused Location (Standort beim Nachweis) | Vordergrund-Standort reicht für Nachweis |
| N7 Audio | ✅ | `MediaRecorder` | Mikrofon-Berechtigung |
| N3 QR / NFC | ✅ | ML Kit Barcode, NFC-API | |
| N8 / KI lokal | ⚠️ zu prüfen | On-Device-Modelle (z. B. Gemini Nano / ML Kit GenAI, Speech-to-Text) | Verfügbarkeit auf S24 Ultra mit Android 17 prüfen |
| P2 Apps sperren | ✅ / ⚠️ | Accessibility-Service oder Overlay-Blocker | |
| Zuverlässigkeit | ⚠️ | Akku-Optimierung deaktivieren | **Samsung "Schlafende Apps"/Geräteschutz ist das größte Risiko** |

**Fazit:** Alle V1-Kandidaten sind lokal umsetzbar. Hauptrisiko ist Samsungs
Akkumanagement – früh am S24 Ultra testen (Spike, siehe Abschnitt 8).

### 6.3 Tech-Stack (Vorschlag)
- Kotlin, Jetpack Compose, Room (lokale DB), AlarmManager + WorkManager
- minSdk/targetSdk 37 (Android 17); AGP-Version mit API-37-Unterstützung nötig
- Build: Gradle (Wrapper), Entwicklung in Android Studio oder VS Code
- Entwicklungsumgebung: ✅ ohne Adminrechte möglich – siehe
  [entwicklungsumgebung.md](entwicklungsumgebung.md)

## 7. Entscheidungen

| Datum | Entscheidung |
|---|---|
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Galaxy Watch nicht in V1 |
| 2026-10-18 | KI später: teils lokal, teils über Backend |

## 8. Nächste Schritte

1. Anforderungen weiter schärfen (offene Fragen unten)
2. Technischer Spike auf dem S24 Ultra: exakter Alarm + Vollbild-Alarm + Eskalation,
   Überleben von Neustart und Samsung-Akkumanagement über mehrere Tage
3. V1-Umfang festlegen

## 9. Offene Fragen

**A – Reminder-Arten**
1. Welche 5–10 konkreten Reminder würdest du als Erstes eintragen?
2. Zwei Arten von Reminder: feste Termine ("Di 18 Uhr Training") und flexible Ziele
   ("3× pro Woche, wann es passt"). Brauchst du beide?

**B – Ziel und Minimum**
3. Wer entscheidet, dass heute nur das Minimum zählt: du selbst beim Auslösen,
   oder schlägt die App es vor (Schlaf, Kalender, mehrere verpasste Tage)?
4. Soll das Minimum begrenzt sein (z. B. max. 2× pro Woche), damit es nicht zur Regel wird?

**C – Rescheduling und Eskalation**
5. Wie oft darf man verschieben, bevor die App den Reminder als verpasst wertet
   oder nachfragt, ob er überhaupt realistisch ist?
6. Ruhezeiten: Wann darf nie eskaliert werden (Nacht, Arbeit, Termine)?
7. Was passiert, wenn du gar nicht reagierst? Wie weit soll die Eskalation gehen?

**D – Nachweis**
8. Ist der Nachweis pro Reminder wählbar (Foto, Ort, Audio, Kombination)?
9. Geht es um Schutz vor Selbstbetrug oder vor allem um bewusste Reibung und
   Dokumentation? (Das bestimmt, wie streng geprüft wird.)
10. Was passiert, wenn kein Nachweis möglich ist (kein Netz, Foto unpassend)?

**E – Motivation**
11. Was motiviert dich nachweislich? (Zahlen/Statistik, Serien, Belohnungen,
    Stimme, Fortschritt zu einem größeren Ziel)
12. Motivations-Audio: Immer abspielen, nur bei Zögern (z. B. nach Snooze),
    oder nur auf Knopfdruck?

**F – Überblick**
13. Pre-Reminder: Morgens Tagesübersicht, abends Vorschau auf morgen, oder pro Reminder?
14. Gibt es einen täglichen oder wöchentlichen Rückblick?

**G – Daten**
15. Fotos und Audio nur lokal? Backup/Export gewünscht? Wie lange aufbewahren?
