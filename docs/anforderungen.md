# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-10-18 – vierter Entwurf, wird iterativ verfeinert.

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
- Viele Dinge lassen sich nur in **bestimmten Zeitfenstern** erledigen (z. B. wenn
  jemand erreichbar ist). Liegen diese in der Arbeitszeit, wird ständig verschoben.

## 3. Leitprinzipien

1. **Kein Reminder verschwindet still.** Jeder Reminder endet bewusst: erledigt
   (Ziel oder Minimum), verschoben, ausgelassen, entfallen – oder wird als verpasst
   bzw. nicht erfolgreich markiert.
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
8. **Erinnern, wenn es möglich ist.** Ein Reminder kennt seine Gelegenheiten
   (Zeitfenster, Ort) und meldet sich dann – nicht zu Zeiten, in denen man es
   ohnehin nicht erledigen kann.
9. **Der Rhythmus zählt, nicht der einzelne Tag.** Weiche Ziele (z. B. Training
   3–4× pro Woche) werden über den Zeitraum bewertet; ein entfallener Tag ist kein
   Misserfolg.

## 4. Ausbaustufen

| Stufe | Inhalt | Technik |
|---|---|---|
| **V1** | Einzelnutzer, komplett lokal, Kernmechanismen | Kein Backend, Sideload-APK |
| V2 | Galaxy-Watch-Integration, Abgleich mit Zielen, lokale KI, Zahlenwerte, Backup | lokal |
| V3 | Mehrere Personen, gemeinsame Reminder, KI über Backend | Backend nötig |

"Alignment" umfasst langfristig: **Erledigung erzwingen** (V1), **Abgleich mit
Zielen** (V2), **Abstimmung mehrerer Personen** (V3).

## 5. Anwendungsfälle (erste Reminder)

| Reminder | Art | Charakter | Bemerkung |
|---|---|---|---|
| Training unter der Woche | Tagesangebot bei Ankunft zuhause / 19 Uhr + **Wochenziel 3–4×** | Überwindung | Nie eine Woche ganz ohne Training; Freitext + Audio |
| Katzenbrunnen reinigen | wiederkehrend ab letzter Erledigung | Dokumentation | Foto als Nachweis naheliegend |
| Kontaktlinsen wechseln | wiederkehrend ab letzter Erledigung | Dokumentation | |
| Wartung diverser Dinge | wiederkehrend, teils kalendarisch | Dokumentation | |
| Arzttermine machen | Frist + Zeitfenster (Praxis erreichbar) | Überwindung | Häufig erinnern, verschiebbar auf nächsten Tag |
| Kleine Alltagsdinge | Ort: "wenn ich zuhause bin" | Erinnerung | Per Widget in einem Klick anlegen |

**Zwei Charaktere von Remindern** (pro Reminder unterschiedlich):
- **Überwindung:** Dinge, die man nicht immer machen will → strenger Nachweis,
  Eskalation, Motivation.
- **Dokumentation:** Hauptsächlich Erinnerung, aber erledigt soll belegt sein →
  leichter Nachweis, Historie wichtig.

### 5.1 Beispiel Training (Regeln im Detail)
- Montag–Freitag wird Training **angeboten**:
  - Ankunft zuhause vor 19 Uhr → Reminder bei Ankunft.
  - Schon zuhause (Homeoffice) → Reminder um 19 Uhr.
  - Um 19 Uhr nicht zuhause → Training **entfällt** für heute (neutral).
- Tatsächlich bewertet wird das **Wochenziel**: Ziel 3–4×, Minimum 1×.
  Eine Woche ganz ohne Training darf nicht passieren.

Die Regel "um 19 Uhr nicht zuhause → entfällt" macht eine Erkennung von
Homeoffice-Tagen überflüssig: Es zählt nur, ob man zum Zeitpunkt am Ort ist.

## 6. Reminder-Modell (Entwurf)

Alle Anwendungsfälle lassen sich mit denselben Bausteinen beschreiben:

| Baustein | Frage | Beispiele |
|---|---|---|
| **Aufgabe** | Was? | Titel, "Warum ist mir das wichtig?", Motivations-Audio |
| **Gelegenheit** | Wann/wo ist es möglich? | Zeitfenster pro Wochentag (Praxis Mo–Fr 8–12), Ort (zuhause), Kombination |
| **Takt** | Wie oft / bis wann? | einmalig, Frist, fester Kalender-Rhythmus, Intervall ab letzter Erledigung, x-mal pro Zeitraum |
| **Ziel** | Wie viel? | Zielwert, Minimum, Limit für Minimum |
| **Nachweis** | Womit belegt? | keiner, Quittieren, Foto, Ort, Audio, Kombination |
| **Strenge** | Wie hartnäckig? | Vorlage *locker / normal / streng*: Eskalation, Erinnerungsintervall, Limits für Verschieben |

