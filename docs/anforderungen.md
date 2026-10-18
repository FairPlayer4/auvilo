# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-10-18 – dritter Entwurf, wird iterativ verfeinert.

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
   (Ziel oder Minimum), verschoben, ausgelassen – oder wird als verpasst bzw.
   nicht erfolgreich markiert.
2. **Positiv vor negativ.** Negative Verstärkung nur begrenzt und taktisch, wenn
   sinnvoll (Regeln dafür sind noch zu erarbeiten).
3. **Lieber klein als gar nicht.** An schweren Tagen zählt das Minimalziel.
4. **Nachweis statt Klick.** Bestätigung braucht einen echten Beleg (Foto, Ort, Audio),
   wenn der Reminder es verlangt.
5. **Überblick vor Überraschung.** Vorab-Reminder und Übersichten, damit nichts
   unvorbereitet kommt.
6. **Der Nutzer entscheidet, die App macht es sichtbar.** Minimum, Verschieben und
   Nachweisart legt der Nutzer fest; die App erzwingt nichts heimlich, sondern
   zeigt ehrlich, was passiert ist.
7. **Erfolg wird über die Zeit sichtbar.** Statistik, Serien und Fortschritt sind
   der wichtigste positive Verstärker.

## 4. Ausbaustufen

| Stufe | Inhalt | Technik |
|---|---|---|
| **V1** | Einzelnutzer, komplett lokal, Kernmechanismen | Kein Backend, Sideload-APK |
| V2 | Galaxy-Watch-Integration, Abgleich mit Zielen, lokale KI, Backup | lokal |
| V3 | Mehrere Personen, gemeinsame Reminder, KI über Backend | Backend nötig |

"Alignment" umfasst langfristig: **Erledigung erzwingen** (V1), **Abgleich mit
Zielen** (V2), **Abstimmung mehrerer Personen** (V3).

## 5. Anwendungsfälle (erste Reminder)

| Reminder | Art | Charakter | Bemerkung |
|---|---|---|---|
| Training unter der Woche | fest + Ort: bei Ankunft zuhause, spätestens 19 Uhr | Überwindung | Ziel/Minimum, Training dokumentieren |
| Katzenbrunnen reinigen | wiederkehrend (Intervall) | Dokumentation | Foto als Nachweis naheliegend |
| Kontaktlinsen wechseln | wiederkehrend (Intervall) | Dokumentation | |
| Wartung diverser Dinge | wiederkehrend (lange Intervalle) | Dokumentation | |
| Arzttermine machen | flexibel, mit Frist | Überwindung | Aufgabe ohne festen Zeitpunkt |
| Kleine Alltagsdinge | Ort: "wenn ich zuhause bin" / nach der Arbeit | Erinnerung | Muss sehr schnell anlegbar sein |

**Zwei Charaktere von Remindern** (pro Reminder unterschiedlich):
- **Überwindung:** Dinge, die man nicht immer machen will → strenger Nachweis,
  Eskalation, Motivation.
- **Dokumentation:** Hauptsächlich Erinnerung, aber erledigt soll belegt sein →
  leichter Nachweis, Historie wichtig.

## 6. Mechanismen

Legende: **[V1]** = Kandidat für die erste Version, [später] = danach.
Die Einordnung ist vorläufig.

### 6.1 Auslösung (Trigger)
- T1 Zeit: einmalig, wiederkehrend, Zeitfenster ("zwischen 14 und 16 Uhr") **[V1]**
- T1b **Flexible Ziele**: "3× pro Woche", "bis Monatsende" – ohne festen Zeitpunkt **[V1]**
- T2 **Ort**: Betreten/Verlassen eines Geofence, z. B. "Ankunft zuhause".
  Verzögerung von einigen Minuten ist akzeptabel. **[V1]**
- T2b Kombination Ort + Frist: "bei Ankunft zuhause, spätestens 19 Uhr" **[V1]**
- T3 Kontext: WLAN, Bluetooth-Gerät (Auto, Kopfhörer), Ladegerät [später]
- T4 Kalender: vor/nach Terminen [später]
- T5 Aktivität: z. B. Gerät entsperrt, bestimmte App geöffnet [später]
- T6 **Vorab-Reminder** (Pre-Reminder): z. B. am Vorabend oder 1 h vorher ankündigen,
  damit man sich vorbereiten kann (Sporttasche packen o. Ä.) **[V1]**

### 6.2 Wahrnehmung sicherstellen (Audio / Visual)
- W1 Eskalationsstufen: Notification → Ton → lauter → Vollbild-Alarm **[V1]**
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören", Alarmton wie
  ein Wecker **[V1]**
- **Eskalationsfenster:** Eskalation (Ton/Alarm) nur zwischen **18 und 21 Uhr**
  (vorerst global). Außerhalb nur stille Notification.
- W3 Overlay über anderen Apps [später]
- W4 Vibrationsmuster, Galaxy Watch [später, V2]
- W5 Sprachausgabe (TTS) mit Aufgabentext [später]

