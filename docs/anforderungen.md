# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Stand: 2026-11-05 – umgesetzt bis V1.3b0.2 (gebaut, V1.3b0.1 auf dem Gerät; Übersicht in Kap. 10). Entscheidungen mit Datum in
> Kap. 9, technisches Design nach Themen in [design.md](design.md). Wird iterativ verfeinert.

## 1. Vision

Eine Android-App, die Erinnerungen **extrem effektiv** macht: Ein Reminder gilt erst
als erfüllt, wenn die Aufgabe nachweislich erledigt (oder bewusst verschoben) wurde.
Dazu kombiniert die App Eskalation, Nachweise, Kontext-Trigger und psychologische
Verstärkung – **mit Schwerpunkt auf positiver Verstärkung**.

Keine Consumer-App: sehr kleine, spezialisierte Zielgruppe, die sich bewusst für
"strenge" Mechanismen entscheidet. Störende/invasive Mechanismen sind gewollt,
solange der Nutzer sie selbst konfiguriert.

Langfristig entwickelt sich die App ein Stück **Richtung Lebensplanung** (z. B.
Haushaltsinventar und Einkaufsliste, 7.23) – immer im Dienst der Reminder, nicht
als eigenständige Verwaltungs-App.

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
| V2 | Abgleich mit Zielen, lokale KI, Zahlenwerte, Backup, **Forschung Bewegungsdaten** (7.16), **Räume und Taster über Home Assistant** (7.19), ggf. GPS-Routen (7.17), **Schnittstelle für KI-Agenten** (7.26) | lokal bzw. im Heimnetz |
| V3 | Mehrere Personen im Haushalt, gemeinsame Reminder (7.18), KI über Backend, ggf. NFC-Kontrollpunkte (7.15), ggf. **Haushaltsinventar und Einkaufsliste** (7.23) | Lokale Synchronisation zwischen den Handys – Backend nur, wenn nötig |

"Alignment" umfasst langfristig: **Erledigung erzwingen** (V1), **Abgleich mit
Zielen** (V2), **Abstimmung mehrerer Personen** (V3).

## 5. Anwendungsfälle (erste Reminder)

| Reminder | Art | Charakter | Bemerkung |
|---|---|---|---|
| Training unter der Woche | Tagesangebot bei Ankunft zuhause / 19:30 + **Wochenziel 3–4×** | Überwindung | Nie eine Woche ganz ohne Training; Freitext + Audio |
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
  - Ankunft zuhause im Zeitraum vor 19:30 (18:00–19:30, 7.29) → Reminder bei Ankunft.
  - Schon zuhause (Homeoffice) → Reminder um 19:30.
  - Um 19:30 nicht zuhause → Training **entfällt** für heute (neutral).
  - Schon verschoben oder geschlummert und dann nicht zuhause → klingelt bei Ankunft,
    sonst zur Tagesgrenze **verpasst** (7.29 OZ5).
- Tatsächlich bewertet wird das **Wochenziel**: Ziel 3–4×, Minimum 1×.
  Eine Woche ganz ohne Training darf nicht passieren.

Die Regel "um 19:30 nicht zuhause → entfällt" macht eine Erkennung von
Homeoffice-Tagen überflüssig: Es zählt nur, ob man zum Zeitpunkt am Ort ist.
Seit 2026-11-04 zählt eine Ankunft nur im Zeitraum vor der Uhrzeit (z. B. 90 min),
erkannt über das WLAN (7.29; GPS später, 7.30).

## 6. Reminder-Modell (Entwurf)

Alle Anwendungsfälle lassen sich mit denselben Bausteinen beschreiben:

| Baustein | Frage | Beispiele |
|---|---|---|
| **Aufgabe** | Was? | Titel, "Warum ist mir das wichtig?", Motivations-Audio, Startklar-Fragen und Blocker (7.21) |
| **Gelegenheit** | Wann/wo ist es möglich? | Zeitfenster pro Wochentag (Praxis Mo–Fr 8–12), Ort (zuhause), Kombination |
| **Takt** | Wie oft / bis wann? | einmalig, Frist, fester Kalender-Rhythmus, Intervall ab letzter Erledigung, x-mal pro Zeitraum |
| **Ziel** | Wie viel? | Zielwert, Minimum, Limit für Minimum |
| **Nachweis** | Womit belegt? | keiner, Quittieren, Foto, Text, Ort, Audio, Kombination; später Raum oder Taster (Home Assistant) |
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
  - **Ankunft bestätigen** **[V1.1]**: Der Geofence ist eher grob (Spike: Radius
    150 m, Vorbeifahren am Zuhause zählte als Ankunft). Ankunft erst nach einer
    kurzen **Verweildauer** werten (einstellbar, z. B. 2–5 min; Wert zeigt sich in
    der Praxis), optional zusätzlich Verbindung mit dem Heim-WLAN als Bestätigung.
    Ab 7.29 gilt: **erkannte Ankunft** = WLAN des Orts verbunden oder Geofence-Meldung
    nach der Verweildauer – je nachdem, was zuerst kommt (GPS später, 7.30). Die
    Verweildauer gilt nur für den Geofence; der Puffer (OZ4) läuft ab der erkannten
    Ankunft (2026-11-05).
  - Nach einem Neustart den Standort aktiv prüfen, statt auf die automatische
    Meldung zu warten.
- T2b Kombination Ort + Uhrzeit: "bei Ankunft, sonst um 19:30 falls schon dort,
  sonst entfällt" **[V1]**. Wird zum Ankunftszeitraum mit Vorlauf und WLAN
  (7.29, 2026-11-04; GPS später, 7.30).
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
- **Nach Ablauf der Frist** (2026-11-05): Der Termin zählt als **verpasst** und bleibt
  als **überfällig** in *Heute*, klingelt in seinem nächsten Fenster weiter, bis er
  erledigt oder bewusst *verworfen* ist. Der Notaus (7.28 PS7) ändert daran nichts:
  „Fristen bleiben hart“ heißt, der Notaus macht eine Frist nicht neutral.

### 7.3 Wahrnehmung sicherstellen (Audio / Visual)
- **Eskalation** = auffällig oder wirklich blockierend: Vollbild-Reminder oder
  wiederholte Benachrichtigung, bis der Nutzer reagiert. **[V1]**
- **Wann sichtbar, wann laut** (V1.0 noch mit festen Zeitfenstern 7–21 Uhr; ab
  **V1.1** wie folgt):
  - **Sichtbar:** Vollbild (damit man es sieht, wenn das Handy lautlos auf dem
    Tisch liegt) + Signal je nach Einstellung, meist **Vibration**. Gilt
    ausdrücklich auch während der Arbeitszeit.
  - **Laut ab** (einstellbar, Standard 18 Uhr): zusätzlich richtig laut (Alarmton
    wie ein Wecker) – nur bei Strenge *streng* (7.9).
  - **"Nicht stören" statt fester Ruhezeiten** **[V1.1]**: Ist auf dem Handy
    "Nicht stören" aktiv (auch Prioritätsmodus oder ein Samsung-Modus, der es
    einschaltet), kommt nur eine stille Benachrichtigung. Grund: "Nicht stören" ist
    je nach Tag anders eingestellt (z. B. werktags bis 6 Uhr, am Wochenende bis
    10 Uhr) – eine zweite Ruhezeit in der App wäre doppelt.
    - Die App kennt den Zeitplan von "Nicht stören" nicht. Die Eskalation läuft
      währenddessen still in 10-Minuten-Runden weiter; die erste Runde nach dem
      Ende alarmiert normal (höchstens gut 10 min später).
    - Hinweis in der App, solange "Nicht stören" aktiv ist und Reminder warten
      ("Nicht stören aktiv – 2 Reminder warten").
  - **"Auch bei Nicht stören"** **[V1.1]**, pro Reminder einstellbar, für besondere
    Ereignisse: Der Reminder alarmiert trotz "Nicht stören" wie eingestellt (normal
    oder streng, auch laut). Der Aufsteh-Wecker (7.22) ist ein solcher Reminder.
    - Voraussetzung: Die App ist einmalig in den Einstellungen von "Nicht stören"
      als **Ausnahme** freigegeben – sonst unterdrückt Android den Vollbild-Alarm.
      Die Berechtigungsliste der App prüft das und warnt, wenn ein Reminder die
      Option nutzt, die Freigabe aber fehlt.
  - **Tagesgrenze** **[V1.1]**, einstellbar, Standard **23 Uhr**: Was bis dahin ohne
    Reaktion offen ist, gilt als **verpasst** (bisher: Ende des Fensters um 21 Uhr).
    Unabhängig von "Nicht stören".
- **Mehrere Reminder gleichzeitig fällig** (2026-11-05, geplant mit V1.3b): Es gibt
  immer nur **ein Vollbild** zur Zeit. Der nächste wartet mit dem Hinweis *+1 wartet*
  und erscheint, sobald das erste erledigt, verschoben oder ausgelassen ist. Seine
  Runden laufen derweil weiter, er verliert also keine Eskalation.
- **Eigene Vibrationsmuster**: länger und ungewöhnlich, damit man sie von normalen
  Benachrichtigungen unterscheidet **[V1]**
- Besprechungen: kein Kalenderabgleich; Schnellknopf *nach dem Termin* (+30/60 min) reicht.
- Häufige Erinnerungen (z. B. stündlich bei Fristen): nicht laut, sondern Vibration
  nach Einstellung.
- Rettungs-Erinnerung: Droht ein Wochenziel bei 0 zu bleiben, erinnert die App am
  Wochenende gezielt. Wochen laufen Montag–Sonntag. **[V1]**
- W1 Eskalationsstufen: Notification → wiederholte Notification/Ton → Vollbild-Alarm **[V1]**
- W2 Vollbild-Alarm über Sperrbildschirm, Bypass von "Nicht stören" (nur mit Option
  "auch bei Nicht stören", siehe 7.3), Alarmton wie
  ein Wecker **[V1]**
- W3 Overlay über anderen Apps [später]
- W4 Vibrationsmuster, Galaxy Watch **[V1.3 / V1.4]** (siehe 7.14; Vollbild-Alarme
  erreichen die Uhr seit V1.1 über G1b)
- W5 Sprachausgabe (TTS) mit Aufgabentext [später]

### 7.4 Aktionen im Reminder/Alarm
Erster Satz, wird mit der Nutzung angepasst: **[V1]**
- *Jetzt erledigen* (→ Nachweis)
- *Minimum* (→ Nachweis)
- *Später heute (+x min)* / *nach dem Termin* (+30/60 min)
  - **Verschiebe-Knöpfe pro Reminder** **[V1.1]**: 1–2 Knöpfe frei wählbar, z. B.
    *+10 min* (Standard) oder *+30 / +60 min* für Arbeitsreminder. Höchstens zwei,
    weil eine Benachrichtigung max. 3 Aktionen zeigt (eine davon *Erledigt*) – so
    passt es auch auf die Uhr.
  - Der Vollbild-Alarm zeigt dieselben Knöpfe groß.
  - Auswahl beim Anlegen; ab V1.2 setzen Vorlagen sie mit (z. B. Vorlage "Arbeit"
    → *+30 / +60*).
  - Kurzes Verschieben bleibt **ohne Limit**, wird aber ehrlich im Verlauf gezählt
    und fließt in "Was hält dich ab?" (S8) ein. Ein Knopf, der über die
    Tagesgrenze (7.3) hinausführen würde, wird ausgeblendet.
- *Verschieben auf …* (zählt gegen das Limit; "morgen" als Schnellwahl)
- *Auslassen (mit Grund)* – zählt **neutral**
- **Drei Knöpfe in der Benachrichtigung** (2026-11-05): Android zeigt höchstens drei.
  Welche, hängt an der Art des Reminders:
  - mit Durchführung (7.11): *Ich starte jetzt* · ein *Später* · *Erledigt*
  - mit Checkliste (7.13): *Erledigt: ‹nächster Punkt›* · ein *Später* · *Rest später*
  - sonst: *Erledigt* · bis zu zwei *Später*-Knöpfe (wie oben)
  - *Erledigt* fehlt, wenn ein Nachweis gefordert ist; dann öffnet ein Tippen den Alarm.
  - Der Vollbild-Alarm zeigt immer alle Aktionen.

### 7.5 Erledigung nachweisen
- **Nachweisart pro Reminder wählbar** (keiner, Foto, Text, Ort, Audio,
  Kombination) – der Nutzer weiß, was passt. **[V1]** Nachweise sind
  **kombinierbar** (z. B. Foto + Text bei den Katzenbrunnen) **[V1.1]**.