## 7. Mechanismen

Legende: **[V1]** = Kandidat für die erste Version, [später] = danach.
Die Einordnung ist vorläufig.

### 7.1 Auslösung (Trigger)
- T1 Zeit: einmalig, wiederkehrend, Zeitfenster ("zwischen 14 und 16 Uhr") **[V1]**
- T1b **Flexible Ziele**: "3× pro Woche" – ohne festen Zeitpunkt **[V1]**
- T1c **Intervall ab letzter Erledigung** als Standard für Wiederkehrendes;
  alternativ fester Kalender-Rhythmus **[V1]**
- T1d **Frist mit Ausführungsfenstern**: Aufgabe bis Datum X, erinnert nur in den
  Zeiten, in denen sie erledigt werden kann; Erinnerungsintervall pro Reminder
  (z. B. stündlich) **[V1]**
- T2 **Ort**: Betreten/Verlassen eines Geofence, z. B. "Ankunft zuhause".
  Verzögerung von einigen Minuten ist akzeptabel. **[V1]**
- T2b Kombination Ort + Uhrzeit: "bei Ankunft, sonst um 19 Uhr falls schon dort,
  sonst entfällt" **[V1]**
- T3 Kontext: WLAN, Bluetooth-Gerät (Auto, Kopfhörer), Ladegerät [später]
- T4 Kalender: vor/nach Terminen [später]
- T5 Aktivität: z. B. Gerät entsperrt, bestimmte App geöffnet [später]
- T6 **Vorab-Reminder** (Pre-Reminder): z. B. am Vorabend oder 1 h vorher ankündigen,
  damit man sich vorbereiten kann (Sporttasche packen o. Ä.) **[V1]**

### 7.2 Flexible Ziele und Fristen – wie erinnert wird
- **Stand am Abend**: "Training: 1 von 3, noch 3 Tage" **[V1]**
- **Vorschläge für passende Tage/Zeiten** (aus Gelegenheiten und freien Tagen) **[V1]**
- **Eskalation, wenn es knapp wird** (Ziel nur noch mit allen restlichen Tagen erreichbar) **[V1]**
- Bei Fristen: häufig erinnern (Intervall pro Reminder), Option **"auf morgen
  verschieben"**, solange die Frist weit genug weg ist **[V1]**

### 7.3 Wahrnehmung sicherstellen (Audio / Visual)
- **Eskalation** = laut oder wirklich blockierend: Vollbild-Reminder oder wiederholte
  Benachrichtigung, bis der Nutzer reagiert. **[V1]**
- **Eskalationsfenster: 7–21 Uhr** (vorerst global). Gilt ausdrücklich auch während
  der Arbeitszeit – dort gesetzte Reminder (beruflich wie privat) brauchen Aufmerksamkeit.
  Außerhalb des Fensters nur stille Benachrichtigung.
- W1 Eskalationsstufen: Notification → wiederholte Notification/Ton → Vollbild-Alarm **[V1]**
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören", Alarmton wie
  ein Wecker **[V1]**
- W3 Overlay über anderen Apps [später]
- W4 Vibrationsmuster, Galaxy Watch [später, V2]
- W5 Sprachausgabe (TTS) mit Aufgabentext [später]

### 7.4 Aktionen im Reminder/Alarm
Erster Satz, wird mit der Nutzung angepasst: **[V1]**
- *Jetzt erledigen* (→ Nachweis)
- *Minimum* (→ Nachweis)
- *Später heute (+x min)*
- *Verschieben auf …* (zählt gegen das Limit; "morgen" als Schnellwahl)
- *Auslassen (mit Grund)* – zählt **neutral**

### 7.5 Erledigung nachweisen
- **Nachweisart pro Reminder wählbar** (keiner, Foto, Ort, Audio, Kombination) –
  der Nutzer weiß, was passt. **[V1]**
- N1 Einfaches Quittieren – für reine Erinnerungen **[V1]**
- N2 **Foto-Nachweis**, nur mit der App-Kamera aufgenommen (nicht aus der Galerie),
  mit Zeitstempel **[V1]**
- N4 **Ortsnachweis**: Standort beim Nachweis wird gespeichert und optional gegen
  einen erwarteten Ort geprüft (kombinierbar mit Foto) **[V1]**
