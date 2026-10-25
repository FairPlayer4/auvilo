# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-10-25 – achter Entwurf (Watch-App in V1.4, Raumerkennung und Taster über Home Assistant, NFC nach V3), wird iterativ verfeinert.

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
10. **Nicht ans Handy gebunden.** Das Handy liegt oft in Reichweite, wird aber nicht
    immer getragen – die Uhr schon. Laufende Reminder müssen sich ab V1.4 auch ohne
    Handy bedienen lassen (Uhr, später Taster im Haus).

## 4. Ausbaustufen

| Stufe | Inhalt | Technik |
|---|---|---|
| **V1** | Einzelnutzer, komplett lokal, Kernmechanismen | Kein Backend, Sideload-APK |
| V2 | Abgleich mit Zielen, lokale KI, Zahlenwerte, Backup, **Forschung Bewegungsdaten** (7.16), **Räume und Taster über Home Assistant** (7.19), ggf. GPS-Routen (7.17) | lokal bzw. im Heimnetz |
| V3 | Mehrere Personen im Haushalt, gemeinsame Reminder (7.18), KI über Backend, ggf. NFC-Kontrollpunkte (7.15) | Lokale Synchronisation zwischen den Handys – Backend nur, wenn nötig |

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
| **Nachweis** | Womit belegt? | keiner, Quittieren, Foto, Ort, Audio, Kombination; später Raum oder Taster (Home Assistant) |
| **Strenge** | Wie hartnäckig? | Vorlage *locker / normal / streng*: Eskalation, Erinnerungsintervall, Limits für Verschieben |
| **Teile** | Woraus besteht es? | eine Aufgabe, oder Checkliste mit einzeln verschiebbaren Punkten (Brunnen 1, Brunnen 2, Futterautomat) |
| **Durchführung** | Wie läuft es ab? | einfach erledigen, oder *Ich starte jetzt* mit Timer und Nachfrage-Muster, oder Ablauf mit Schritten (Training: Sätze, Pausen) |

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
- **Eskalation** = auffällig oder wirklich blockierend: Vollbild-Reminder oder
  wiederholte Benachrichtigung, bis der Nutzer reagiert. **[V1]**
- **Zwei Zeitfenster** (vorerst global):
  - **7–21 Uhr – sichtbar:** Vollbild (damit man es sieht, wenn das Handy lautlos
    auf dem Tisch liegt) + Signal je nach Einstellung, meist **Vibration**. Gilt
    ausdrücklich auch während der Arbeitszeit.
  - **18–21 Uhr – laut:** zusätzlich richtig laut (Alarmton wie ein Wecker), wenn
    der Reminder es erlaubt.
  - Außerhalb 7–21 Uhr nur stille Benachrichtigung.
- **Eigene Vibrationsmuster**: länger und ungewöhnlich, damit man sie von normalen
  Benachrichtigungen unterscheidet **[V1]**
- Besprechungen: kein Kalenderabgleich; Schnellknopf *nach dem Termin* (+30/60 min) reicht.
- Häufige Erinnerungen (z. B. stündlich bei Fristen): nicht laut, sondern Vibration
  nach Einstellung.
- Rettungs-Erinnerung: Droht ein Wochenziel bei 0 zu bleiben, erinnert die App am
  Wochenende gezielt. Wochen laufen Montag–Sonntag. **[V1]**
- W1 Eskalationsstufen: Notification → wiederholte Notification/Ton → Vollbild-Alarm **[V1]**
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören", Alarmton wie
  ein Wecker **[V1]**
- W3 Overlay über anderen Apps [später]
- W4 Vibrationsmuster, Galaxy Watch **[V1.3 / V1.4]** (siehe 7.14)
- W5 Sprachausgabe (TTS) mit Aufgabentext [später]