- Nachweise sind wertvoll: Sie bringen einen dazu, sich mit dem Reminder
  auseinanderzusetzen.
- N0 **Alarm aus, solange der Nachweis läuft** **[V1.1]**:
  - Beim Öffnen des Nachweises verstummen Ton und Vibration sofort, die
    Eskalation pausiert. Benachrichtigung und Uhr zeigen "Nachweis läuft".
  - **Frist** für den Nachweis, Standard 10 min, pro Reminder einstellbar. Danach
    eskaliert es weiter – sonst wäre "Nachweis öffnen und weglegen" eine Hintertür.
  - Abbrechen oder die App verlassen: nach kurzer Schonfrist (ca. 1 min) geht die
    Eskalation normal weiter.
- *Erledigt* direkt in der Benachrichtigung bzw. auf der Uhr gibt es nur bei
  Reminders ohne Foto-, Text- oder Audionachweis.
- N1 Einfaches Quittieren – für reine Erinnerungen **[V1]**
- N2 **Foto-Nachweis**, nur mit der App-Kamera aufgenommen (nicht aus der Galerie),
  mit Zeitstempel **[V1]**
- N4 **Ortsnachweis**: Standort beim Nachweis wird gespeichert und optional gegen
  einen erwarteten Ort geprüft (kombinierbar mit Foto) **[später]** (2026-11-05: in
  V1 nicht umgesetzt und nicht vermisst; kommt frühestens mit den Orten aus 7.29)
- N7 **Audio-Nachweis**: kurze Sprachaufnahme "was habe ich erledigt" **[V1.1]**
  - **Mindestdauer** pro Reminder einstellbar (z. B. 10 s).
  - Im Verlauf abspielbar; später Abschrift per lokaler KI (N8), damit man darin
    suchen kann.
  - Ab V1.4 auch **von der Uhr** aufnehmbar (Mikrofon der Galaxy Watch) – Nachweis
    ohne das Handy in die Hand zu nehmen (G10).
- N11 **Text-Nachweis** **[V1.1]**: aufschreiben, was man getan hat.
  - **Mindestzahl an Zeichen** pro Reminder einstellbar (z. B. 50); Leerzeichen
    zählen nicht.
  - Leichte Prüfung gegen Unsinn: nicht nur ein wiederholtes Zeichen, nicht
    identisch mit dem letzten Text. Mehr Prüfung später per lokaler KI (N8).
  - Diktieren über die Tastatur ist erlaubt.
  - Die Texte machen den Verlauf wertvoll und sind Grundlage für Zahlenwerte (N9).
- **Dokumentation** zur Erledigung: Freitext + Audio (z. B. Training) **[V1]**
- **Fehlender Nachweis:** Wird ein geforderter Nachweis nicht erbracht, gilt der
  Reminder als **nicht erfolgreich** und wird so markiert. Kommt vor und ist in
  Maßen unkritisch – kein Drama, aber ehrlich in der Statistik. **[V1]**
  - Genauer (2026-11-05): *nicht erfolgreich* entsteht nur, wenn man den Reminder
    ausdrücklich ohne Nachweis abschließt. Bleibt der Nachweis einfach offen, läuft
    die Eskalation nach der Nachweis-Frist weiter (N0), und zur Tagesgrenze ist der
    Termin **verpasst**.
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
- P4 Wenn-dann-Vorsätze ("Wenn X, dann tue ich Y") [später]; Hindernisse und
  erster Schritt sind schon Teil der Startklar-Fragen (7.21)
- Belohnungen/Punkte/Level: eher nicht – Belohnungen sind in einer App schwierig.

**Negativ (begrenzt, taktisch):**
- Eskalation mit Vollbild/Alarm im Eskalationsfenster **[V1]**
- P2 Ablenkungs-Apps sperren [später, Regeln offen]
- P5 Dritte Person informieren [V3]

### 7.8 Überblick
- O1 **Übersicht morgens** (was steht heute an) und **abends** (Rückblick auf heute,
  Stand der Wochenziele, Vorschau auf morgen). Zeiten und Umfang einstellbar. **[V1]**
  Bei mehreren Uhrzeiten (7.25 VS1) steht jede Uhrzeit einzeln drin (2026-11-04).
- O2 Rückblick ist Teil der Übersicht (Tag; Woche/Monat in der Statistik) **[V1]**

### 7.9 Bedienung
- **Widget**: ein Klick → direkt in die Reminder-Erstellung **[V1]**
- **Vorlagen / Strenge-Stufen**, die Eskalation, Nachweis und Limits bündeln
  (Startwerte, Refinement mit der Nutzung): **[V1]**
  - *locker*: Banner + Vibration, alle 10 min wiederholt, Quittieren, Verschieben unbegrenzt
  - *normal*: Vollbild-Alarm wie ein Wecker, aber ohne Ton; Nachweis wählbar, 3× Verschieben
  - *streng*: Vollbild-Alarm, ab *laut ab* mit Alarmton (7.3), Nachweis Pflicht,
    1× Verschieben, Minimum max. 1×/Woche
- UI wird sich mit der tatsächlichen Nutzung stark anpassen → früh benutzbare
  Version wichtiger als vollständige Planung.
- **Reminder-Liste aufgeräumt halten** (aus der Nutzung, 2026-10-29): **[V1.2]**
  - L1 **Abgeschlossen ausblenden**: Erledigte Einmal-Reminder (auch *Wenn ich
    zuhause bin*) kommen automatisch in einen eingeklappten Bereich
    *Abgeschlossen (n)* am Ende der Liste.
  - L2 **Archivieren** von Hand, vor allem für wiederkehrende Reminder, die man
    nicht mehr braucht, aber wegen des Verlaufs nicht löschen will. Archiviert
    heißt pausiert und im Bereich *Abgeschlossen*.
  - L3 **Wiederherstellen**: Ein wiederkehrender Reminder kommt pausiert zurück.
    Ein Einmal-Reminder braucht eine neue Zeit; das Bearbeiten öffnet sich dafür.
  - L4 **Gruppen**: aktiv, darunter *Pausiert*, ganz unten *Abgeschlossen*.
  - L5 **Sortieren** innerhalb der Gruppen: *nächste Fälligkeit* (Standard),
    *Name*, *zuletzt angelegt*. Die Wahl bleibt gespeichert. Weitere Optionen erst,
    wenn sie fehlen.

### 7.10 Lebenszyklus eines Reminders
Der genaue Ablauf mit allen Zuständen steht in [design.md 3](design.md); hier die Sicht
des Nutzers (Stand 2026-11-05):

```
geplant ─► Vorab-Reminder ─► fällig ─► eskaliert (Runden)
   │                          │
   ├─► vorab erledigt / vorgezogen gestartet (vor der Fälligkeit – siehe 7.20)
   ├─► entfällt (Gelegenheit trat nicht ein, z. B. um 19:30 nicht zuhause – neutral)
   ├─► ausgelassen (Wochenziel schon erreicht, Notaus vor der Fälligkeit, pausiert – neutral)
   │                          ├─► läuft (Ich starte jetzt, Timer/Ablauf, Nachfragen – siehe 7.11)
   │                          │     └─► endet wie unten: Ziel / Minimum / abgebrochen (nicht erfolgreich)
   │                          ├─► teilweise erledigt (Checkliste: Restpunkte auf später – siehe 7.13)
   │                          ├─► erledigt (Ziel) / erledigt (Minimum)
   │                          ├─► nicht erfolgreich (ausdrücklich ohne Nachweis abgeschlossen)
   │                          ├─► später (+x min, ohne Limit) / verschoben (zählt gegen das Limit)
   │                          │     └─► klingelt wieder; bei Ankunft-Remindern erst bei Ankunft (7.29 OZ5)
   │                          ├─► ausgelassen (bewusst, mit Grund – neutral)
   │                          └─► verpasst (keine Reaktion bis zur Tagesgrenze bzw. zum nächsten Termin)
   └─► Frist abgelaufen ─► verpasst, bleibt *überfällig* bis erledigt oder verworfen (7.2)
```

Pausierte und archivierte Reminder (7.9 L2) erzeugen keine Termine; ein offener Termin
wird beim Pausieren ausgelassen. Fortsetzen plant den nächsten Termin ab jetzt, ohne
verpasste Termine nachzumelden.

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
- D7 **Sitzung und andere Regeln** (2026-11-05, Review-Lauf 3; Technik in design.md E.3):
  - Eine laufende Sitzung wird **nicht verpasst**: weder zur Tagesgrenze noch beim
    nächsten Termin eines *mehrmals täglich*-Reminders noch am Fensterende einer Frist.
    Sie endet nur über die Nachfragen oder die maximale Dauer (D5). Über die
    Tagesgrenze hinaus zählt sie für den Tag, an dem sie begann. Der nächste Termin
    desselben Reminders wartet, bis die Sitzung endet.
  - **Notaus** (7.28 PS7): Die Nachfragen kommen still; nach der maximalen Dauer endet
    die Sitzung wie in D5, mit erledigten Teilschritten als Minimum, sonst *abgebrochen*.
  - **„Nicht stören“**: Nachfragen kommen still wie jeder andere Alarm (7.3); die
    Reaktionszeit für die letzte Nachfrage beginnt erst, wenn sie hörbar sein darf.
  - Pausieren, Archivieren, Bearbeiten und die Schnittstelle ändern eine laufende
    Sitzung nicht (wie beim klingelnden Reminder, 7.26 AG11).
  - Klingelt währenddessen ein anderer Reminder, gilt *ein Vollbild zur Zeit* (7.3);
    die Sitzung läuft im Hintergrund weiter.

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
- C8 **Punkte jederzeit abhaken** (2026-11-04): im Formular *Punkte abhaken ›* öffnet den
  offenen Termin wie *Vorab erledigen*; *Fertig für jetzt – Termin bleibt* geht ohne
  Abschluss zurück, der nächste Zeitpunkt ändert sich nicht. Im Alarm sind die Punkte
  schlichte Zeilen (kleiner Radius, mehrzeilig), damit lange Punkte lesbar bleiben.

### 7.14 Galaxy Watch
Guter Hebel für neue Anforderungen; zuerst so viel wie möglich über
**Benachrichtigungen**, eigene Watch-App nur wenn nötig. **[V1.3]**
- G1 Erinnerungen, Eskalation und Vibration kommen **auf der Uhr** an.
  ✅ Schon mit V1.0 beobachtet: Benachrichtigung samt *Erledigt* und *+10 min*
  erscheint auf der Uhr – **aber nur bei "locker"** (stille Benachrichtigung).
  "normal" und "streng" kamen anfangs nicht an (beobachtet 2026-10-26): Der
  Vollbild-Alarm ist eine laufende Vordergrund-Benachrichtigung, und solche spiegelt
  Android nicht auf die Uhr. ✅ Gelöst mit G1b (V1.1).
- G1b **Vollbild-Alarme auch auf der Uhr** **[V1.1]**: Zusätzlich zum Vollbild-Alarm
  eine normale, spiegelbare Benachrichtigung mit *Erledigt* / *+10 min*, die mit dem
  Alarm verschwindet; die Uhr vibriert bei jeder Eskalationsrunde. Auf dem Handy
  darf dadurch kein doppelter Eintrag stören. Auf dem Gerät prüfen.
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
  - G8b **Audio-Nachweis direkt auf der Uhr** aufnehmen (N7).
  - G9 **Die Uhr ist Fernbedienung und Anzeige, das Handy das Gehirn.** Annahme: Das
    Handy ist immer in der Nähe der Uhr. Timer, Alarme, Abläufe und Daten laufen nur
    auf dem Handy; die Uhr zeigt den Zustand, den das Handy schickt, sendet
    Knopfdrücke zurück und vibriert auf Befehl. Countdowns rechnet die Uhr nur für die
    Anzeige aus Startzeit und Dauer. Ohne Verbindung zeigt die Uhr das deutlich an;
    die Sitzung läuft auf dem Handy weiter.
  - G10 **Während ein Reminder läuft, muss man das Handy nie in die Hand nehmen.**
    Jede Aktion einer laufenden Sitzung (inkl. Nachfragen und letzter Reminder aus D5)
    ist auf der Uhr möglich. Nur Foto-Nachweis bleibt am Handy.
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
- H1 **Raumerkennung per Bluetooth-Empfängern**: Empfänger in den Räumen (z. B. ESP32
  mit ESPresense oder Bluetooth-Proxys mit *Bermuda* in Home Assistant) erkennen die
  **Uhr als Sender** (das Handy liegt oft woanders). Raumgenauigkeit reicht. Die App
  erfährt über Home Assistant, in welchem Raum man ist.