### 6.3 Erledigung nachweisen
- **Nachweisart pro Reminder wählbar** (keiner, Foto, Ort, Audio, Kombination) –
  der Nutzer weiß, was passt. **[V1]**
- N1 Einfaches Quittieren – für reine Erinnerungen **[V1]**
- N2 **Foto-Nachweis**, nur mit der App-Kamera aufgenommen (nicht aus der Galerie),
  mit Zeitstempel **[V1]**
- N4 **Ortsnachweis**: Standort beim Nachweis wird gespeichert und optional gegen
  einen erwarteten Ort geprüft (kombinierbar mit Foto) **[V1]**
- N7 **Audio-Nachweis**: kurze Sprachaufnahme "was habe ich erledigt" **[V1]**
- **Fehlender Nachweis:** Wird ein geforderter Nachweis nicht erbracht, gilt der
  Reminder als **nicht erfolgreich** und wird so markiert. Kommt vor und ist in
  Maßen unkritisch – kein Drama, aber ehrlich in der Statistik. **[V1]**
- N3 QR-/NFC-Tag scannen [später]
- N5 Quiz/Rechenaufgabe gegen "im Halbschlaf wegdrücken" [später]
- N8 KI-Prüfung des Nachweises (Foto passt zur Aufgabe? Audio transkribieren und
  zusammenfassen) [später]