### 7.4 Aktionen im Reminder/Alarm
Erster Satz, wird mit der Nutzung angepasst: **[V1]**
- *Jetzt erledigen* (→ Nachweis)
- *Minimum* (→ Nachweis)
- *Später heute (+x min)* / *nach dem Termin* (+30/60 min)
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
- N3 NFC-Kontrollpunkte (7.15) [V3]; QR-Codes [später]
- N10 **Raum** oder **Taster** über Home Assistant als Nachweis – siehe 7.19 [V2]
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
  Woche/Monat** ist erlaubt ("Joker"): 1 pro Woche bei täglichen, 1 pro Monat bei
  wöchentlichen Remindern. Auslassen zählt neutral. Später einstellbar. **[V1]**
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
- **Vorlagen / Strenge-Stufen**, die Eskalation, Nachweis und Limits bündeln
  (Startwerte, Refinement mit der Nutzung): **[V1]**
  - *locker*: Banner + Vibration, alle 10 min wiederholt, Quittieren, Verschieben unbegrenzt
  - *normal*: Vollbild-Alarm wie ein Wecker, aber ohne Ton; Nachweis wählbar, 3× Verschieben
  - *streng*: Vollbild-Alarm, Nachweis Pflicht, 1× Verschieben, Minimum max. 1×/Woche
- UI wird sich mit der tatsächlichen Nutzung stark anpassen → früh benutzbare
  Version wichtiger als vollständige Planung.

### 7.10 Lebenszyklus eines Reminders (Entwurf)

```
geplant ─► Vorab-Reminder ─► fällig ─► eskaliert
   │                          │
   │                          ├─► läuft (Ich starte jetzt, Timer/Ablauf, Nachfragen – siehe 7.11)
   │                          │     └─► endet wie unten: Ziel / Minimum / abgebrochen (nicht erfolgreich)
   │                          ├─► teilweise erledigt (Checkliste: Restpunkte auf später – siehe 7.13)
   │                          ├─► erledigt (Ziel)
   │                          ├─► erledigt (Minimum)
   │                          ├─► nicht erfolgreich (Nachweis fehlt)
   │                          ├─► verschoben (neuer Zeitpunkt, zählt gegen Limit)
   │                          ├─► ausgelassen (bewusst, mit Grund – neutral)
   │                          └─► verpasst (keine Reaktion bis Ende der Gelegenheit)
   └─► entfallen (Gelegenheit trat nicht ein, z. B. um 19 Uhr nicht zuhause – neutral)
```

### 7.11 Durchführung mit Timer – "Ich starte jetzt"
Für Reminder, bei denen das Tun selbst Zeit braucht (Training, Aufräumen, Lernen).
Pro Reminder einschaltbar. **[V1.3]**
- D1 Aktion **Ich starte jetzt** im Alarm, in der Benachrichtigung und in der App.
  Starten zählt als Reaktion: die Eskalation endet, eine **Sitzung** beginnt.
- D2 **Timer** läuft sichtbar mit (laufende Benachrichtigung, Sperrbildschirm, Uhr).
  Die Dauer wird automatisch dokumentiert.
- D3 **Nachfragen** nach dem Start sind **zeitbasiert**: Intervall pro Reminder
  einstellbar (Standard **alle 10 min**), ebenso die **Strenge der Nachfragen**
  (unabhängig von der Strenge des Reminders). Zweck: Kein Reminder bleibt
  versehentlich offen. Die App fragt:
  - *Noch dran* → weiter bis zur nächsten Nachfrage
  - *Fertig* → **Ziel** erreicht (→ Nachweis, falls gefordert)
  - *Teilschritt erreicht, beenden* → **Minimum** erreicht (zählt gegen das Limit für Minimum)
  - *Abbruch ohne Minimalziel* → **nicht erfolgreich (abgebrochen)**, ehrlich in der Statistik
- D4 Ziel und Minimum können als **Dauer** angegeben werden ("45 min" / "10 min");
  die App schlägt dann die passende Antwort vor.
- D5 **Maximale Dauer** pro Reminder einstellbar. Ist der Reminder bis dahin nicht
  abgeschlossen, kommt ein **letzter Reminder, so streng wie zu der Uhrzeit möglich**
  (Vollbild, im lauten Fenster mit Alarmton) mit **einigen Minuten Reaktionszeit**
  (Standard 5 min). Danach gilt er als **nicht erfüllt** – außer es wurden schon
  Teilschritte abgeschlossen, dann zählt das **Minimum**.
- D6 **Schnell und effizient fertig werden soll sich lohnen:** Die Dauer jeder
  Sitzung wird festgehalten und im Verlauf gezeigt, mit Vergleich zu früheren
  Sitzungen (z. B. "6 min schneller als im Schnitt"). Positive Verstärkung, kein Druck.
- Technik: Foreground-Service mit laufender Benachrichtigung (wie der Alarm),
  Nachfragen als exakte Alarme – überlebt Bildschirm aus und App im Hintergrund.