- H1b **Die Watch-App sendet selbst ein Beacon-Signal** mit fester Kennung, solange
  eine Sitzung läuft. Grund: Uhren wechseln ihre Bluetooth-Adresse aus
  Datenschutzgründen regelmäßig und werden sonst nicht stabil erkannt. Nur während
  Sitzungen → wenig Akku.
- H1c **Aktive Methoden für den Raumwechsel**, um schneller und sicherer zu sein als
  Signalstärke allein:
  - Bewegung der Uhr erkannt (Gehen) → Beacon kurz häufiger senden, danach wieder
    sparsam.
  - Präsenzmelder (mmWave/PIR) oder Türkontakte in Home Assistant bestätigen, dass
    in einem Raum gerade jemand ist bzw. eine Tür passiert wurde.
  - Taster (H3) als eindeutige Bestätigung.
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

### 7.20 Vorziehen und vorab erledigen
Der Reminder ist oft nur der **letztmögliche Zeitpunkt**. Wer vorher handelt, soll
das ohne Umweg tun können – und es soll sich gut anfühlen.
- V1 **Vorab erledigen**: Jeder anstehende Reminder (Heute-Liste, *Später heute*,
  Detailansicht) lässt sich schon vor der Fälligkeit erledigen – Ziel, Minimum oder
  mit Nachweis wie sonst auch. Der Alarm entfällt, ein Vorab-Reminder (T6) auch.
  **[V1.1]**
- V2 **Vorziehen und starten**: Reminder mit Durchführung (7.11) oder Ablauf (7.12)
  lassen sich vorher **starten**, z. B. Training früher beginnen – mit Timer,
  Nachfragen, Schritten und Uhr wie beim regulären Reminder. **[V1.3]**
- V3 Folgen für den Takt:
  - *nach Erledigung*: Das Intervall zählt ab der tatsächlichen Erledigung.
  - *fester Kalender-Rhythmus*: Erledigt wird der nächste Termin; der übernächste
    bleibt, wo er ist.
  - *Wochenziel*: Zählt für die Woche, in der es erledigt wurde.
  - *Tagesangebot mit Bedingung* (Training bei Ankunft / 19:30): Wurde heute schon
    trainiert, kommt das Angebot heute nicht mehr.
  - *Checkliste*: Einzelne Punkte vorab abhaken; der Reminder kommt nur mit den
    Restpunkten.
- V4 Vorziehen ist nur für den **nächsten** Termin eines Reminders möglich, nicht
  für mehrere im Voraus.
- V5 **Positive Verstärkung**: "vor dem Reminder erledigt" wird im Verlauf und in
  der Statistik eigens ausgewiesen (z. B. "12× diesen Monat vor der Erinnerung").
  Kein Malus, wenn man es erst beim Reminder macht.
- V6 Schnellzugriff: Widget und (ab V1.4) Uhr bieten *Jetzt starten* bzw.
  *Erledigt* für den nächsten Reminder.

### 7.21 Startklar-Fragen und Blocker bei Überwindung
Reminder scheitern selten am Erinnern, sondern am **Grund, warum man nicht
anfängt** – oft eine ungelöste Vorbedingung, die zur Dauer-Ausrede wird (Beispiel:
Training braucht erst eine größere Aufräumaktion). Wichtig ist, sich damit
auseinanderzusetzen, **warum dieser Reminder Überwindung braucht**. Besonders
relevant für Arbeitsaufgaben und Wartung. Angelehnt an WOOP (Wunsch, Ergebnis,
Hindernis, Plan).
- S1 Beim Anlegen eines Reminders mit Charakter **Überwindung** beantwortet man
  (Pflicht; bei *Dokumentation* optional): **[V1.1]**
  1. **Was hast du davon, wenn es erledigt ist?** (Attraktivität des Ziels)
  2. **Was könnte dich abhalten?** (Hindernisse)
  3. **Kannst du sofort starten? Wenn nein: was fehlt?** (Startbereitschaft)
  4. **Was ist der allererste Schritt?** (kleinster konkreter Anfang, z. B.
     "Matte ausrollen", "Ticket X öffnen")
- S2 *Weiß ich noch nicht* ist eine erlaubte Antwort und wird als offener Punkt
  markiert – das Anlegen darf keine Hürde werden. **[V1.1]**
- S3 Fragen und Antworten sind **im Reminder gespeichert und jederzeit änderbar**
  (z. B. eintragen, dass ein Blocker beseitigt ist). Frühere Fassungen bleiben im
  Verlauf erhalten – so sieht man, welche Blocker immer wiederkehren. **[V1.1]**
- S4 Der **Alarm zeigt** Ziel (Frage 1), ersten Schritt (Frage 4) und offene
  Blocker. **[V1.1]**
- S5 **Blocker werden zu Aufgaben**: Ein "was fehlt" aus Frage 3 wird eine
  **Vorbereitungsaufgabe** mit eigenem Termin vor dem Reminder; abhaken = Blocker
  beseitigt. So beginnt die Vorbereitung, bevor der Reminder kommt. **[V1.2]**