### 6.4 Ziel, Minimum und Rescheduling
- Z1 **Zielwert und Minimalziel** pro Reminder (z. B. "45 min Training" / "10 min
  Training"). Erledigung des Minimums zählt als Erfolg, aber unterscheidbar.
  **Der Nutzer entscheidet**, wann das Minimum gilt. **[V1]**
- Z1b **Limit für Minimum** pro Reminder einstellbar (z. B. max. 2× pro Woche) **[V1]**
- Z2 **Gutes Rescheduling**: Verschieben ist eine bewusste Entscheidung mit neuem
  konkretem Zeitpunkt – kein endloses Snooze. **[V1]**
- Z2b **Limit für Verschieben** pro Reminder einstellbar **[V1]**
- N6 Snooze begrenzt / mit Begründung **[V1]**

### 6.5 Verstärkung (Psychologie)
**Positiv (Schwerpunkt):**
- P1 **Statistik, Serien, Fortschritt** – Erfolg über die Zeit sichtbar machen,
  Zeiträume **Woche, Monat, Jahr, gesamt** ("was habe ich alles erledigt") **[V1]**
- P1b **Training dokumentieren** (Details siehe offene Fragen) **[V1]**
- P7 **Motivations-Audio vom eigenen Ich**: Bei der Erstellung eine Botschaft
  aufnehmen; abgespielt **nur auf Klick** **[V1]**
- P3 Commitment: Begründung "Warum ist mir das wichtig?" beim Anlegen **[V1]**
- P4 Wenn-dann-Vorsätze ("Wenn X, dann tue ich Y") [später]
- Belohnungen/Punkte/Level: eher nicht – Belohnungen sind in einer App schwierig.

**Negativ (begrenzt, taktisch):**
- Alarm wie ein Wecker im Eskalationsfenster **[V1]**
- P2 Ablenkungs-Apps sperren [später, Regeln offen]
- P5 Dritte Person informieren [V3]

### 6.6 Überblick
- O1 **Übersicht morgens** (was steht heute an) und **abends** (Rückblick auf heute,
  Vorschau auf morgen). Zeiten und Umfang einstellbar. **[V1]**
- O2 Rückblick ist Teil der Übersicht (Tag; Woche/Monat in der Statistik) **[V1]**

### 6.7 Lebenszyklus eines Reminders (Entwurf)

```
geplant ─► Vorab-Reminder ─► fällig ─► eskaliert
                              │
                              ├─► erledigt (Ziel)
                              ├─► erledigt (Minimum)
                              ├─► nicht erfolgreich (Nachweis fehlt)
                              ├─► verschoben (neuer Zeitpunkt, zählt gegen Limit)
                              ├─► ausgelassen (bewusst, mit Grund)
                              └─► verpasst (keine Reaktion bis Ende des Fensters)
```

## 7. Zielplattform und Technik

### 7.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V2
- **Entscheidung:** minSdk = targetSdk = 37. Kein Rückwärts-Support nötig, da
  absehbar nur ein Nutzer. Moderne APIs und Energiespar-Mechanismen nutzen.

### 7.2 Technische Machbarkeit

Großer Vorteil: **Sideload statt Play Store** → die meisten Play-Richtlinien
(eingeschränkte Berechtigungen) gelten nicht. Berechtigungen müssen aber trotzdem
vom Nutzer in den Systemeinstellungen erteilt werden.

| Mechanismus | Machbar? | Android-Mittel | Anmerkung |
|---|---|---|---|
| T1 Zeit, exakt | ✅ | `AlarmManager.setAlarmClock()` | Umgeht Doze; exakte Alarme manuell erlauben |
| T2 Geofence | ✅ (mit Latenz) | Play Services Geofencing | Hintergrund-Standort nötig; Auslösung oft 1–5 min verzögert – laut Nutzer akzeptabel |
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

**Fazit:** Alle V1-Kandidaten sind lokal umsetzbar. Hauptrisiken: Samsungs
Akkumanagement und die Zuverlässigkeit des Geofence "Ankunft zuhause" – beides
früh am S24 Ultra testen (Spike, siehe Abschnitt 9).

### 7.3 Tech-Stack (Vorschlag)
- Kotlin, Jetpack Compose, Room (lokale DB), AlarmManager + WorkManager
- minSdk/targetSdk 37 (Android 17); AGP-Version mit API-37-Unterstützung nötig
- Build: Gradle (Wrapper), Entwicklung in Android Studio oder VS Code
- Entwicklungsumgebung: ✅ ohne Adminrechte möglich – siehe
  [entwicklungsumgebung.md](entwicklungsumgebung.md)

### 7.4 Daten
- Alle Daten inkl. Fotos und Audio **nur lokal** auf dem Gerät.
- Kein Backup in V1 (später nachrüstbar, z. B. Export als ZIP).
- Aufbewahrung: vorerst unbegrenzt.

## 8. Entscheidungen

| Datum | Entscheidung |
|---|---|
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Galaxy Watch nicht in V1 |
| 2026-10-18 | KI später: teils lokal, teils über Backend |
| 2026-10-18 | Feste, flexible und ortsbasierte Reminder in V1 |
| 2026-10-18 | Minimum entscheidet der Nutzer; Limits für Minimum und Verschieben pro Reminder |
| 2026-10-18 | Eskalation (Alarm wie Wecker) nur 18–21 Uhr, vorerst global |
| 2026-10-18 | Nachweisart pro Reminder; fehlender Nachweis = "nicht erfolgreich" |
| 2026-10-18 | Motivation über Statistik, Serien, Fortschritt (Woche/Monat/Jahr/gesamt); keine Belohnungssysteme |
| 2026-10-18 | Motivations-Audio nur auf Klick |
| 2026-10-18 | Übersicht morgens und abends (einstellbar), inkl. Rückblick |
| 2026-10-18 | Daten nur lokal, kein Backup in V1 |

## 9. Nächste Schritte

1. Anforderungen weiter schärfen (offene Fragen unten)
2. Technischer Spike auf dem S24 Ultra: exakter Alarm + Vollbild-Alarm + Eskalation,
   Geofence "Ankunft zuhause", Überleben von Neustart und Samsung-Akkumanagement
   über mehrere Tage
3. V1-Umfang festlegen

## 10. Offene Fragen

**H – Zeit und Ort**
1. Eskalation nur 18–21 Uhr: Was ist mit Remindern außerhalb (z. B. Kontaktlinsen
   morgens, Wochenende)? Nur stille Notification, oder pro Reminder ein eigenes Fenster?
2. Training "bei Ankunft zuhause, spätestens 19 Uhr": Was soll passieren, wenn du um
   19 Uhr noch nicht zuhause bist (Termin, Überstunden)? Und an Homeoffice-Tagen,
   an denen es keine "Ankunft" gibt?
3. Wiederkehrende Dinge (Katzenbrunnen, Linsen, Wartung): Soll das nächste Mal ab
   dem **festen Rhythmus** zählen (jeden 1. des Monats) oder ab der **letzten
   Erledigung** (14 Tage nach dem letzten Reinigen)?

**I – Flexible Ziele**
4. "3× pro Woche": Wie soll die App erinnern? Z. B. abendlicher Stand ("noch 2 offen,
   3 Tage übrig"), Vorschläge für passende Tage, oder Eskalation erst, wenn es knapp wird?
5. Aufgaben mit Frist ("Arzttermin machen bis Monatsende"): Wann und wie oft soll
   die App vorher erinnern?

**J – Verhalten im Alarm**
6. Welche Knöpfe soll der Alarm haben? Vorschlag: *Jetzt erledigen*, *Minimum*,
   *Später heute (+x min)*, *Verschieben auf…*, *Auslassen (mit Grund)*.
   Fehlt etwas, ist etwas zu viel?
7. Ist "Auslassen" erlaubt (z. B. krank, Urlaub)? Zählt es in der Statistik
   neutral oder als nicht erfolgreich?

**K – Serien und Statistik**
8. Was hält eine Serie am Leben: Nur Ziel? Auch Minimum? Bricht ein fehlender
   Nachweis die Serie? Idee: "nie zweimal hintereinander verpassen" als Regel.
9. Training dokumentieren: Was willst du erfassen? (Art, Dauer, Intensität,
   Übungen/Gewichte, Freitext, Audio)

**L – Bedienung**
10. Kleine Alltagsreminder müssen schnell gehen: Reicht die App selbst, oder
    brauchst du Schnellwege (Widget, Quick-Settings-Kachel, Spracheingabe,
    "Teilen mit"-Menü)?
11. Soll es Vorlagen/Strenge-Stufen geben (z. B. *locker*, *normal*, *streng*), die
    Eskalation, Nachweis und Limits bündeln – statt alles einzeln einzustellen?