### 7.12 Abläufe – gut geregeltes Training
Keine Trainings-App, aber der **Mechanismus für ein gut geregeltes Training** gehört
dazu, weil die App genau auf einen Nutzer zugeschnitten ist. Begrenzt auf das
Wichtigste. **[V1.3]**
- A1 Ein **Ablauf** ist eine Folge von Schritten, die die Sitzung aus 7.11 führt.
  Das einfache Nachfrage-Muster aus D3 ist der Sonderfall "ein Schritt".
- A2 Schrittarten:
  - *zeitgesteuert* – läuft ab, dann Signal (z. B. Aufwärmen 5 min)
  - *offen* – bis man *Fertig* tippt (z. B. ein Satz)
  - *Pause* – Countdown mit Signal am Ende (z. B. 90 s)
- A3 **Wiederholungen**: Schrittgruppen mehrfach ("3 Sätze à Satz + Pause").
- A4 **Eigene Signale** pro Schrittart (Vibrationsmuster für "Pause vorbei",
  "nächster Satz"), auf dem Handy und der Uhr; ohne aufs Display schauen zu müssen.
- A5 **Fortschritt** sichtbar: Schritt x von y, Satz 2 von 3, Restzeit.
  Abbruch speichert den erreichten Stand.
- A6 Minimum kann über den Ablauf definiert sein (z. B. "Aufwärmen + 1 Satz").
- A7 Abläufe sind **Vorlagen**, mehreren Remindern zuordenbar und vor dem Start
  anpassbar (Schritt überspringen, Pause verlängern).
- Nicht enthalten: Übungsdatenbank, Trainingspläne, Gewichte/Wiederholungen als
  Logbuch (Zahlenwerte bleiben bei N9, V2).

### 7.13 Checklisten
Für Reminder, die aus mehreren Teilen bestehen, die man nicht alle am selben Tag
schafft oder schaffen will. **[V1.1]**
- C1 Ein Reminder kann eine **Checkliste** haben (z. B. Katzenbrunnen 1,
  Katzenbrunnen 2, Futterautomat).
- C2 Punkte **einzeln abhaken**, auch direkt aus der Benachrichtigung.
- C3 Offene Punkte **auf morgen** (oder ein Datum) **schieben**; der Reminder kommt
  dann nur mit den Restpunkten wieder. Beispiel: heute Brunnen 1, morgen Brunnen 2,
  übermorgen Futterautomat.