- S6 Der **Vorab-Reminder** (T6) erinnert gezielt an offene Blocker ("Morgen
  Training – Bereich ist noch nicht aufgeräumt"). **[V1.2]**
- S7 **"Blocker gelöst" kann als Minimum zählen**, pro Reminder einstellbar; das
  Limit für Minimum gilt. **[V1.2]**
- S8 **Nachfragen, wenn es hakt**: Wird ein Überwindungs-Reminder z. B. 2× in Folge
  verschoben, ausgelassen oder verpasst, fragt die App "Was hält dich gerade ab?" –
  die Antwort wird ein neuer Blocker. Schwelle einstellbar. **[V1.2]**

### 7.22 Aufsteh-Wecker – erst aus, wenn man aus dem Bett ist
Aufstehen ist sehr unattraktiv; der Wecker soll es **forcieren**. Extreme Methode,
aber mit viel echtem Mehrwert. **[V2 – Kandidat zum Vorziehen]**
- AW1 **Zwei Stufen:**
  1. **Wecker 1 – normal**, abschaltbar (optional zuerst Vibration auf der Uhr).
  2. **Kontrollfenster** (Standard 10 min): Die App prüft still, ob man aufgestanden
     ist.
  3. **Wecker 2 – nicht abschaltbar**, laut; kommt nur, wenn im Kontrollfenster
     kein Nachweis kam, und endet nur mit Nachweis.
- AW2 **Wach-bleiben-Prüfung** (Standard 15 min nach dem Nachweis): Deutet etwas
  darauf hin, dass man wieder liegt (keine Schritte, Schlafzimmer, Bettsensor),
  startet Wecker 2 erneut.
- AW3 **Nachweis "aufgestanden"** – stufenweise:
  - **PoC / einfachste Variante:** Schritte (Schrittdetektor) auf der **Uhr**
    oder dem Handy, z. B. 30–50 Schritte innerhalb weniger Minuten. Ohne Home
    Assistant umsetzbar.
  - Später: Foto von einem festen Ort (z. B. Waschbecken) mit Bildvergleich (lokale
    KI, V2).
  - Später, mit Home Assistant (7.19, noch wacklig): Raumwechsel der Uhr (z. B.
    2 min im Bad), Taster in Bad/Küche, Bettsensor (Druckmatte) – Letzterer ideal
    für AW2.
  - Bewegungsabfolgen/Gesten: nicht verfolgen (aufwendig, im Bett machbar).
- AW4 **Auslassen nur am Vorabend** (krank, Urlaub, Wochenende) – nicht morgens im
  Halbschlaf.
- AW5 **Notausgang** für echte Notfälle (z. B. langen Satz abtippen); zählt als
  nicht erfolgreich.
- AW6 "Nicht abschaltbar" heißt unter Android "sehr lästig", nicht "unmöglich"
  (Ausschalten, Stopp erzwingen). Umgehungen werden erkannt und ehrlich als
  "nicht aufgestanden" gewertet.
- AW7 **Akku-Warnung** am Abend, wenn das Handy fast leer bzw. nicht am Ladegerät ist.
- AW8 **Positive Verstärkung:** Zeit bis zum nachgewiesenen Aufstehen wird
  festgehalten; "mit Wecker 1 aufgestanden" wird als Erfolg eigens ausgewiesen,
  Wecker 2 ist der ehrliche Fallback. Motivations-Audio (P7) passt hier besonders.
- Später mit mehreren Personen (V3): Wecker 1 rücksichtsvoll nur auf der Uhr.

### 7.23 Haushaltsinventar und Einkaufsliste
Ein **ungefährer Ist-Stand** aller Dinge im Haushalt – Lager, Keller und in
begrenztem Maß Verbrauchsgüter wie Gewürze. Bewusst ungenau, weil man das
Aktualisieren vergisst. Das Inventar **liefert Gründe für Reminder** (auffüllen,
warten) und ist keine eigenständige Verwaltungs-App. **[V3 – vielleicht nie]**
- I1 **Grobe Füllstände** statt Zahlen: *voll / genug / wenig / leer*. Stückzahlen
  nur, wo es sich lohnt (z. B. Filter, Batterien).
- I2 **"Zuletzt gesehen"** pro Eintrag; die App zeigt ehrlich, wie verlässlich der
  Stand ist ("Stand: vor 8 Monaten").
- I3 **Orte als Hierarchie** (Keller → Regal 2 → blaue Kiste). "Wo ist das?" ist oft
  wichtiger als die Menge.
- I4 **Eine Kiste kann ein Eintrag sein**: Foto plus Stichworte ("Weihnachtsdeko,
  Lichterketten") statt jedes Ding einzeln.
- I5 **Aktualisieren nebenbei**:
  - Abhaken auf der Einkaufsliste füllt den Bestand auf.
  - "Leer" antippen setzt den Artikel auf die Einkaufsliste.
  - **Teil-Inventur-Reminder** (z. B. vierteljährlich "Keller-Regal 2 kurz
    prüfen"), Charakter *Dokumentation*.
- I6 **Reminder aus dem Inventar**: auffüllen, wenn etwas knapp wird; Wartung mit
  Bezug auf den Vorrat (z. B. "Filter Katzenbrunnen wechseln – noch 1 im Keller").
- I7 **Ersterfassung per Import** (JSON o. ä.): Die Inventarisierung läuft
  **außerhalb der App**, z. B. mit Fotos und einer starken Cloud-KI; die App
  importiert nur das Ergebnis. Das liegt in der Verantwortung des Nutzers, nicht
  der App. Import mit Vorschau und Bestätigung.
- **Einkaufsliste:**
  - E1 Einkaufsliste in der App; Artikel kommen von Hand, aus dem Inventar (I5) oder
    aus Remindern.
  - E2 **Geschäfts-Trigger** (wie der Trigger für Zuhause, T2): Ankunft in einem
    Geschäft erinnert an die Liste. Eigene Geschäfte werden markiert (keine
    Filialsuche über Online-Kartendaten); **Geschäftstyp pro Artikel** (Supermarkt,
    Drogerie, Baumarkt); Verweildauer ca. 1–2 min, damit Vorbeifahren nicht
    auslöst; nur bei nicht leerer Liste für diesen Typ.
  - E3 Abhaken im Geschäft schnell, auch auf der Uhr.
  - E4 **Teilen mit Haushaltsmitgliedern.** Eine Einkaufsliste braucht Abgleich
    **aus der Ferne** (einer im Geschäft, einer zuhause) – die lokale
    Synchronisation aus 7.18 reicht dafür wohl nicht. Kandidat: Home Assistant als
    Sync-Medium (Einkaufs-/To-do-Listen, Zugang von unterwegs per VPN). Alternativ
    ein eigenes Backend, falls es bis dahin wirklich nötig und sinnvoll ist.
    Entscheidung erst in V3.
- **Keine Anbindung an externe Listen-Apps** (z. B. Bring!, Google Tasks) – der
  Datenschutz wäre dann nicht mehr gewährleistet.
- Inventardaten sind sensibel (was steht wo im Haus) → gleiche Regeln wie 8.4.

### 7.24 Verbesserung vorschlagen
Bei der Nutzung fallen oft Kleinigkeiten auf, meist zur Bedienung und teils
wichtig. Bis zur nächsten Ausbaustufe sind sie wieder vergessen. Die App soll sie
deshalb dort festhalten, wo sie auffallen. **[V1.2]**
- VV1 **Von überall erreichbar**: Jeder Bildschirm hat den Eintrag *Verbesserung
  vorschlagen*, auch der Alarm (dort unauffällig, damit er nicht vom Reagieren
  ablenkt). Er öffnet einen kurzen Dialog.
- VV2 **Schnell erfasst**: ein Textfeld (Diktieren über die Tastatur reicht), dazu
  optional **wichtig**. Nach dem Speichern ist man sofort wieder dort, wo man war.
  Das Erfassen soll nicht länger als ca. 10 Sekunden dauern.
- VV3 **Kontext automatisch**: Gespeichert werden auch Zeitpunkt, App-Version und
  der Bildschirm, von dem aus der Vorschlag kam (bei Alarm und Bearbeiten auch
  welcher Reminder). Man muss die Situation dann nicht beschreiben.
- VV4 **Liste der Vorschläge** in den Einstellungen: wichtige zuerst, dann die
  neuesten. Vorschläge lassen sich bearbeiten, als *umgesetzt* oder *verworfen*
  markieren und löschen.
- VV5 **Mitnehmen in die Planung**: *Alle offenen kopieren* bzw. *Teilen* als
  einfacher Text (Markdown-Liste mit Kontext). Kevin entscheidet selbst, wohin der
  Text geht.
- VV6 Die Vorschläge bleiben **lokal** in der App-Datenbank (wie 8.4) und sind im
  Backup vor Migrationen enthalten. Es gibt keinen automatischen Versand.

### 7.25 Aus den Vorschlägen (2026-10-31)
Kevins erste Vorschläge aus der App. Sofort umgesetzt (V1.2b): Uhrzeiten in den
Einstellungen frei wählbar, eigene Werte neben der Vorauswahl (*andere…*), Schalter
mit gut sichtbarem Aus-Zustand, Einstellungen von allen Tabs erreichbar, Bearbeiten in
klappbaren Bereichen, Charakter oben mit *Dokumentation* als Standard. Offen:
- VS1 **Mehrmals täglich**: ein Reminder mit mehreren Uhrzeiten am Tag (z. B. 8, 13,
  19 Uhr) statt mehrerer Reminder, mit einer gemeinsamen Statistik. Ein Tag zählt für
  die Serie, sobald **mindestens ein** Termin eingehalten wurde (Standard; strenger nur,
  falls es sich in der Nutzung als nötig zeigt). Im Design zu klären: wie das
  Wochenziel zählt und ob Auslassen pro Termin gilt. **[V1.3]**
  **Mit Checkliste (2026-11-03):** Die Liste wird über die Termine abgearbeitet, pro
  Termin ein Punkt. Ein abgehakter Punkt erfüllt den Termin, der Rest kommt beim
  nächsten Termin (heute oder morgen). Ein Termin ohne Punkt ist *verpasst*, die Liste
  behält ihren Stand; nach dem letzten Punkt beginnt sie von vorn. Im Alarm ist der
  große Knopf „Erledigt: ‹nächster Punkt›“, *Alles auf einmal erledigt* ist klein.
  Das Wochenlimit für das *Minimum* zählt Tage, nicht Termine.
- VS2 **Andere Zeiten am Wochenende**: Übersicht, *laut ab* und Tagesgrenze getrennt
  für Mo–Fr und Sa–So einstellbar. Einzelne Tage erst, wenn zwei Gruppen nicht reichen.
  **[V1.3]**
- VS3 **Statistik schöner**: Wochenverlauf als kleines Balkendiagramm, Serie
  hervorgehoben. Die Liste reicht vorerst. **[später]**

### 7.26 Schnittstelle für KI-Agenten
Persönliche KI-Agenten werden voraussichtlich immer mehr übernehmen. Die App soll
deshalb vollständig über eine Schnittstelle für Agenten bedienbar sein, und ein Agent
soll helfen, die Reminder einzuhalten: planen, Vorarbeit erledigen, motivieren.
**[V2]**
- AG1 **Nur lokal auf dem Gerät.** Kein Zugang aus dem Netz, kein Backend.
- AG2 **Für alle persönlichen Agenten**, nicht nur einen (z. B. Gemini und die
  KI-Assistenten-Apps anderer Anbieter). Deshalb über offene bzw. System-Standards:
  Android *App Functions* (vom System geregelter Zugriff) und ein lokaler MCP-Server.
  Beide nutzen dieselben Befehle aus `core` wie die Oberfläche der App.
- AG3 **Freiwillig, Standard aus.** Wer seine Reminder komplett selbst verwalten
  will, braucht keinen Agenten. Freigabe **pro Agent** (Kopplung mit Bestätigung in
  der App), jederzeit widerrufbar, dazu ein Notschalter für die ganze Schnittstelle.
  Ein localhost-Port ist für jede App auf dem Gerät erreichbar, darum ist die
  Kopplung Pflicht. Wie die Freigabe im Detail aussieht, wird im Design geklärt.
- AG4 **Rechte in Stufen:** lesen (Reminder, Stand, Statistik, Übersicht);
  vorschlagen (anlegen, verschieben, erster Schritt – der Nutzer bestätigt in der
  App); ändern (Reminder anlegen und bearbeiten). Der Nutzer wählt die Stufe pro
  Agent.
- AG5 **Kein Schlupfloch** (Leitprinzip 4): Ein Agent darf keinen Reminder als
  erledigt markieren, keinen Alarm und keine Eskalation beenden und keinen Nachweis
  ersetzen.
- AG6 **Delegierbar** als Markierung pro Reminder: Nur dann darf ein Agent ihn
  erledigen, mit einem Beleg als Nachweis. Beispiel: Termin vereinbaren oder ein
  Meeting vorbereiten ja, Training nein. Beim Training kann der Agent aber den
  Trainingstag planen, den Fortschritt verfolgen und motivieren.
- AG7 **Ehrlich sichtbar** (Leitprinzip 6): Was ein Agent tut, steht im Verlauf als
  *durch Agent*. Vom Agenten Erledigtes wird in der Statistik getrennt ausgewiesen.
- AG8 **Coaching im Sinne der App:** Die Schnittstelle liefert neben den Daten auch
  Vorlagen (z. B. „Hilf mir, mit X anzufangen“, „Wochenrückblick“) mit den Regeln der
  App: positiv vor negativ, Minimum zählt, Joker, Warum (P3), Startklar-Fragen und
  Blocker (7.21). So motiviert jeder Agent auf dieselbe Art.
- AG9 **Die App stößt den Agenten an:** bei Eskalation, Verpasst, wiederholtem
  Verschieben und zum Abendrückblick. Dazu ein Knopf *Mit Agent besprechen* im Alarm
  und bei den Startklar-Fragen, der Reminder, Warum und Blocker mitgibt.
- AG10 **Deutlicher Datenschutzhinweis** bei der Freigabe: Was der Agent sieht, kann
  das Gerät verlassen; das verantwortet der Nutzer (wie I7). Sensible Kategorien
  aus 8.4 (Fotos, Audio, Standorte, Bewegungsdaten, Inventar) sind einzeln
  freizugeben und standardmäßig aus. Ein Zugriffsprotokoll zeigt, welcher Agent wann
  was gelesen oder geändert hat.
- Vorbereitung bis dahin: neue Logik weiter als saubere Befehle in `core`, damit
  Oberfläche und Agent später dieselben Wege nutzen.
- AG11 **Vorstufe für Claude über adb (vorgezogen, 2026-11-02, umgesetzt in V1.3a2):** Kevin möchte,
  dass Claude in der Entwicklung Reminder lesen und schreiben kann (z. B. Test-Reminder
  einrichten, Stand prüfen). Datenschutz ist für ihn als Entwickler kein Hindernis, die
  Schnittstelle muss aber **ausdrücklich in der App freigegeben** werden. Empfehlung:
  - **Weg:** `adb shell content call` an einen ContentProvider der App, geschützt mit
    der Berechtigung `android.permission.DUMP` – die hat nur die adb-Shell, keine andere
    App. Kein Netz, kein Port; geht per USB und kabellosem Debugging. Antworten als JSON
    mit Versionsnummer.
  - **Freigabe:** Schalter in den Einstellungen, Standard aus, mit Rückfrage. Solange er
    an ist, steht das sichtbar in den Einstellungen; ohne Freigabe antwortet die
    Schnittstelle nur „nicht freigegeben“.
  - **Lesen:** Reminder, offene Fälligkeiten, Verlauf, Statistik, Blocker; Einstellungen
    später.
  - **Schreiben:** Reminder anlegen, bearbeiten, pausieren, archivieren; Blocker anlegen.
    Immer über dieselben Befehle wie die Oberfläche (Prüfung, Alarme neu planen).
  - **Nicht (AG5):** nichts erledigen oder auslassen, keinen Alarm beenden, keine
    Nachweise, nicht löschen (archivieren statt löschen). Klingelt ein Reminder oder
    ist er überfällig, darf die Schnittstelle ihn nicht ändern, pausieren oder
    archivieren („läuft gerade – nur in der App“, 2026-11-03).
  - **Protokoll:** jeder Zugriff im Diagnose-Log mit Methode und ID, ohne Inhalte.
  - **Nur im Debug-Build** (Sideload); der Play-Store-Release bekommt stattdessen die
    Agenten-Schnittstelle aus AG1–AG10.
  - Umsetzung: design.md 15. Einstellungen liest die Schnittstelle vorerst nicht; ein
    neuer Reminder ohne Angabe ist *Dokumentation* (*Überwindung* braucht die vier Startklar-Antworten).

### 7.27 Nutzung beobachten, Funktionen trimmen, Schlupflöcher bremsen
Stand 2026-11-02: Die App funktioniert im Alltag sehr gut. Mit V1.2c ist aber viel
dazugekommen; Kevin sammelt jetzt erst einmal mit aktiver Nutzung.
- NT1 **Funktionen trimmen:** herausfinden, welche Funktionen wirklich genutzt werden.
  Was ungenutzt bleibt, wird vereinfacht oder entfernt, statt neue hinzuzufügen.
- NT2 **Zu streng geplant:** Reminder werden manchmal strenger angelegt als
  durchhaltbar und dann x-mal um 10 Minuten geschoben. Die App soll das erkennen und
  helfen, realistischer zu planen (Zeit, Strenge, Minimum), statt nur lauter zu werden.
  **Bestätigt (2026-11-02):** Wird ein Reminder über mehrere Fälligkeiten hintereinander
  jeweils mehrfach mit *Später* geschoben (etwa ab dem dritten Mal), fragt die App
  einmal, ob Uhrzeit, Strenge oder Minimum angepasst werden sollen – ähnlich der
  Nachfrage aus 7.21 (S8), keine harte Sperre. Die Karte bietet Uhrzeit, Strenge und
  *Nein danke*; *Minimum* braucht Kevin dort nicht (2026-11-03).
- NT3 **Schlupflöcher (Cheats) bleiben vorerst**, bekommen aber sinnvolle Rückfragen
  („Willst du wirklich …?“). Welche Stellen das sind und wie stark die Bremse sein
  soll, ergibt sich aus der Nutzung.
- Ergebnisse fließen als Vorschläge (7.24) oder hier ein; danach Entwurf.
- NT4 **Nutzung zählen erst vor V2**, zusammen mit der Testsession: nur Anzahlen aus der
  Datenbank (welche Funktionen eingestellt sind, wie oft Später, Verschieben, Minimum),
  keine Titel oder Inhalte. Bis dahin nutzt Kevin V1.2c weiter.

### 7.28 Play-Store-Release und Backend
Ziel ist, einmal den kompletten Weg zu gehen: eine offizielle App im Play Store mit
sehr kleiner Zielgruppe, mit Backend und vollem EU-Datenschutz. Lernen steht im
Vordergrund, nicht Reichweite.
- PS1 **Play Store** statt nur Sideload. **Android 17 bleibt Minimum** (8.1).
- PS2 **Backend** entsteht parallel in einem eigenen Repository (Entscheidung vom
  2026-11-01) und dient später als **Backup** und **Sync** mit anderen (7.18).
- PS3 **EU-Datenschutz (DSGVO)** vollständig: Datenschutzerklärung, Hosting in der EU,
  Export und Löschen auf Wunsch, Datensicherheits-Angaben im Play Store.
- PS4 Die eingeschränkten Berechtigungen, die beim Sideload egal waren (8.2), müssen
  für den Play Store begründet und deklariert werden.
- PS5 **Keine Eile (2026-11-02):** Die App soll erst „fertig“ sein – mindestens alles bis
  V2, eventuell Teile aus V2 und das Backend. Erst dann Play Store.
- PS6 **Mehr Transparenz** für Tester: Die App zeigt verständlich, was sie speichert,
  welche Berechtigungen sie wofür nutzt und warum ein Alarm kam bzw. nicht kam.
- PS7 **Bewusster Notausschalter:** ein klar erreichbarer Weg, alle Alarme sofort
  abzuschalten (z. B. wenn die App sich falsch verhält), mit Rückfrage und Anzeige, dass
  er aktiv ist. Wichtig, weil die Mechanismen absichtlich invasiv sind. Neutral
  (*ausgelassen – Notaus*) nur, wenn die Pause vor der Fälligkeit begann, sonst
  *verpasst*; Fristen bleiben hart (2026-11-03). Endet die Pause, ob von selbst oder von
  Hand, klingelt eine offene Fälligkeit sofort (2026-11-03).
  - Genauer (2026-11-05): Ein Termin bleibt unter Notaus **offen** bis zur Tagesgrenze
    (bzw. bis zum nächsten Termin). Erst dann wird entschieden: Pause noch aktiv und vor
    der Fälligkeit begonnen → *ausgelassen – Notaus*, neutral für die Serie. Sonst
    *verpasst* wie jeder andere verpasste Termin (Serie und Joker wie in 7.7).
  - „Fristen bleiben hart“: Eine abgelaufene Frist ist mit oder ohne Notaus *verpasst*
    und bleibt *überfällig* (7.2). Der Notaus macht sie nicht neutral.
- PS8 **Backend nur nach ausdrücklichem Einschalten.** Ohne Zustimmung keine Anmeldung,
  keine Verbindung; die App bleibt vollständig lokal nutzbar.
- PS8a **Einstellungen im Backup** (2026-11-03): Das Backup B2b enthält neben der
  Datenbank die Einstellungen (Darstellung, Alarmzeiten, Wochenende, Übersicht,
  Nachfrage, Verweildauer, Sortierung). *Notaus* und der Schalter der Schnittstelle
  bleiben auf dem Gerät: Eine Wiederherstellung schaltet keine Alarme stumm und keine
  Schnittstelle ein.
- PS9 **Verschlüsselung auf dem Gerät** (nur die App kann die Daten lesen): **geklärt
  (2026-11-02)** – Android-Sandbox, dateibasierte Verschlüsselung und `allowBackup=false`
  reichen; der Play-Store-Release ist ein Release-Build (nicht debuggable, kein
  `run-as`). Kein SQLCipher, solange kein konkreter Fall übrig bleibt.
- Backend-Stand und was es für die App bedeutet: [backend.md](backend.md).

### 7.29 Ankunft im Zeitraum mit WLAN (2026-11-04, ohne GPS seit 2026-11-05)
**Anlass:** Am 2026-11-04 klingelte *Training* (19:30, Ankunft zuhause, sonst entfällt)
um 07:12, weil der Geofence morgens eine Ankunft meldete. Eine früheste Uhrzeit gibt es
bisher nicht (T2b). Abends klingelte der auf 20:30 verschobene Termin auf dem Heimweg:
Die grobe Ortsabfrage ergab vermutlich „unklar“, und dann klingelt die App. Die Ankunft
wurde erst um 20:34 gemeldet. Ziel: **Man wird zuverlässig ausgelöst, wenn man ankommt**
(mit Puffer), und nicht, solange man noch nicht da ist.

**Einfach anfangen (Kevin, 2026-11-05):** Den Ort bestimmt zuerst **nur das WLAN**. Für
Zuhause und Arbeit reicht das, und auch viele andere Orte haben ein eindeutiges WLAN. Die
genaue Ortsprüfung per GPS ist nach **7.30** ausgelagert und kommt später.

- OZ1 **Ankunftszeitraum pro Reminder:** Statt nur einer Uhrzeit gibt es einen Zeitraum
  mit einer **spätesten Uhrzeit** und einem **Vorlauf**. Beispiel Training: spätestens
  19:30, Vorlauf 90 min, also Zeitraum 18:00–19:30.
  - Ankunft im Zeitraum → der Reminder klingelt bei Ankunft (plus Puffer, OZ4).
  - Ankunft vor dem Zeitraum (z. B. morgens) zählt nicht als Ankunft. Das
    Diagnose-Log vermerkt sie als „zu früh“.
  - Ist man beim Beginn des Zeitraums schon da (Homeoffice), klingelt er zur
    spätesten Uhrzeit wie bisher (5.1, F2).
  - Zur spätesten Uhrzeit klar nicht da → entfällt für heute, neutral (5.1, F1).
  - Im Zeitraum angekommen, wieder weg und wieder da (2026-11-05): Es zählt die erste
    erkannte Ankunft. Hat der Reminder schon geklungen, läuft er normal weiter;
    sonst klingelt er bei der nächsten Ankunft im Zeitraum.
- OZ2 **Prüfung im Zeitraum (nur WLAN):** Innerhalb des Zeitraums prüft die App den Ort
  alle **15–30 min** (pro Reminder einstellbar, Standard 20 min), dazu beim Beginn und zur
  spätesten Uhrzeit. Eine Prüfung liest nur den WLAN-Zustand (OZ3) und kostet praktisch
  keinen Strom. Sie fängt den Fall ab, dass Android eine Verbindung nicht meldet.
  - Mehrere Reminder am selben Ort teilen sich eine Prüfung. Haben sie verschiedene
    Prüfabstände, gilt der **kürzeste** (2026-11-05).
  - Ergebnis *da* → Auslösen (mit Puffer). *Klar weg* → nächste Prüfung. *Unklar* →
    nächste Prüfung. Ist es zur spätesten Uhrzeit immer noch unklar, gilt der Rückfall
    wie bisher (Geofence-Stand und grobe Ortsabfrage, design.md 6); bleibt es dann
    unklar, klingelt er, damit nichts verloren geht.
- OZ3 **WLAN als Ortssignal:** Jeder Ort kann ein oder mehrere WLANs haben (beim
  Festlegen des Orts übernehmen: „mit diesem WLAN verbunden“).
  - Gemerkt wird die **Kennung der Zugangspunkte** (BSSID), nicht nur der Name: Namen
    wie „FRITZ!Box“ gibt es oft mehrfach. Bei Mesh oder 2,4/5 GHz übernimmt die App alle
    Zugangspunkte desselben Netzes, die sie beim Festlegen sieht. Der Name dient nur
    zur Anzeige.
  - **Da:** mit einem WLAN des Orts verbunden. Android meldet das Verbinden von selbst,
    auch wenn die App nicht läuft. Im Zeitraum zählt es sofort als Ankunft, ohne auf die
    nächste Prüfung zu warten.
  - **Klar weg:** nicht verbunden, und eine aktuelle WLAN-Suche (höchstens 10 min alt)
    zeigt **kein** WLAN des Orts. Die Suche macht Android selbst, auch bei
    ausgeschaltetem WLAN, solange die WLAN-Suche in den Standort-Einstellungen an ist.
  - **Unklar:** nicht verbunden und keine aktuelle Suche, oder ein WLAN des Orts ist in
    Reichweite, ohne dass sich das Handy verbindet.
  - Der Geofence bleibt zusätzlich bestehen (Ankunft ohne WLAN), mit Verweildauer (T2).
- OZ4 **Puffer nach der Ankunft:** Zwischen erkannter Ankunft und Alarm liegt ein
  Puffer, damit man erst reinkommt (Standard 5 min, pro Reminder 0–30 min). Bei einer
  Ankunft kurz vor der spätesten Uhrzeit verschiebt der Puffer den Alarm auch über sie
  hinaus.
  - Genauer (2026-11-05): **Alarm = erkannte Ankunft + Puffer.** Erkannt heißt: WLAN
    des Orts verbunden oder Geofence-Meldung nach der Verweildauer (T2).
    Ist man im Puffer kurz wieder weg (Auto umparken), bricht das den Alarm nicht ab.
- OZ5 **Verschoben oder geschlummert behält die Ortsbedingung:** Zur neuen Zeit gilt
  dieselbe Prüfung wie zur spätesten Uhrzeit. Ist man noch nicht da, klingelt der
  Termin bei Ankunft, spätestens bis zum Tagesende; danach zählt er wie ein nicht
  erledigter Termin. Wer verschiebt, hat sich für heute entschieden.
  - Genauer (2026-11-05, Review-Lauf 3, Frage 1): Hat der Termin schon geklungen, gilt
    zur neuen Zeit: *da* → klingelt; *klar weg* **oder** *unklar* → er wartet auf die
    Ankunft (Prüfungen im Prüfabstand) und klingelt dann; ohne Ankunft ist er zur
    Tagesgrenze **verpasst**, nie *entfällt*. Damit klingelt *unklar* nicht mehr auf
    dem Heimweg.
  - Umgesetzt in V1.3b0.2 für *klar weg* und *unklar* (Warten auf die Ankunft,
    *verpasst* zur Tagesgrenze); die Prüfungen im Prüfabstand kommen mit 7.29.
- OZ6 **Weitere Orte:** Orte sind allgemein (Zuhause, Arbeit, Fitnessstudio, später
  Geschäfte, 7.23 E2), jeder mit Mittelpunkt, Radius und WLANs. Ein Reminder wählt
  seinen Ort. Für den Anfang reicht die Oberfläche für Zuhause plus ein weiterer Ort.
  Ein Ort ohne WLAN funktioniert weiter wie bisher über den Geofence.
- OZ7 **Strom:** Kein GPS (7.30). WLAN-Zustand und Ergebnisse der WLAN-Suche zu lesen
  kostet nichts; die App startet keine eigene Suche.
- OZ8 **Nachvollziehbar:** Das Diagnose-Log schreibt jede Prüfung mit Quelle (WLAN
  verbunden, WLAN-Suche, Geofence, Rückfall), Ergebnis (da, weg, unklar), Alter der Suche
  und Grund („zu früh“, „Puffer bis 18:47“), ohne Koordinaten, ohne WLAN-Namen und ohne
  Kennungen. Später zeigt die App das verständlich an (7.28 PS6: warum es klingelte
  oder nicht).

- OZ9 **Gilt für** (2026-11-05, Review-Lauf 3, Frage 3):
  - Ankunft **mit Uhrzeit** (Training): alles aus 7.29.
  - *Wenn ich zuhause bin* (ohne Uhrzeit): WLAN zählt zusätzlich als Ankunft (OZ3),
    keine Prüfungen und kein Zeitraum.
  - Die Option *trotzdem klingeln* (statt *entfällt*, wenn man zur spätesten Uhrzeit
    nicht da ist) bleibt wie heute: Sie klingelt zur spätesten Uhrzeit, egal wo man ist.
    Der Zeitraum gilt auch dort für eine frühere Ankunft.
  - Bestehende Ankunfts-Reminder bekommen die Standardwerte aus F3 (Vorlauf 90 min,
    Puffer 5 min, Prüfabstand 20 min). Kevin bekommt bei der Umstellung einen Hinweis.

**Entschieden (2026-11-04, Kevin übernimmt die Vorschläge):**
- F1 Zur spätesten Uhrzeit nicht da: **entfällt** wie bisher, keine Nachfrist.
- F2 Schon zu Beginn des Zeitraums da: klingelt zur **spätesten Uhrzeit** (wie 5.1).
- F3 Standardwerte: Puffer 5 min, Prüfabstand 20 min, Vorlauf 90 min.

Kevin hat die Anforderungen am 2026-11-05 im Ganzen gelesen; 7.29 ist damit für das
Design freigegeben.

### 7.30 Genaue Ortsprüfung per GPS [später] (aus 7.29 ausgelagert, 2026-11-05)
Für Orte ohne eigenes WLAN (Fitnessstudio, Geschäfte, unterwegs) und als zweite Meinung,
wenn das WLAN *unklar* sagt. Kommt erst, wenn 7.29 im Alltag läuft und solche Orte
gebraucht werden; Zuhause und Arbeit brauchen es voraussichtlich nicht.
- GP1 **Nur im Zeitraum:** GPS mit hoher Genauigkeit nur innerhalb eines
  Ankunftszeitraums (7.29 OZ1) und nur, wenn das WLAN nichts Eindeutiges sagt.
  Außerhalb kein GPS.
- GP2 **Messung mit Genauigkeit:** pro Prüfung mehrere Fixes über höchstens 30 s,
  Abbruch, sobald die Genauigkeit 20 m oder besser ist. Bewertung: *da*, wenn Entfernung
  plus Genauigkeit innerhalb des Radius liegt; *klar weg*, wenn Entfernung minus
  Genauigkeit außerhalb liegt; sonst *unklar*.
- GP3 **Erst messen:** Vor dem Einsatz eine Ansicht *Orte prüfen* im Debug-Build
  (Entfernung, Genauigkeit, Quelle und WLAN-Treffer pro Ort) und ein paar Tage Messung
  im Alltag: Wie genau ist es drinnen, wie lange dauert ein Fix im Hintergrund?
- GP4 **Hintergrund:** Reicht ein exakter Alarm nicht für einen frischen Fix, ein kurzer
  Vordergrunddienst vom Typ Standort, der nach dem Fix endet.
- GP5 **Strom:** höchstens eine Prüfung pro Ort und Intervall, jede mit Zeitlimit; die
  Diagnose zählt die Fixes pro Tag.
- GP6 **Datenschutz:** kein Bewegungsprofil. Gespeichert werden nur die festgelegten
  Orte und Ergebnisse der Prüfungen (Entfernung, Genauigkeit, Quelle, Zeit), keine
  Koordinaten der Messungen.

## 8. Zielplattform und Technik

### 8.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V1.3 über Benachrichtigungen, eigene Watch-App in V1.4 (siehe 7.14)
- **Entscheidung:** minSdk = targetSdk = 37. Kein Rückwärts-Support nötig, da
  absehbar nur ein Nutzer. Moderne APIs und Energiespar-Mechanismen nutzen.

### 8.2 Technische Machbarkeit

Bisher **Sideload statt Play Store** → die meisten Play-Richtlinien
(eingeschränkte Berechtigungen) gelten nicht. Mit dem geplanten Play-Store-Release
(7.28) ändert sich das. Berechtigungen müssen aber trotzdem
vom Nutzer in den Systemeinstellungen erteilt werden.

| Mechanismus | Machbar? | Android-Mittel | Anmerkung |
|---|---|---|---|
| T1 Zeit, exakt | ✅ | `AlarmManager.setAlarmClock()` | Umgeht Doze; exakte Alarme manuell erlauben |
| T2 Geofence | ✅ (mit Latenz) | Play Services Geofencing (Dwell-Übergang für Verweildauer) | Hintergrund-Standort nötig; Auslösung oft 1–5 min verzögert – laut Nutzer akzeptabel; Spike: grob, Vorbeifahren kann auslösen |
| T2b "zur spätesten Uhrzeit am Ort?" | ✅ | Alarm + aktueller Geofence-Status / Standortabfrage | |
| T3 WLAN/BT/Laden | ✅ teilweise | Broadcasts, `ConnectivityManager` Callbacks | Im Hintergrund nur mit Foreground-Service zuverlässig |
| 7.29 WLAN als Ort | ✅ | `NetworkCallback` mit Standort-Info (WLAN-Name und BSSID brauchen genauen Standort, Standortdienst an); bei jeder Prüfung den aktuellen WLAN-Zustand und die letzten Suchergebnisse (`WifiManager.getScanResults`) lesen, ohne eigene Suche | Wechsel im Hintergrund nicht garantiert sofort, deshalb zusätzlich die Prüfungen im Zeitraum |
| 7.30 GPS im Zeitraum [später] | ✅ | exakte Alarme alle 15–30 min, `getCurrentLocation` mit hoher Genauigkeit und Zeitlimit; Hintergrund-Standort ist erteilt | Fix drinnen oft langsam; Empfänger hat ~10 s, sonst kurzer Vordergrund-Dienst (Typ Standort) |
| T4 Kalender | ✅ | `CalendarContract` | Leseberechtigung |
| T5 App geöffnet | ⚠️ | `UsageStatsManager` / Accessibility-Service | Funktioniert, aber akkuintensiv bzw. heikle Berechtigung |
| W1/W2 Vollbild, DND-Bypass | ✅ | Full-Screen-Intent, Alarm-Audiostream | Full-Screen-Intent manuell erlauben |
| "Nicht stören" erkennen | ✅ | `NotificationManager.getCurrentInterruptionFilter()`, keine Berechtigung nötig; Zeitplan selbst nicht lesbar | Bypass braucht Freigabe der App als Ausnahme in "Nicht stören" (prüfbar über den Kanal bzw. Policy-Zugriff) |
| W3 Overlay | ✅ | `SYSTEM_ALERT_WINDOW` | manuell erlauben |
| W4 Galaxy Watch | ✅ | Notifications spiegeln sich automatisch (inkl. Aktionen); laufende Sitzung als Ongoing Activity; eigene Wear-OS-App mit Compose for Wear OS, Tiles, Data Layer API | V1.3 Benachrichtigungen (Erledigt und +10 min kommen bei "locker" schon an, 2026-10-25; "normal"/"streng" als Vollbild-Alarm noch nicht → G1b, V1.1), V1.4 eigene App |
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
| AG KI-Agenten | ⚠️ zu prüfen | Android App Functions (Jetpack `androidx.appfunctions`), lokaler MCP-Server mit Kopplung | Welche Agenten App Functions aufrufen dürfen und wie Agenten-Apps einen lokalen MCP-Server erreichen, auf Android 17 prüfen |
| P2 Apps sperren | ✅ / ⚠️ | Accessibility-Service oder Overlay-Blocker | |
| Zuverlässigkeit | ⚠️ | Akku-Optimierung deaktivieren | **Samsung "Schlafende Apps"/Geräteschutz ist das größte Risiko** |

**Fazit:** Alle V1-Kandidaten sind lokal umsetzbar. Hauptrisiken: Samsungs
Akkumanagement und die Zuverlässigkeit des Geofence "Ankunft zuhause" – beides
früh am S24 Ultra testen (Spike, siehe Abschnitt 10).

### 8.3 Tech-Stack (Vorschlag)
- Kotlin, Jetpack Compose, Room (lokale DB), AlarmManager + WorkManager, Glance (Widget)
- minSdk/targetSdk 37 (Android 17); AGP-Version mit API-37-Unterstützung nötig
- Build: Gradle (Wrapper), Entwicklung in Android Studio oder VS Code
- Entwicklungsumgebung: siehe
  [entwicklungsumgebung.md](entwicklungsumgebung.md)

### 8.4 Daten
- Alle Daten inkl. Fotos und Audio **nur lokal** auf dem Gerät.
- **Sensible Daten** (Fotos, Audio, Standorte, Bewegungsdaten) bleiben grundsätzlich
  **auf dem Gerät**. Gespeicherte Orte und WLANs verlassen es nur **Ende-zu-Ende
  verschlüsselt** im Backup B2b (2026-11-05); Fotos, Audio und Bewegungsdaten gar nicht.
  Synchronisation mit anderen Geräten im Haushalt (7.18) nur für ausdrücklich geteilte
  Inhalte.
- Inventar (7.23) zählt zu den sensiblen Daten. Was außerhalb der App mit Fotos und
  Cloud-KI für den Import vorbereitet wird, verantwortet der Nutzer selbst.
- Rohdaten der Bewegungssensoren (7.16) nach einer einstellbaren Frist löschen;
  das Ergebnis (Dauer, erkanntes Muster) bleibt.
- Kein **Nutzer-Backup** in V1 (später nachrüstbar, z. B. Export als ZIP). Die Kopie
  der Datenbank vor jeder Migration (design.md 13) ist nur eine Sicherung gegen
  Datenverlust beim Update, kein Backup. Das Cloud-Backup des
  Backends (B2b, [backend.md](backend.md)) ist Ende-zu-Ende verschlüsselt, enthält
  keine Fotos und kein Audio und läuft nur nach ausdrücklichem Einschalten (7.28 PS8).
- Aufbewahrung: vorerst unbegrenzt.

## 9. Entscheidungen

Neueste zuerst. Kap. 7 enthält immer den gültigen Stand; diese Tabelle ist die Historie. Überholte Einträge sind mit „→ ersetzt“ bzw. „→ präzisiert“ markiert.

| Datum | Entscheidung |
|---|---|
| 2026-11-05 | 7.29 einfach anfangen: Ort **nur über WLAN** (Verbindung und Ergebnisse der WLAN-Suche, BSSIDs pro Ort), Prüfungen im Zeitraum lesen nur den WLAN-Zustand, sonst Rückfall wie bisher. Genaue Ortsprüfung per GPS nach **7.30** ausgelagert, später und erst nach einer Messung. Kevin hat die Anforderungen im Ganzen gelesen |
| 2026-11-05 | Review-Lauf 3, Funde B1/B2 und Empfehlungen E5/E6: Ein Termin merkt sich, ob er schon geklungen hat. Speichern beim Schlummern verschiebt den heutigen Termin nicht mehr auf morgen; *Fortsetzen* meldet keine Verpasser. Ortsprüfung außerhalb der Transaktion, Alarme erst nach dem Speichern. Umgesetzt in V1.3b0.2 |
| 2026-11-05 | Review-Lauf 3, Fragen 1–3 (7.29): Verschoben/geschlummert und *weg* oder *unklar* → wartet auf die Ankunft, zur Tagesgrenze *verpasst*, nie *entfällt* (OZ5, umgesetzt in V1.3b0.2). Alarm = erkannte Ankunft + Puffer, kurz weg im Puffer bricht nicht ab (OZ4). Geltungsbereich OZ9: Ankunft mit Uhrzeit ganz; *Wenn ich zuhause bin* nur WLAN; *trotzdem klingeln* bleibt; Bestand bekommt die Standardwerte. Erste Ankunft im Zeitraum zählt; kürzester Prüfabstand gilt |
| 2026-11-05 | Review-Lauf 3, Fragen 4–5: ein Vollbild zur Zeit, der nächste wartet mit *+1 wartet* und eskaliert weiter (7.3); Knöpfe der Benachrichtigung je Art (7.4). Geplant mit V1.3b |
| 2026-11-05 | Review-Lauf 3, Fragen 6–7: Frist nach Ablauf *verpasst* und *überfällig*, auch unter Notaus („hart“ = nicht neutral, 7.2, PS7). Orte und WLANs verlassen das Gerät nur Ende-zu-Ende verschlüsselt im Backup B2b; das Migrations-Backup ist kein Nutzer-Backup (8.4) |
| 2026-11-05 | Review-Lauf 3, Klarstellungen: *nicht erfolgreich* nur bei ausdrücklichem Abschluss ohne Nachweis, sonst *verpasst* (7.5); Notaus entscheidet zur Tagesgrenze (PS7); *laut ab* nur bei *streng* (7.3); Ortsnachweis N4 auf später; laufende Sitzung wird nicht verpasst (7.11 D7); Kap. 9 neu sortiert, Kap. 7 ist der gültige Stand |
| 2026-11-04 | Ankunft im Zeitraum (7.29) nach der Diagnose der Training-Fälle (morgens Geofence-Ankunft ohne früheste Zeit, abends grobe Ortsabfrage „unklar“): Ankunftszeitraum mit spätester Uhrzeit und Vorlauf, genaue Ortsprüfung (GPS) nur im Zeitraum alle 15–30 min (→ ersetzt 2026-11-05: erst nur WLAN, GPS in 7.30), WLAN als stärkstes und zusätzliches Ortssignal, Puffer nach der Ankunft, weitere Orte. F1–F3 wie vorgeschlagen entschieden (entfällt ohne Nachfrist, schon da → späteste Uhrzeit, 5/20/90 min); Umsetzung erst nach Kevins Gesamtdurchsicht der Anforderungen |
| 2026-11-04 | Testdaten aus Kevins Daten: kein JSON-Import/Export in der App, sondern ein Skript über adb (`tools/testdata/export_testdata.py`), das die Tabellen als JSON schreibt, alle freien Texte ersetzt, Medien, Vorschläge und echte Koordinaten weglässt und auf Reste der Originaltexte prüft. Ergebnis in `testdata/`, keine SQLite-Datei im Repo |
| 2026-11-04 | Tests: Szenario-Tests ganzer Tage in `core` und Verbindungs-Tests der Receiver mit Robolectric, zunächst ohne Emulator. Kevins Datenbank darf als Testdaten ins Repo, aber nur mit pseudonymisierten Texten (Titel, Ziele, Notizen, Nachweise, Orte); umgesetzt mit Kevin am Abend |
| 2026-11-04 | Kevins Rückmeldungen nach V1.3b0: Checklisten-Punkte jederzeit abhaken (C8), lesbare Punkte im Alarm, Übersicht nennt jede Uhrzeit (O1), Textfelder wachsen mit und bieten Vorschläge der Tastatur. Umgesetzt in V1.3b0.1. Training-Alarm am Morgen und Checklisten-Speichern: Diagnose mit Kevins Szenario und Logs |
| 2026-11-03 | Umbau-Etappe V1.3b0 vor V1.3b (Review-Lauf 2): Engine als Fassade, Befehle in einer Transaktion, zentrales Klingeln, Alarm-Ansicht in `core`; ein gespeicherter Reminder *Wenn ich zuhause bin* klingelt sofort, wenn man schon da ist, auch über die Schnittstelle. Sonst kein neues Verhalten |
| 2026-11-03 | Einstellungen kommen mit ins Backup B2b, ohne *Notaus* und Schnittstellen-Schalter (Review-Lauf 2, Frage 2; 7.28 PS8a). Vorbereitet in V1.3b0 |
| 2026-11-03 | Notaus von Hand ausgeschaltet (Review-Frage 7b): eine offene Fälligkeit klingelt sofort wie beim automatischen Pausenende, nicht erst zur nächsten Runde (7.28 PS7). Umgesetzt in V1.3a3 |
| 2026-11-03 | Checkliste mit mehreren Uhrzeiten (7.25 VS1, Review-Frage 7a und 7c): pro Termin ein Punkt, der Rest kommt beim nächsten Termin, die Liste behält ihren Stand über verpasste Termine; Minimum-Limit zählt Tage. Umgesetzt in V1.3a3 |
| 2026-11-03 | Antworten auf Review-Lauf 1 (Fragen 1–6): Schnittstelle ändert keinen klingelnden oder überfälligen Reminder (AG11); nach „Handy aus“ klingelt der gerade laufende Termin (VS1); *Notaus* neutral nur bei Pause vor der Fälligkeit, Fristen hart (PS7); NT2-Karte ohne *Minimum*; Titel im Diagnose-Log in der Entwicklungsphase erlaubt, vor einem offiziellen Release entfernen. Umgesetzt in V1.3a3 |
| 2026-11-02 | AG11 bestätigt und als V1.3a2 umgesetzt: ContentProvider mit `DUMP`, nur Debug-Build, Schalter mit Rückfrage; Claude darf Reminder-Inhalte über die freigegebene Schnittstelle lesen, sie bleiben aber aus Repo und Logs |
| 2026-11-02 | Schnittstelle für Claude vorgezogen (vor V1.3b): Reminder lesen und schreiben über adb, ausdrücklich in der App freizugeben; Entwurf in 7.26 AG11 |
| 2026-11-02 | V1.3a umgesetzt: höchstens 6 Uhrzeiten am Tag; Notaus mit 1 Stunde, bis morgen 06:00 oder bis zum Einschalten; Wochenend-Schalter gilt für Alarmzeiten und Übersicht zusammen |
| 2026-11-02 | V1.3-Entwurf bestätigt (design.md E.5): Aufteilung a/b/c; mehrere Uhrzeiten – verpasst beim nächsten Termin, kein Wochenziel und keine Ankunft; NT2-Schwelle 3×2 (oder einmal 4×), Karte auf *Heute*, *Nein danke* 14 Tage; *Notaus* bricht keine Serie; für V1.3b: *abgebrochen* bricht die Serie, Nachfragen *locker*, max. 90 min. V1.3a gestartet |
| 2026-11-02 | Ziel offizieller Play-Store-Release für eine sehr kleine Zielgruppe, mit Backend (Backup, Sync) und vollem EU-Datenschutz; Android 17 bleibt Minimum (7.28) |
| 2026-11-02 | Verschlüsselung auf dem Gerät geklärt: Sandbox + Release-Build reichen, kein SQLCipher (7.28 PS9) |
| 2026-11-02 | Play Store ohne Eile: erst „fertig“ (mindestens bis V2, ggf. Backend); dafür Transparenz, Notausschalter, Backend nur nach Einschalten; Verschlüsselung auf dem Gerät wird bewertet (7.28 PS5–PS9) |
| 2026-11-02 | Hinweis bei wiederholtem *Später* bestätigt (7.27 NT2); Nutzung erst vor V2 mit der Testsession zählen (NT4) |
| 2026-11-02 | Vor V1.3 erst Nutzung beobachten: Funktionen trimmen statt erweitern, Rückfragen bei Schlupflöchern (7.27) |
| 2026-11-02 | V1.2c-Entwurf (design.md 12.4) bestätigt und umgesetzt: Fristen mit Ausführungsfenstern, Vorlagen, Vorab-Reminder, Blocker als Liste mit Vorbereitungsaufgaben, *Blocker gelöst* als Minimum, Nachfragen wenn es hakt |
| 2026-11-02 | Testsession erst vor V2 statt vor V1.3 – die App läuft im Alltag gut |
| 2026-11-01 | Schnittstelle für KI-Agenten in V2: nur lokal, für alle persönlichen Agenten, freiwillig mit Freigabe pro Agent; Agent erledigt nur als *delegierbar* markierte Reminder, nie Nachweis oder Alarm; App stößt den Agenten an; Datenschutz verantwortet der Nutzer, mit deutlichem Hinweis (7.26) |
| 2026-10-31 | Mehrmals täglich: Serie hält mit mindestens einem eingehaltenen Termin pro Tag. Wochenend-Zeiten gelten auch für die Tagesgrenze (7.25) |
| 2026-10-31 | V1.2b bestätigt wie Entwurf. UI-Feinschliff aus den ersten Vorschlägen noch vor der Testsession; mehrmals täglich und Wochenend-Zeiten nach V1.3 (7.25) |
| 2026-10-29 | Reminder-Liste: abgeschlossene Einmal-Reminder automatisch eingeklappt, Archivieren von Hand, Gruppen und Sortierung (V1.2). Einmal-Reminder mit neuer Zeit wird wieder eingeplant und eingeschaltet (Bugfix, sofort) |
| 2026-10-29 | V1.2 in drei Etappen: a Alltag (Vorschläge, Liste), b Ziele über die Zeit, c Fristen und Blocker; Vorab-Reminder (T6) in V1.2c |
| 2026-10-28 | "Verbesserung vorschlagen" in der App: von überall erreichbar, Kontext automatisch, lokal gespeichert, als Text teilbar (V1.2) |
| 2026-10-27 | Alarm verstummt, solange der Nachweis läuft (mit Frist, Standard 10 min); Text-Nachweis mit Mindestzeichen und Audio-Nachweis mit Mindestdauer, Nachweise kombinierbar – alles V1.1; Audio von der Uhr in V1.4 |
| 2026-10-27 | Spike abgeschlossen: 13/13 Alarme pünktlich, Update + Neustart überstanden, kein Problem mit Samsungs Akkumanagement. Ankunft zuhause in V1.1 erst nach Verweildauer werten (Geofence grob) |
| 2026-10-26 | Vollbild-Alarme ("normal", "streng") erscheinen nicht auf der Uhr → zusätzliche spiegelbare Benachrichtigung in V1.1 (G1b) |
| 2026-10-26 | Feste Ruhezeit (außerhalb 7–21 Uhr still) entfällt ab V1.1; stattdessen gilt "Nicht stören" des Handys. Pro Reminder "auch bei Nicht stören" für besondere Ereignisse (ersetzt die Aufsteh-Wecker-Ausnahme). Laut weiterhin ab 18 Uhr; verpasst ab Tagesgrenze, Standard 23 Uhr |
| 2026-10-26 | Verschiebe-Knöpfe pro Reminder (1–2, z. B. +10 oder +30/+60 min), V1.1; kurzes Verschieben ohne Limit, aber gezählt, nicht über die Tagesgrenze |
| 2026-10-25 | Mehrere Personen (V3) möglichst ohne Backend über lokale Synchronisation im Haushalt |
| 2026-10-25 | Sensible Daten grundsätzlich nur lokal auf dem Gerät |
| 2026-10-25 | GPS-Routen erst V2/V3 – punktuelle Ortserkennung reicht vorerst |
| 2026-10-25 | Bewegungsdaten von Handy und Uhr als Forschungsprojekt (messen, später auswerten), V2 |
| 2026-10-25 | Startklar-Fragen (Ziel, Hindernisse, Startbereitschaft, erster Schritt) Pflicht bei Überwindung, änderbar mit Verlauf (V1.1); Blocker als Vorbereitungsaufgaben, Nachfragen bei wiederholtem Verschieben, "Blocker gelöst" als Minimum (V1.2) |
| 2026-10-25 | Aufsteh-Wecker zweistufig (normal, dann nicht abschaltbar bis Nachweis), V2; einfachste Variante (Schritte) als PoC früher möglich, Home-Assistant-Varianten später |
| 2026-10-25 | Haushaltsinventar (grob, "zuletzt gesehen", Import aus externer KI-Erfassung) und Einkaufsliste mit Geschäfts-Trigger, V3 – vielleicht nie; keine Anbindung an externe Listen-Apps; geteilte Liste ggf. über Home Assistant, Backend nur wenn wirklich nötig – Entscheidung in V3 |
| 2026-10-25 | Reminder vorab erledigen (V1.1) und vorgezogen starten (V1.3); "vor dem Reminder erledigt" wird positiv ausgewiesen |
| 2026-10-25 | Raumerkennung über Bluetooth-Empfänger mit der Uhr als Sender, ergänzt um aktive Methoden (Bewegung, Präsenzmelder, Taster) |
| 2026-10-25 | Watch-App ist Fernbedienung und Anzeige, Logik bleibt auf dem Handy (Handy immer in der Nähe); laufende Reminder komplett über die Uhr bedienbar |
| 2026-10-25 | Leitprinzip 10: nicht ans Handy gebunden |
| 2026-10-25 | Eigene Watch-App in V1.4 – Bedienung laufender Reminder ohne Handy |
| 2026-10-25 | NFC-Kontrollpunkte nach V3 – zu unflexibel; stattdessen Raumerkennung per Bluetooth und Taster über Home Assistant, V2 (ggf. V3) |
| 2026-10-25 | ~~NFC-Kontrollpunkte als Nachweis im Haus, V1.3~~ → ersetzt: nach V3, Räume über Home Assistant (2026-10-25) |
| 2026-10-25 | Watch: Benachrichtigungen samt Aktionen kommen bereits an – kein eigener Spike nötig |
| 2026-10-25 | Training: nur Dauer und erledigte Schritte/Sätze; Wiederholungen/Gewicht bleiben V2 |
| 2026-10-25 | Dauer wird getrackt und mit früheren Sitzungen verglichen (schnell fertig werden soll sich lohnen) |
| 2026-10-25 | Maximale Dauer pro Sitzung; danach letzter Reminder so streng wie möglich, kurze Reaktionszeit, dann nicht erfüllt – mit erledigten Teilschritten zählt das Minimum |
| 2026-10-25 | Nachfragen in Sitzungen zeitbasiert (Standard alle 10 min), Strenge der Nachfragen einstellbar |
| 2026-10-25 | Restpunkte verschieben zählt nicht gegen das Limit, wenn am Tag mindestens ein Punkt erledigt wurde |
| 2026-10-25 | Checkliste = Block: Intervall ab dem ersten erledigten Punkt, nächste Runde mit allen Punkten |
| 2026-10-25 | Checklisten mit einzeln verschiebbaren Punkten |
| 2026-10-25 | Durchführung mit Timer ("Ich starte jetzt") und Nachfrage-Muster; Abläufe für geregeltes Training, begrenzt aufs Wichtigste – keine Trainings-App |
| 2026-10-25 | Galaxy Watch vorgezogen auf V1.3, zuerst über Benachrichtigungen |
| 2026-10-25 | Alles bis einschließlich V1.3 kommt vor V2 |
| 2026-10-24 | Strenge hochgestuft: *locker* = Banner + Vibration alle 10 min, *normal* = Vollbild wie Wecker ohne Ton |
| 2026-10-18 | V1 in Etappen V1.0 / V1.1 / V1.2 → erweitert um V1.3 und V1.4 (2026-10-25), Stand in 10.1 |
| 2026-10-18 | Wochen Mo–So; Rettungs-Erinnerung am Wochenende; Joker 1/Woche (täglich) bzw. 1/Monat (wöchentlich) |
| 2026-10-18 | Besprechungen: Schnellknopf statt Kalenderabgleich |
| 2026-10-18 | Widget für Ein-Klick-Erstellung; Strenge-Vorlagen in V1 (Startwerte siehe 7.9) |
| 2026-10-18 | Dokumentation per Freitext + Audio; Zahlenwerte später |
| 2026-10-18 | Auslassen zählt neutral; Serie hält bei Minimum; ein Aussetzer pro Woche/Monat erlaubt |
| 2026-10-18 | Reminder kennen Ausführungsfenster; Fristen erinnern häufig, "auf morgen" möglich |
| 2026-10-18 | Wiederkehrendes standardmäßig ab letzter Erledigung, kalendarisch optional |
| 2026-10-18 | Training: Tagesangebot (Ankunft / 19 Uhr / sonst entfällt) + Wochenziel 3–4×, nie eine Woche ohne → Zeitraum ab 2026-11-04 (7.29) |
| 2026-10-18 | Eskalation = Vollbild oder wiederholte Benachrichtigung bis Reaktion; **7–21 Uhr sichtbar** (Vollbild + Vibration), **18–21 Uhr laut** → ersetzt durch „Nicht stören“ und Tagesgrenze (2026-10-26) und *laut ab* (7.3) |
| 2026-10-18 | ~~Daten nur lokal, kein Backup in V1~~ → ersetzt: Backup B2b mit Backend, Ende-zu-Ende verschlüsselt (2026-11-02, 7.28; 8.4) |
| 2026-10-18 | Übersicht morgens und abends (einstellbar), inkl. Rückblick |
| 2026-10-18 | Motivations-Audio nur auf Klick |
| 2026-10-18 | Motivation über Statistik, Serien, Fortschritt (Woche/Monat/Jahr/gesamt); keine Belohnungssysteme |
| 2026-10-18 | Nachweisart pro Reminder; fehlender Nachweis = "nicht erfolgreich" → präzisiert 2026-11-05 (7.5) |
| 2026-10-18 | Minimum entscheidet der Nutzer; Limits für Minimum und Verschieben pro Reminder |
| 2026-10-18 | Feste, flexible und ortsbasierte Reminder in V1 |
| 2026-10-18 | KI später: teils lokal, teils über Backend |
| 2026-10-18 | ~~Galaxy Watch nicht in V1~~ → ersetzt: V1.3 (2026-10-25) |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |

## 10. Nächste Schritte

### 10.1 Was ist wo umgesetzt

| Version | auf dem Gerät | Inhalt (Anforderungen) |
|---|---|---|
| Spike | 2026-10 | Machbarkeit auf dem S24 Ultra ([spike.md](spike.md)) |
| V1.0 | 2026-10-24 | Zeit-Reminder, Eskalation, Aktionen, Quittieren/Foto, Ziel/Minimum, Verlauf, Widget, *nach Erledigung* |
| V1.1a | ✅ | Nicht stören und Tagesgrenze (7.3), Später-Knöpfe (7.4), Nachweis ohne Alarm, Text, Audio (7.5), vorab erledigen (7.20), Startklar (7.21 S1–S4), Uhr (7.14 G1b), Motivations-Audio |
| V1.1b | ✅ | Ankunft zuhause (7.1), Checklisten (7.13) |
| V1.2a | ✅ | Verbesserung vorschlagen (7.24), Reminder-Liste (7.9 L1–L5) |
| V1.2b | 2026-10-31 | Wochenziele, Serien/Joker, Statistik, Übersicht morgens/abends, Diagnose-Log, UI aus Kevins Vorschlägen |
| V1.2c | 2026-11-02 | Fristen mit Fenstern (7.2), Vorlagen, Vorab-Reminder (T6), Blocker als Aufgaben (7.21 S5–S8) |
| V1.3a | 2026-11-02 | Mehrmals täglich, Wochenend-Zeiten (7.25 VS1, VS2), Hinweis bei ständigem *Später* (7.27 NT2), Notausschalter (7.28 PS7) |
| V1.3a2 | 2026-11-02 | Schnittstelle für Claude über adb (7.26 AG11) |
| V1.3a3 | ✅ | Fixes aus Review-Lauf 1 und Antworten darauf, Checkliste über mehrere Uhrzeiten (7.25 VS1) (Kap. 9, 2026-11-03) |
| V1.3b0 | ✅ | Umbau ohne neues Verhalten (design.md 1), Empfehlungen E1–E4 aus Review-Lauf 2 (Kap. 9, 2026-11-03) |
| V1.3b0.1 | 2026-11-04 | Kevins Rückmeldungen: Checkliste jederzeit, Übersicht mit allen Uhrzeiten, Textfelder (Kap. 9, 2026-11-04) |
| V1.3b0.2 | gebaut | Termin merkt sich, ob er geklungen hat; Speichern beim Schlummern verschiebt nicht mehr auf morgen; verschobenes Training wartet auf die Ankunft (7.29 OZ5); Ortsprüfung vor der Transaktion (Kap. 9, 2026-11-05) |

Details zum Verhalten: [design.md](design.md), Gerätetests: [geraetetest.md](geraetetest.md).

### 10.2 Plan

1. ✅ **Technischer Spike auf dem S24 Ultra** (Plan: [spike.md](spike.md))
2. **V1 in Etappen**, damit früh echte Nutzung möglich ist (umgesetzt bis V1.3b0.2, siehe 10.1):
   - **V1.0 (benutzbar):** Zeit-Reminder, Eskalation, Aktionen, Quittieren/Foto,
     Ziel/Minimum, einfache Liste der Erledigungen, Widget
     – ✅ seit 2026-10-24 auf dem Gerät, inkl. Intervall ab letzter Erledigung
   - **V1.1:** Ort (Ankunft zuhause), **Checklisten** (7.13), Audio-Nachweis,
     Motivations-Audio, **vorab erledigen** (7.20), **Startklar-Fragen** (7.21, S1–S4),
     **Vollbild-Alarme auf der Uhr** (7.14, G1b), **"Nicht stören" statt Ruhezeiten**
     und Tagesgrenze (7.3), **Verschiebe-Knöpfe pro Reminder** (7.4),
     **Alarm aus während des Nachweises**, **Text-Nachweis**, kombinierbare
     Nachweise (7.5)
   - **V1.2** in drei Etappen:
     - **V1.2a:** **Verbesserung vorschlagen** (7.24), **Reminder-Liste aufräumen**
       (7.9, L1–L5)
     - **V1.2b:** Wochenziele, Serien/Joker, Statistik, Übersicht morgens/abends
     - **V1.2c:** Fristen mit Ausführungsfenstern, **Vorab-Reminder** (T6), **Blocker
       als Aufgaben** (7.21, S5–S8), Vorlagen
   - **Jetzt (seit 2026-11-02): Nutzung beobachten und trimmen** (7.27): welche
     Funktionen Kevin wirklich nutzt, Rückfragen bei Schlupflöchern, Hinweis bei
     wiederholtem *Später*. Fehler aus dem Alltag als Fix auf `develop`.
   - **Als Nächstes (2026-11-04): Ankunft im Zeitraum** mit WLAN (7.29); F1–F3 und
     OZ9 entschieden, Entwurf in design.md (Abschnitt *Entwurf 7.29*). Kevin hat die
     Anforderungen am 2026-11-05 gelesen; vor V1.3b. GPS (7.30) später.
   - **V1.3:** **Durchführung mit Timer** und Nachfrage-Muster (7.11), ein Vollbild
     zur Zeit (7.3), Knöpfe je Art (7.4), **Abläufe**
     für Training (7.12), **Galaxy Watch** über Benachrichtigungen (7.14),
     **vorgezogen starten** (7.20)
   - **V1.4:** **eigene Watch-App** für laufende Reminder (7.14, G5–G9)
   - **Kandidat zum Vorziehen:** Aufsteh-Wecker (7.22) als PoC mit Schritten –
     sinnvoll ab V1.4 (Schritte auf der Uhr), eine Handy-only-Variante auch früher.
   - **Möglicher Kandidat zum Vorziehen:** Einkaufsliste nur für einen selbst mit
     Geschäfts-Trigger (7.23, E1–E3) – ohne Inventar, KI und Abgleich; Technik
     ab V1.1 vorhanden. Nicht festgelegt.
3. **V2** (Kap. 4), danach erst der **Play-Store-Release** mit Backend für Backup und
   Sync, EU-Datenschutz (7.28 PS5, 2026-11-02).
4. Weitere Anforderungen ergeben sich aus der Nutzung.

## 11. Offene Fragen

Die Fragen aus Review-Lauf 3 (2026-11-05) sind entschieden (Kap. 9). Offen, aber
klein (Stand 2026-11-05):
- Vor V1.3 kurz prüfen, ob *Ich starte jetzt* und die Nachfrage-Antworten auf der Uhr
  genauso ankommen wie *Erledigt* / *+10 min*.
- Sommerzeit: Was passiert mit einer Uhrzeit zwischen 02:00 und 03:00 am
  Umstellungstag, und mit einer Tagesgrenze nach Mitternacht?
- Handy im Ankunftszeitraum aus und erst nach der spätesten Uhrzeit wieder an (7.29).
- *Mehrmals täglich* (7.25 VS1): Gilt *Auslassen* für einen Termin oder den ganzen Tag?
- Begriff „Pause“: Notaus, pausierter Reminder und Pause-Schritt im Ablauf (7.12)
  heißen gleich; in der Oberfläche bei Gelegenheit unterscheiden.
- Details (Oberfläche, Vorlagen, Vibrationsmuster, Standardwerte für Nachfrage-Intervall
  und Reaktionszeit) klären sich mit der Nutzung.

Für später:
- V2: Welche Home-Assistant-Lösung für Räume (Bermuda mit Bluetooth-Proxys oder
  ESPresense), wie viele Empfänger pro Wohnung, und wie schnell wird ein Raumwechsel
  der Uhr erkannt? Vorher kurzer Test mit 2–3 Empfängern.
- V2: Welche Kennzahlen machen ein Bewegungsmuster "gleich genug"? Klärt sich erst
  mit echten Messdaten (7.16).
- V3: Wie oft und worüber synchronisieren (Nearby Connections vs. Heim-WLAN), und
  welche Konfliktregel gilt bei gleichzeitigen Änderungen (7.18)?
- V3: Wie wird die geteilte Einkaufsliste aus der Ferne abgeglichen – Home
  Assistant oder eigenes Backend (7.23, E4)?
- Play Store (7.28): Welche Berechtigungen lassen sich dort begründen (Vollbild-Alarm,
  exakte Alarme, Standort im Hintergrund, Foreground-Service), und welche Testphase
  verlangt Google vor dem ersten Release? Vor dem Start an den aktuellen Richtlinien
  prüfen.
- Backend (7.28): Wie passen Backup und Sync zu 8.4 (sensible Daten nur auf dem Gerät)?
  Empfehlung: Backup Ende-zu-Ende verschlüsselt, sodass das Backend nur verschlüsselte
  Daten sieht. **Geklärt:** Das Backend ist genau so gebaut ([backend.md](backend.md)).
- ~~Verschlüsselung auf dem Gerät~~ – geklärt 2026-11-02 (7.28 PS9). Begründung: Android trennt App-Daten schon heute
  (Sandbox, dateibasierte Verschlüsselung, `allowBackup=false`). Lesbar sind sie nur
  über `run-as` in Debug-Builds und auf gerooteten Geräten. **Empfehlung:** Für den
  Play Store ein Release-Build (nicht debuggable) – das schließt `run-as` aus.
  Zusätzlich SQLCipher mit Schlüssel im Keystore nur, wenn ein konkreter Fall übrig
  bleibt; es kostet Leistung, erschwert Diagnose und Migrationen. Fotos und Audio
  lägen weiterhin als Dateien vor.
- V2: Wie genau funktioniert die Freigabe für Agenten (Kopplung, Stufen, sensible
  Kategorien), und welche Agenten-Apps erreichen App Functions bzw. einen lokalen
  MCP-Server tatsächlich (7.26)?