- N7 **Audio-Nachweis**: kurze Sprachaufnahme "was habe ich erledigt" **[V1]**
- **Dokumentation** zur Erledigung: Freitext + Audio (z. B. Training) **[V1]**
- **Fehlender Nachweis:** Wird ein geforderter Nachweis nicht erbracht, gilt der
  Reminder als **nicht erfolgreich** und wird so markiert. Kommt vor und ist in
  Maßen unkritisch – kein Drama, aber ehrlich in der Statistik. **[V1]**
- N3 QR-/NFC-Tag scannen [später]
- N5 Quiz/Rechenaufgabe gegen "im Halbschlaf wegdrücken" [später]
- N8 KI-Prüfung des Nachweises (Foto passt zur Aufgabe? Audio transkribieren und
  zusammenfassen) [später]
- N9 **Zahlenwerte** pro Erledigung (Dauer, Gewicht, Distanz …) als Statistik über
  die Zeit; evtl. per lokaler KI aus grob formatiertem Freitext extrahiert [später]

### 7.6 Ziel, Minimum und Rescheduling
- Z1 **Zielwert und Minimalziel** pro Reminder (z. B. "45 min Training" / "10 min
  Training"). Erledigung des Minimums zählt als Erfolg, aber unterscheidbar.
  **Der Nutzer entscheidet**, wann das Minimum gilt. **[V1]**
- Z1b **Limit für Minimum** pro Reminder einstellbar (z. B. max. 2× pro Woche) **[V1]**
- Z1c **Ziel über Zeitraum**: z. B. Wochenziel 3–4×, Minimum 1× pro Woche **[V1]**
- Z2 **Gutes Rescheduling**: Verschieben ist eine bewusste Entscheidung mit neuem
  konkretem Zeitpunkt – kein endloses Snooze. **[V1]**
- Z2b **Limit für Verschieben** pro Reminder einstellbar **[V1]**

### 7.7 Verstärkung (Psychologie)
**Positiv (Schwerpunkt):**
- P1 **Statistik, Serien, Fortschritt** – Erfolg über die Zeit sichtbar machen,
  Zeiträume **Woche, Monat, Jahr, gesamt** ("was habe ich alles erledigt") **[V1]**
- P1b **Serienregeln:** Serie bleibt bei Minimum erhalten; **ein Aussetzer pro
  Woche/Monat** ist erlaubt ("Joker"). Auslassen zählt neutral. Später einstellbar. **[V1]**
- P7 **Motivations-Audio vom eigenen Ich**: Bei der Erstellung eine Botschaft
  aufnehmen; abgespielt **nur auf Klick** **[V1]**
- P3 Commitment: Begründung "Warum ist mir das wichtig?" beim Anlegen **[V1]**
- P4 Wenn-dann-Vorsätze ("Wenn X, dann tue ich Y") [später]
- Belohnungen/Punkte/Level: eher nicht – Belohnungen sind in einer App schwierig.

**Negativ (begrenzt, taktisch):**
- Eskalation mit Vollbild/Alarm im Eskalationsfenster **[V1]**
- P2 Ablenkungs-Apps sperren [später, Regeln offen]
- P5 Dritte Person informieren [V3]

### 7.8 Überblick
- O1 **Übersicht morgens** (was steht heute an) und **abends** (Rückblick auf heute,
  Stand der Wochenziele, Vorschau auf morgen). Zeiten und Umfang einstellbar. **[V1]**
- O2 Rückblick ist Teil der Übersicht (Tag; Woche/Monat in der Statistik) **[V1]**

### 7.9 Bedienung
- **Widget**: ein Klick → direkt in die Reminder-Erstellung **[V1]**
- **Vorlagen / Strenge-Stufen** (*locker / normal / streng*), die Eskalation,
  Nachweis und Limits bündeln; Details brauchen noch Refinement **[V1]**
- UI wird sich mit der tatsächlichen Nutzung stark anpassen → früh benutzbare
  Version wichtiger als vollständige Planung.

### 7.10 Lebenszyklus eines Reminders (Entwurf)

```
geplant ─► Vorab-Reminder ─► fällig ─► eskaliert
   │                          │
   │                          ├─► erledigt (Ziel)
   │                          ├─► erledigt (Minimum)
   │                          ├─► nicht erfolgreich (Nachweis fehlt)
   │                          ├─► verschoben (neuer Zeitpunkt, zählt gegen Limit)
   │                          ├─► ausgelassen (bewusst, mit Grund – neutral)
   │                          └─► verpasst (keine Reaktion bis Ende der Gelegenheit)
   └─► entfallen (Gelegenheit trat nicht ein, z. B. um 19 Uhr nicht zuhause – neutral)
```

## 8. Zielplattform und Technik

### 8.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V2
- **Entscheidung:** minSdk = targetSdk = 37. Kein Rückwärts-Support nötig, da
  absehbar nur ein Nutzer. Moderne APIs und Energiespar-Mechanismen nutzen.

### 8.2 Technische Machbarkeit

Großer Vorteil: **Sideload statt Play Store** → die meisten Play-Richtlinien
(eingeschränkte Berechtigungen) gelten nicht. Berechtigungen müssen aber trotzdem
vom Nutzer in den Systemeinstellungen erteilt werden.

| Mechanismus | Machbar? | Android-Mittel | Anmerkung |
|---|---|---|---|
| T1 Zeit, exakt | ✅ | `AlarmManager.setAlarmClock()` | Umgeht Doze; exakte Alarme manuell erlauben |
| T2 Geofence | ✅ (mit Latenz) | Play Services Geofencing | Hintergrund-Standort nötig; Auslösung oft 1–5 min verzögert – laut Nutzer akzeptabel |
| T2b "um 19 Uhr am Ort?" | ✅ | Alarm + aktueller Geofence-Status / Standortabfrage | |
| T3 WLAN/BT/Laden | ✅ teilweise | Broadcasts, `ConnectivityManager` Callbacks | Im Hintergrund nur mit Foreground-Service zuverlässig |
| T4 Kalender | ✅ | `CalendarContract` | Leseberechtigung |
| T5 App geöffnet | ⚠️ | `UsageStatsManager` / Accessibility-Service | Funktioniert, aber akkuintensiv bzw. heikle Berechtigung |
| W1/W2 Vollbild, DND-Bypass | ✅ | Full-Screen-Intent, Alarm-Audiostream | Full-Screen-Intent manuell erlauben |
| W3 Overlay | ✅ | `SYSTEM_ALERT_WINDOW` | manuell erlauben |
| W4 Galaxy Watch | ✅ | Notifications spiegeln sich automatisch; eigene Wear-OS-App optional | V2 |
| Widget | ✅ | Jetpack Glance | |
| N2 Foto | ✅ | CameraX (In-App-Aufnahme) | |
| N4 Ort | ✅ | Fused Location (Standort beim Nachweis) | Vordergrund-Standort reicht für Nachweis |
| N7 Audio | ✅ | `MediaRecorder` | Mikrofon-Berechtigung |
| N3 QR / NFC | ✅ | ML Kit Barcode, NFC-API | |
| N8 / KI lokal | ⚠️ zu prüfen | On-Device-Modelle (z. B. Gemini Nano / ML Kit GenAI, Speech-to-Text) | Verfügbarkeit auf S24 Ultra mit Android 17 prüfen |
| P2 Apps sperren | ✅ / ⚠️ | Accessibility-Service oder Overlay-Blocker | |
| Zuverlässigkeit | ⚠️ | Akku-Optimierung deaktivieren | **Samsung "Schlafende Apps"/Geräteschutz ist das größte Risiko** |

**Fazit:** Alle V1-Kandidaten sind lokal umsetzbar. Hauptrisiken: Samsungs
Akkumanagement und die Zuverlässigkeit des Geofence "Ankunft zuhause" – beides
früh am S24 Ultra testen (Spike, siehe Abschnitt 10).

### 8.3 Tech-Stack (Vorschlag)
- Kotlin, Jetpack Compose, Room (lokale DB), AlarmManager + WorkManager, Glance (Widget)
- minSdk/targetSdk 37 (Android 17); AGP-Version mit API-37-Unterstützung nötig
- Build: Gradle (Wrapper), Entwicklung in Android Studio oder VS Code
- Entwicklungsumgebung: ✅ ohne Adminrechte möglich – siehe
  [entwicklungsumgebung.md](entwicklungsumgebung.md)

### 8.4 Daten
- Alle Daten inkl. Fotos und Audio **nur lokal** auf dem Gerät.
- Kein Backup in V1 (später nachrüstbar, z. B. Export als ZIP).
- Aufbewahrung: vorerst unbegrenzt.

## 9. Entscheidungen

| Datum | Entscheidung |
|---|---|
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Galaxy Watch nicht in V1 |
| 2026-10-18 | KI später: teils lokal, teils über Backend |
| 2026-10-18 | Feste, flexible und ortsbasierte Reminder in V1 |
| 2026-10-18 | Minimum entscheidet der Nutzer; Limits für Minimum und Verschieben pro Reminder |
| 2026-10-18 | Nachweisart pro Reminder; fehlender Nachweis = "nicht erfolgreich" |
| 2026-10-18 | Motivation über Statistik, Serien, Fortschritt (Woche/Monat/Jahr/gesamt); keine Belohnungssysteme |
| 2026-10-18 | Motivations-Audio nur auf Klick |
| 2026-10-18 | Übersicht morgens und abends (einstellbar), inkl. Rückblick |
| 2026-10-18 | Daten nur lokal, kein Backup in V1 |
| 2026-10-18 | Eskalation = Vollbild oder wiederholte Benachrichtigung bis Reaktion; Fenster **7–21 Uhr** (ersetzt 18–21 Uhr) |
| 2026-10-18 | Training: Tagesangebot (Ankunft / 19 Uhr / sonst entfällt) + Wochenziel 3–4×, nie eine Woche ohne |
| 2026-10-18 | Wiederkehrendes standardmäßig ab letzter Erledigung, kalendarisch optional |
| 2026-10-18 | Reminder kennen Ausführungsfenster; Fristen erinnern häufig, "auf morgen" möglich |
| 2026-10-18 | Auslassen zählt neutral; Serie hält bei Minimum; ein Aussetzer pro Woche/Monat erlaubt |
| 2026-10-18 | Dokumentation per Freitext + Audio; Zahlenwerte später |
| 2026-10-18 | Widget für Ein-Klick-Erstellung; Strenge-Vorlagen in V1 (Details offen) |

## 10. Nächste Schritte

1. Restliche offene Fragen klären (unten)
2. Technischer Spike auf dem S24 Ultra: exakter Alarm + Vollbild-Alarm + Eskalation,
   Geofence "Ankunft zuhause", Überleben von Neustart und Samsung-Akkumanagement
   über mehrere Tage
3. V1-Umfang festlegen – Vorschlag: in Etappen, damit früh echte Nutzung möglich ist
   (siehe Frage 6)

## 11. Offene Fragen

**M – Eskalation im Alltag**
1. Eskalation 7–21 Uhr auch in der Arbeitszeit: Was ist mit Besprechungen? Soll die
   App einen Kalender (privat oder dienstlich auf dem Handy?) lesen und in Terminen
   nur vibrieren – oder reicht ein schnelles "nach dem Termin" (+30/60 min)?
2. Fristen stündlich erinnern: Jede Erinnerung eskalierend (Vollbild), oder still
   und erst zum Ende des Ausführungsfensters / kurz vor der Frist laut?

**N – Ziele über Zeitraum**
3. Wochenziel Training: Woche = Montag–Sonntag? Soll am Wochenende eine
   "Rettungs-Erinnerung" kommen, wenn die Woche sonst auf 0 bliebe?
4. Joker: Vorschlag **1 Aussetzer pro Woche bei täglichen Remindern, 1 pro Monat
   bei wöchentlichen** – passt das als Startregel?

**O – Vorlagen**
5. Erster Wurf für die Strenge-Stufen:
   - *locker*: stille Benachrichtigung, Quittieren, Verschieben unbegrenzt
   - *normal*: wiederholte Benachrichtigung, Nachweis wählbar, 3× Verschieben
   - *streng*: Vollbild-Alarm, Nachweis Pflicht, 1× Verschieben, Minimum max. 1×/Woche
   Was würdest du ändern?

**P – Vorgehen**
6. Die Anforderungen für V1 sind schon umfangreich. Vorschlag für Etappen:
   - **V1.0 (benutzbar):** Zeit-Reminder, Eskalation, Aktionen, Quittieren/Foto,
     Ziel/Minimum, einfache Liste der Erledigungen, Widget
   - **V1.1:** Ort (Ankunft zuhause), Intervall ab letzter Erledigung, Audio-Nachweis,
     Motivations-Audio
   - **V1.2:** Wochenziele, Fristen mit Ausführungsfenstern, Serien/Joker,
     Statistik, Übersicht morgens/abends, Vorlagen
   Passt die Reihenfolge, oder ist dir etwas für den Start wichtiger?
7. Für den Spike brauche ich das S24 Ultra per Wireless Debugging am PC (Handy und PC
   im selben WLAN). Ist das bei dir möglich, und erlaubt das Firmennetz es?