- C4 Die Verteilung kann man auch **gleich beim Start planen** ("heute A, morgen B,
  übermorgen C").
- C5 Der Reminder ist **erledigt (Ziel)**, wenn alle Punkte erledigt sind. Ein
  Minimum (z. B. "mindestens 1 Punkt heute") ist möglich.
- C5b Die Checkliste ist ein **Block**: Sie hilft, nichts zu vergessen, und erlaubt
  bei wenig Motivation, nur Teile zu erledigen – der Rest kommt wieder. Bei
  *nach Erledigung* läuft das Intervall für den **ganzen Block ab dem ersten
  erledigten Punkt**; die nächste Runde bringt wieder alle Punkte.
  (Wenn sich zeigt, dass ein Block realistisch nie an einem Tag klappt, lieber in
  einzelne Reminder aufteilen.)
- C5c Restpunkte auf morgen schieben zählt **nicht** gegen das Verschiebe-Limit,
  wenn am selben Tag mindestens ein Punkt erledigt wurde; sonst schon.
- C6 Nachweis wahlweise **pro Punkt** (Foto je Brunnen) oder einmal für alles.
- C7 Verlauf zeigt pro Punkt, wann er erledigt wurde.

### 7.14 Galaxy Watch
Guter Hebel für neue Anforderungen; zuerst so viel wie möglich über
**Benachrichtigungen**, eigene Watch-App nur wenn nötig. **[V1.3]**
- G1 Erinnerungen, Eskalation und Vibration kommen **auf der Uhr** an.
  ✅ Schon mit V1.0 beobachtet: Benachrichtigung samt *Erledigt* und *+10 min*
  erscheint auf der Uhr.
- G2 **Aktionen auf der Uhr**: Erledigt, +10 min, Ich starte jetzt, Checklisten-Punkt
  abhaken, Antworten auf Nachfragen (Noch dran / Fertig / Teilschritt / Abbruch).
- G3 **Laufende Sitzung** (Timer, aktueller Schritt, Pausen-Countdown) auf der Uhr
  sichtbar, Signale am Handgelenk – wichtig fürs Training (Handy liegt weg).
- G4 Foto-Nachweis bleibt auf dem Handy; die Uhr verweist darauf.
- **Eigene Watch-App [V1.4]:** Die Uhr ist der beste Hebel für laufende Reminder,
  besonders wenn das Handy weiter weg liegt (Training). Ziel: eine Sitzung komplett
  von der Uhr aus bedienen können.
  - G5 Laufende Sitzung als eigene Ansicht: Timer, aktueller Schritt, Pausen-Countdown,
    große Knöpfe für *Fertig* / *Nächster Schritt* / *Pause verlängern*.
  - G6 Nachfragen (7.11) und Signale (A4) direkt auf der Uhr, ohne dass das Handy
    sie erst spiegeln muss.
  - G7 *Ich starte jetzt* und Checklisten-Punkte von der Uhr aus.
  - G8 Kachel (Tile) mit dem nächsten fälligen Reminder bzw. der laufenden Sitzung.
  - G9 Fällt die Verbindung zum Handy kurz aus, läuft die Sitzung auf der Uhr weiter
    und gleicht danach ab.
  - Grundlage für die Sensor-Aufzeichnung der Forschung (7.16).

### 7.15 NFC-Kontrollpunkte
Nachweis für Abläufe **im Haus**, wo GPS zu ungenau ist. NFC-Sticker an den Orten,
an denen etwas passieren muss. **[V3]** – zurückgestellt, weil zu unflexibel (man
muss genau hin und das Handy dabeihaben; Metall und Nässe brauchen Spezial-Tags).
Raumerkennung und Taster (7.19) sind vorerst der bessere Weg.
- Hinweise für später: NTAG213 (NFC Forum Type 2) reicht; an Metall *On-Metal*-Tags,
  an Spüle/Brunnen wasserfeste Tags; kein MIFARE Classic. Ob die Galaxy Watch Tags
  lesen darf, ist offen (eher nicht).
- K1 **Kontrollpunkte anlernen**: Sticker in der App scannen und benennen
  ("Brunnen Wohnzimmer", "Spüle"). Ein Kontrollpunkt ist mehreren Remindern
  zuordenbar.
- K2 **Scannen = abhaken**: Das Handy kurz an den Sticker halten hakt den
  zugehörigen Checklisten-Punkt (7.13) bzw. Ablauf-Schritt (7.12) ab – auch bei
  gesperrtem Bildschirm, ohne die App zu öffnen.
- K3 **Reihenfolge** optional vorgeben (erst Brunnen einsammeln, dann Spüle).
- K4 **Mindestzeiten** zwischen Kontrollpunkten optional (z. B. zwischen Spüle und
  *Fertig* mindestens 5 min) – grobe Prüfung, dass wirklich etwas gemacht wurde.
- K5 Ein Scan außerhalb einer laufenden Sitzung kann die Sitzung starten
  (*Ich starte jetzt* per Sticker).
- K6 Kombinierbar mit anderen Nachweisen (z. B. Foto am Ende).
- Austricksen bleibt möglich; Ziel ist eine spürbare Hürde und eine ehrliche
  Dokumentation, keine Überwachung.

### 7.16 Forschung: Bewegungsdaten von Handy und Uhr
**Forschungsprojekt der App**, kein fertiges Feature: erst messen, nach einer Weile
auswerten, ob sich damit etwas anfangen lässt. **[V2]**
- R1 Während einer Sitzung (7.11) **Rohdaten der Bewegungssensoren** aufzeichnen:
  Beschleunigung, Gyroskop, Schrittzähler, erkannte Aktivität (Gehen/Stehen);
  auf der Uhr zusätzlich Handgelenksbewegung (über die Watch-App aus V1.4, siehe 7.14).
- R2 Aufzeichnung **nur während einer Sitzung** und nur für Reminder, bei denen sie
  eingeschaltet ist – kein Dauer-Tracking.
- R3 Ziel: ein **grobes Bewegungsmuster** pro Aufgabe erkennen, speichern und neue
  Sitzungen damit vergleichen ("sieht aus wie die letzten Male Katzenbrunnen").
- R4 Raumwechsel und Taster (7.19) dienen als **Markierungen** in den Daten (wann
  war man an der Spüle) – das macht die Auswertung deutlich einfacher.
- R5 Auswertung zuerst am Rechner (Export der Rohdaten), erst bei brauchbaren
  Ergebnissen in die App einbauen.
- Erwartung ehrlich: unsicher. Das Handy liegt oft weg; mit der Uhr sind die
  Chancen besser.

### 7.17 Routen und Bereiche per GPS
Bewegungsverlauf **draußen** aufzeichnen (Route abgelaufen, Zeit in einem Bereich
verbracht). Aktuell nicht wichtig – punktuelle Ortserkennung (T2, N4) reicht.
**[V2 oder V3]**
- Nur während einer Sitzung, Prüfung grob (Anteil im Bereich, Strecke, Punkte
  passiert).

### 7.18 Mehrere Personen im Haushalt
Mehrere Personen **ohne Backend**: Die Handys im Haushalt synchronisieren sich
regelmäßig **lokal**, da sie oft zusammen sind. **[V3]**
- M1 Gemeinsame Reminder und Checklisten (wer hat welchen Brunnen gemacht).
- M2 Abgleich, sobald die Geräte in der Nähe bzw. im selben WLAN sind; ohne
  Internet und ohne Server.
- M3 Konflikte (beide haken denselben Punkt ab) werden automatisch aufgelöst.
- M4 Was geteilt wird, ist pro Reminder festgelegt; persönliche Daten (Fotos,
  Audio, Bewegungsdaten) bleiben standardmäßig auf dem eigenen Gerät.
- Backend nur, falls die lokale Synchronisation im Alltag nicht reicht.

### 7.19 Räume und Taster über Home Assistant
Das Haus kennt den Raum, nicht nur das Handy. **[V2, ggf. V3]**
- H1 **Raumerkennung per Bluetooth**: Empfänger in den Räumen (z. B. ESP32 mit
  ESPresense oder Bluetooth-Proxys mit *Bermuda* in Home Assistant) erkennen Uhr und
  Handy. Die App erfährt über Home Assistant, in welchem Raum man ist.
- H2 Raum als **Gelegenheit** (Reminder kommt, wenn man in der Küche ist) und als
  **Nachweis** (Ablauf: erst Wohnzimmer bei den Brunnen, dann Küche an der Spüle).
- H3 **Physische Taster** (z. B. Zigbee-Taster) an festen Stellen: Druck bestätigt
  einen Checklisten-Punkt, Ablauf-Schritt oder *Fertig* – ganz ohne Handy und Uhr.
- H4 Taster und Räume sind in der App benennbar und Remindern zuordenbar.
- H5 Kommunikation nur **im Heimnetz** mit der lokalen Home-Assistant-Instanz
  (Zugriffs-Token), keine Cloud.
- H6 Ohne Home Assistant funktioniert die App wie bisher; die Integration ist ein
  Zusatz.
- Raumdaten sind Bewegungsdaten → gleiche Regeln wie 8.4.

## 8. Zielplattform und Technik

### 8.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V1.3 über Benachrichtigungen, eigene Watch-App in V1.4 (siehe 7.14)
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
| W4 Galaxy Watch | ✅ | Notifications spiegeln sich automatisch (inkl. Aktionen); laufende Sitzung als Ongoing Activity; eigene Wear-OS-App mit Compose for Wear OS, Tiles, Data Layer API | V1.3 Benachrichtigungen (Erledigt und +10 min kommen schon an, 2026-10-25), V1.4 eigene App |
| Widget | ✅ | Jetpack Glance | |
| N2 Foto | ✅ | CameraX (In-App-Aufnahme) | |
| N4 Ort | ✅ | Fused Location (Standort beim Nachweis) | Vordergrund-Standort reicht für Nachweis |
| N7 Audio | ✅ | `MediaRecorder` | Mikrofon-Berechtigung |
| N3 QR / NFC | ✅ | ML Kit Barcode, NFC-API (Reader Mode) | NFC-Kontrollpunkte V3; NTAG213 reicht |
| H1–H3 Home Assistant | ✅ | REST-/WebSocket-API von Home Assistant im Heimnetz; Raumerkennung über Bermuda/ESPresense, Taster über Zigbee | Erfordert laufendes Home Assistant und Empfänger pro Raum |
| R1 Bewegungssensoren | ✅ Aufzeichnen / ⚠️ Auswerten | `SensorManager`, Activity Recognition; Uhr: eigene Wear-OS-App | Aufzeichnen ist einfach, ob Muster erkennbar sind, ist offen (Forschung) |
| GPS-Routen | ✅ | Fused Location im Foreground-Service | Nur während Sitzungen, sonst zu akkuintensiv |
| M2 Lokale Synchronisation | ✅ | Nearby Connections (Bluetooth/WLAN ohne Internet) oder WLAN im Haushalt | Konfliktauflösung ist der eigentliche Aufwand |
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
- **Sensible Daten** (Fotos, Audio, Standorte, Bewegungsdaten) bleiben grundsätzlich
  **nur auf dem Gerät** – auch in späteren Versionen. Synchronisation mit anderen
  Geräten im Haushalt (7.18) nur für ausdrücklich geteilte Inhalte.
- Rohdaten der Bewegungssensoren (7.16) nach einer einstellbaren Frist löschen;
  das Ergebnis (Dauer, erkanntes Muster) bleibt.
- Kein Backup in V1 (später nachrüstbar, z. B. Export als ZIP).
- Aufbewahrung: vorerst unbegrenzt.

## 9. Entscheidungen

| Datum | Entscheidung |
|---|---|
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Galaxy Watch nicht in V1 (geändert am 2026-10-25, siehe unten) |
| 2026-10-18 | KI später: teils lokal, teils über Backend |
| 2026-10-18 | Feste, flexible und ortsbasierte Reminder in V1 |
| 2026-10-18 | Minimum entscheidet der Nutzer; Limits für Minimum und Verschieben pro Reminder |
| 2026-10-18 | Nachweisart pro Reminder; fehlender Nachweis = "nicht erfolgreich" |
| 2026-10-18 | Motivation über Statistik, Serien, Fortschritt (Woche/Monat/Jahr/gesamt); keine Belohnungssysteme |
| 2026-10-18 | Motivations-Audio nur auf Klick |
| 2026-10-18 | Übersicht morgens und abends (einstellbar), inkl. Rückblick |
| 2026-10-18 | Daten nur lokal, kein Backup in V1 |
| 2026-10-18 | Eskalation = Vollbild oder wiederholte Benachrichtigung bis Reaktion; **7–21 Uhr sichtbar** (Vollbild + Vibration), **18–21 Uhr laut** |
| 2026-10-18 | Training: Tagesangebot (Ankunft / 19 Uhr / sonst entfällt) + Wochenziel 3–4×, nie eine Woche ohne |
| 2026-10-18 | Wiederkehrendes standardmäßig ab letzter Erledigung, kalendarisch optional |
| 2026-10-18 | Reminder kennen Ausführungsfenster; Fristen erinnern häufig, "auf morgen" möglich |
| 2026-10-18 | Auslassen zählt neutral; Serie hält bei Minimum; ein Aussetzer pro Woche/Monat erlaubt |
| 2026-10-18 | Dokumentation per Freitext + Audio; Zahlenwerte später |
| 2026-10-18 | Widget für Ein-Klick-Erstellung; Strenge-Vorlagen in V1 (Startwerte siehe 7.9) |
| 2026-10-18 | Besprechungen: Schnellknopf statt Kalenderabgleich |
| 2026-10-18 | Wochen Mo–So; Rettungs-Erinnerung am Wochenende; Joker 1/Woche (täglich) bzw. 1/Monat (wöchentlich) |
| 2026-10-18 | V1 in Etappen V1.0 / V1.1 / V1.2 (siehe Abschnitt 10) |
| 2026-10-24 | Strenge hochgestuft: *locker* = Banner + Vibration alle 10 min, *normal* = Vollbild wie Wecker ohne Ton |
| 2026-10-25 | Alles bis einschließlich V1.3 kommt vor V2 |
| 2026-10-25 | Galaxy Watch vorgezogen auf V1.3, zuerst über Benachrichtigungen |
| 2026-10-25 | Durchführung mit Timer ("Ich starte jetzt") und Nachfrage-Muster; Abläufe für geregeltes Training, begrenzt aufs Wichtigste – keine Trainings-App |
| 2026-10-25 | Checklisten mit einzeln verschiebbaren Punkten |
| 2026-10-25 | Checkliste = Block: Intervall ab dem ersten erledigten Punkt, nächste Runde mit allen Punkten |
| 2026-10-25 | Restpunkte verschieben zählt nicht gegen das Limit, wenn am Tag mindestens ein Punkt erledigt wurde |
| 2026-10-25 | Nachfragen in Sitzungen zeitbasiert (Standard alle 10 min), Strenge der Nachfragen einstellbar |
| 2026-10-25 | Maximale Dauer pro Sitzung; danach letzter Reminder so streng wie möglich, kurze Reaktionszeit, dann nicht erfüllt – mit erledigten Teilschritten zählt das Minimum |
| 2026-10-25 | Dauer wird getrackt und mit früheren Sitzungen verglichen (schnell fertig werden soll sich lohnen) |
| 2026-10-25 | Training: nur Dauer und erledigte Schritte/Sätze; Wiederholungen/Gewicht bleiben V2 |
| 2026-10-25 | Watch: Benachrichtigungen samt Aktionen kommen bereits an – kein eigener Spike nötig |
| 2026-10-25 | NFC-Kontrollpunkte als Nachweis im Haus, V1.3 (geändert, siehe unten) |
| 2026-10-25 | NFC-Kontrollpunkte nach V3 – zu unflexibel; stattdessen Raumerkennung per Bluetooth und Taster über Home Assistant, V2 (ggf. V3) |
| 2026-10-25 | Eigene Watch-App in V1.4 – Bedienung laufender Reminder ohne Handy |
| 2026-10-25 | Leitprinzip 10: nicht ans Handy gebunden |
| 2026-10-25 | Bewegungsdaten von Handy und Uhr als Forschungsprojekt (messen, später auswerten), V2 |
| 2026-10-25 | GPS-Routen erst V2/V3 – punktuelle Ortserkennung reicht vorerst |
| 2026-10-25 | Sensible Daten grundsätzlich nur lokal auf dem Gerät |
| 2026-10-25 | Mehrere Personen (V3) möglichst ohne Backend über lokale Synchronisation im Haushalt |

## 10. Nächste Schritte

1. **Technischer Spike auf dem S24 Ultra** (Plan: [spike.md](spike.md)): exakter
   Alarm, Vollbild über Sperrbildschirm, Vibrationsmuster, Alarmton, Geofence
   "Ankunft zuhause", Überleben von Neustart und Samsung-Akkumanagement über mehrere Tage
2. **V1 in Etappen**, damit früh echte Nutzung möglich ist:
   - **V1.0 (benutzbar):** Zeit-Reminder, Eskalation, Aktionen, Quittieren/Foto,
     Ziel/Minimum, einfache Liste der Erledigungen, Widget
     – ✅ seit 2026-10-24 auf dem Gerät, inkl. Intervall ab letzter Erledigung
   - **V1.1:** Ort (Ankunft zuhause), **Checklisten** (7.13), Audio-Nachweis,
     Motivations-Audio
   - **V1.2:** Wochenziele, Fristen mit Ausführungsfenstern, Serien/Joker,
     Statistik, Übersicht morgens/abends, Vorlagen
   - **V1.3:** **Durchführung mit Timer** und Nachfrage-Muster (7.11), **Abläufe**
     für Training (7.12), **Galaxy Watch** über Benachrichtigungen (7.14)
   - **V1.4:** **eigene Watch-App** für laufende Reminder (7.14, G5–G9)
3. Weitere Anforderungen ergeben sich aus der Nutzung.

## 11. Offene Fragen

Aktuell keine großen offenen Fragen; die Fragen vom 2026-10-25 sind entschieden
(Abschnitt 9). Details (Oberfläche, Vorlagen, Vibrationsmuster, Standardwerte für
Nachfrage-Intervall und Reaktionszeit) werden mit der Nutzung geklärt. Vor V1.3
kurz prüfen, ob *Ich starte jetzt* und die Nachfrage-Antworten auf der Uhr genauso
ankommen wie *Erledigt* / *+10 min*.

Für später:
- V1.4: Wie viel Logik läuft auf der Uhr selbst (Sitzung weiterführen ohne Handy)?
- V2: Welche Home-Assistant-Lösung für Räume (Bermuda mit Bluetooth-Proxys oder
  ESPresense), wie genau ist sie pro Raum, und erkennt sie die Uhr zuverlässig?
- V2: Welche Kennzahlen machen ein Bewegungsmuster "gleich genug"? Klärt sich erst
  mit echten Messdaten (7.16).
- V3: Wie oft und worüber synchronisieren (Nearby Connections vs. Heim-WLAN), und
  welche Konfliktregel gilt bei gleichzeitigen Änderungen (7.18)?
