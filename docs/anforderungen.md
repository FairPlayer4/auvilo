# Anforderungen – avlreminder

> Arbeitsname. AVL = **A**udio / **V**isual / **L**ocation.
> Was installiert und was offen ist: [stand.md](stand.md); Versionen in Kap. 10.1.
> Entscheidungen mit Datum in Kap. 9 (bis 2026-10-31 in
> [anforderungen-archiv.md](anforderungen-archiv.md)), technisches Design nach Themen in
> [design.md](design.md). Wird iterativ verfeinert.

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
| **V1** | Einzelnutzer, lokal, Kernmechanismen | Sideload-APK; Backend nur für das optionale Cloud-Backup (7.28, 8.4) |
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
- **Mehrere Reminder gleichzeitig fällig** (2026-11-05, verschoben auf V1.3c): Es gibt
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
  - N2a **Auslösen mit den Lautstärketasten** wie in der Kamera-App (2026-11-16):
    Leiser und Lauter lösen aus, solange die Kamera des Nachweises zu sehen ist;
    Gedrückthalten gibt ein Foto. Die Lautstärke ändert sich dabei nicht, auch nicht
    die des Alarmtons. Ohne Kamera wirken die Tasten wie gewohnt. Umgesetzt in V1.3c10.
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
- Z2b **Limit für Verschieben** pro Reminder einstellbar **[V1]**, wahlweise **pro Termin**
  (jeder neue Termin fängt bei 0 an), **pro Woche** oder **pro Monat** (2026-11-13, V1.3c1):
  - Woche und Monat sind Kalenderwoche (Mo–So) und Kalendermonat; es zählen alle
    Verschiebungen des Reminders in diesem Zeitraum, zum Zeitpunkt des Verschiebens. Mit der
    neuen Woche bzw. dem neuen Monat geht es wieder, auch für einen schon verschobenen Termin.
  - Es zählen *Verschieben* (auch vorab, 7.32 VB3) und *Rest auf morgen*, wenn es bisher
    zählte (ohne erledigten Punkt am selben Tag); *Später* bleibt ohne Limit.
  - Bestehende Reminder, Vorlagen und die Startwerte der Strenge zählen pro Termin.
  - Der Alarm zeigt, was bleibt („noch 1× diese Woche“), aufgebraucht ausgegraut mit
    „wieder ab Mo 02.11.“.

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
- **Widget**: ein Klick → direkt in die Reminder-Erstellung **[V1]**; dazu die
  Schnellerinnerung mit *+5 / +20 / +60* und Sprache (7.35)
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
  - *Teilschritt erreicht, beenden* → **Minimum** erreicht (zählt gegen das Limit für Minimum;
    ist es aufgebraucht, fehlt der Knopf, und die laufende Benachrichtigung sagt es)
  - *Abbruch ohne Minimalziel* → **nicht erfolgreich (abgebrochen)**, ehrlich in der Statistik.
    Aus Benachrichtigung oder Uhr ohne Rückfrage, aber 60 s zurücknehmbar wie *Erledigt*
    (7.31); die Sitzung läuft dann mit dem alten Start weiter (2026-11-12)
  - Für Wochenziel, Limit für Minimum und Statistik zählt eine Sitzung in der Woche (und
    am Tag) ihres **Starts**, wie im Verlauf (D7, 2026-11-12)
- D4 Ziel und Minimum können als **Dauer** angegeben werden ("45 min" / "10 min");
  die App schlägt dann die passende Antwort vor.
- D5 **Maximale Dauer** pro Reminder einstellbar. Ist der Reminder bis dahin nicht
  abgeschlossen, kommt ein **letzter Reminder, so streng wie zu der Uhrzeit möglich**
  (Vollbild, im lauten Fenster mit Alarmton) mit **einigen Minuten Reaktionszeit**
  (Standard 5 min). Danach gilt er als **nicht erfüllt** (*verpasst*) – außer es wurden
  schon Teilschritte abgeschlossen, dann zählt das **Minimum**. Unter Notaus kommt kein
  letzter Reminder; am Maximum endet die Sitzung als *abgebrochen*.
- D6 **Schnell und effizient fertig werden soll sich lohnen:** Die Dauer jeder
  Sitzung wird festgehalten und im Verlauf gezeigt, mit Vergleich zu früheren
  Sitzungen (z. B. "6 min schneller als im Schnitt"). Positive Verstärkung, kein Druck.
- Technik: Foreground-Service mit laufender Benachrichtigung (wie der Alarm),
  Nachfragen als exakte Alarme – überlebt Bildschirm aus und App im Hintergrund.
- D7 **Sitzung und andere Regeln** (2026-11-05, Review-Lauf 3; Technik in design.md 3.1):
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
  Backup vor Migrationen enthalten. Es gibt keinen automatischen Versand. Senden an den
  Entwickler kommt mit 7.36 (2026-11-18), nur mit Zustimmung je Sendung.

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
- NT4 **Nutzung zählen erst vor V2**, zusammen mit einer Testsession (wird vor V2 neu
  geschrieben): nur Anzahlen aus der Datenbank (welche Funktionen eingestellt sind, wie oft
  Später, Verschieben, Minimum), keine Titel oder Inhalte.

### 7.28 Play-Store-Release und Backend
Ziel ist, einmal den kompletten Weg zu gehen: eine offizielle App im Play Store mit
sehr kleiner Zielgruppe, mit Backend und vollem EU-Datenschutz. Lernen steht im
Vordergrund, nicht Reichweite.
- PS1 **Play Store** statt nur Sideload. Minimum ist **Android 14** (8.1; bis 2026-11-15
  Android 17).
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
- PS10 **Positionierung als Wecker und Timer (2026-11-15):** Die App ist im Kern ein
  Wecker (klingelt über den Alarm-Kanal, im Vollbild, bis man reagiert) und mit den
  Sitzungen (7.11) ein Timer; später kommt ein gemeinsamer Kalender dazu (7.18). So
  beschreibt sie auch der Store-Eintrag, nicht als To-do-App mit Erinnerungen.
  - Exakte Alarme: Ein zusätzlicher Einrichtungsschritt (`SCHEDULE_EXACT_ALARM`, vom
    Nutzer in *Alarme & Erinnerungen* erteilt) stört weder Kevin noch die Zielgruppe,
    solange die Kernfunktion nicht schlechter wird. Damit hängt der Store nicht an der
    Prüfung von `USE_EXACT_ALARM` (playstore.md P1).
  - Fehlt die Erlaubnis, muss die App klar sagen, dass sie dann nicht richtig funktioniert
    (Kernfunktion): auf *Heute* mit dem, was dann nicht geht (Alarme bis zu einer Stunde zu
    spät, kein Dauer-Alarm), ein Tippen führt direkt zum Schalter; plant die App ohne
    Erlaubnis, sagt es eine Benachrichtigung. Umgesetzt in V1.3c4.
- PS11 **Name „AuViLo-Reminder“ (2026-11-15):** **Au**dio-**Vi**suell-**Lo**kation, die
  Bedeutung von „AVL“ bleibt, das Zeichen der AVL List GmbH fällt weg (playstore.md N1).
  Kevin hat geprüft, dass der Name in der EU nicht eingetragen ist.
  - Jetzt nur der Anzeigename (App-Name im Launcher, Widget, Versionszeile in den
    Einstellungen), damit die Sideload-App per `adb install -r` weiterläuft. Umgesetzt in
    V1.3c5.
  - Vor einem Play-Store-Antrag der Rest: `applicationId`, Namespace und Paket
    `com.avlreminder`, Logo (zeigt „AVL“), Repo- und Dateinamen (playstore.md B7, N1).
  - Eine Marke (Wortmarke „AuViLo“) meldet Kevin wahrscheinlich an, wenn der Play Store
    wirklich kommt; die Namenssitzung empfiehlt dafür das DPMA, Klassen 9 und 42.
- PS12 **Akku-Optimierung nur empfohlen (2026-11-15):** Die App soll auch mit
  Akku-Optimierung funktionieren, nur ohne Garantie (Alarme über `setAlarmClock` wecken auch
  aus Doze). Deshalb kein Pflichtpunkt und keine Warnung auf *Heute*, sondern eine Empfehlung
  in der Berechtigungsliste mit diesem Hinweis. Kein direkter Dialog mehr
  (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ist im Play Store für Wecker nicht zulässig); der
  Knopf öffnet die App-Info, dort *Akku → Nicht eingeschränkt*. In allen Builds, damit der
  Sideload den Store-Weg testet (playstore.md P4). Umgesetzt in V1.3c6.
- PS13 **Standort im Hintergrund bleibt, mit Begründung (2026-11-15):** Die Orterkennung
  läuft heute vor allem über das WLAN (7.29); der Geofence per Standort ist gut, aber nicht
  unbedingt nötig und muss ohnehin noch überarbeitet werden (7.30). Die Play-Fassung behält
  `ACCESS_BACKGROUND_LOCATION` und begründet ihn bei der Einreichung mit der Ankunft am Ort
  (Entwurf: playstore-erklaerungen.md P5). Gestrichen wird erst bei einer Ablehnung.
  - Achtung: Auch das WLAN braucht „Immer erlauben“. Android gibt BSSID und SSID nur mit
    Standort-Berechtigung heraus, im Hintergrund nur mit „Immer erlauben“ (design.md 6,
    WLAN). Streichen hieße deshalb: Ankunft am Ort im Play-Build ganz aus, nicht nur der
    Geofence.
  - Die auffällige Offenlegung kommt vor Androids Dialog: Tippt man in der
    Berechtigungsliste auf *Standort „Immer erlauben“*, erklärt erst ein eigener Hinweis
    (Standort und WLAN, auch wenn die App geschlossen ist, bleibt auf dem Handy), dann fragt
    Android; *Nicht jetzt* fragt nichts. Umgesetzt in V1.3c7.
- PS14 **Sicherungsdatei: Export und Import (2026-11-15):** Daten und Einstellungen lassen
  sich als Datei exportieren und wieder importieren (*Einstellungen → Lokale Kopien →
  Sicherungsdatei*), ohne Backend. Gedacht für Tester ohne Cloud-Backup, als zusätzliche
  Sicherung und für den Umzug, falls sich die `applicationId` ändert (playstore.md N1, N4).
  - Die Datei ist mit einem **eigenen Passwort** verschlüsselt (mindestens 12 Zeichen,
    zweimal eingeben), weil sie das Handy verlässt (8.4). Ohne Passwort ist sie nicht zu
    öffnen; die App merkt sich das Passwort nicht.
  - **Ohne Fotos und Audio** (wie das Cloud-Backup); ob sie später dazukommen, entscheidet
    Kevin nach dem Gerätetest.
  - Importieren fragt wie jedes Wiederherstellen: Vergleich, Rückfrage, Tausch beim
    nächsten Start, vorher eine Tageskopie zum Rückgängigmachen (W-5, W-6).
  - Umgesetzt in V1.3c8.
- PS15 **Einstieg für Tester (2026-11-15):** Nach einer frischen Installation zeigt die App
  einen kurzen Einstieg in vier Seiten (playstore.md N3, passt zu PS6): was die App macht
  (klingelt, bis man reagiert; Strenge pro Reminder; Notaus; Daten bleiben auf dem Handy),
  die Berechtigungen der Reihe nach mit ihrem Zweck (nötige zuerst, die übrigen „später“),
  ein Probealarm und der erste Reminder, dazu wohin Rückmeldungen gehen.
  - Von selbst nur, solange es keinen Reminder gibt und der Einstieg nie beendet wurde; auf
    Kevins Handy kommt er nach dem Update nicht. *Überspringen*, *Fertig*, Zurück und
    *Ersten Reminder anlegen* beenden ihn.
  - **Wiederholbar** (Kevin: „Am besten mit Knopf zum Triggern“): *Einstellungen → Hilfe →
    Einstieg zeigen*, jederzeit.
  - **Probealarm:** legt einen normalen Einmal-Reminder „Probealarm“ an (Art
    *Dokumentieren*, also ohne Startklar-Fragen), zwei Minuten nach der nächsten vollen
    Minute; er klingelt wie jeder Reminder und bleibt danach im Verlauf.
  - **Rückmeldung:** über *Verbesserung vorschlagen* (Glühbirne, 7.24) und *Teilen*, dazu
    *Rückmeldung per E-Mail* (Seite 4 und *Einstellungen → Hilfe*) an die öffentliche
    Kontaktadresse `kevin.bartik@gmail.com`, mit festem Betreff samt Version zum Filtern.
    Dieselbe Adresse gilt für Store-Eintrag und Datenschutzerklärung (Kevin, 2026-11-15).
  - Umgesetzt in V1.3c9.
- PS16 **Play-Fassung mit Cloud-Backup (2026-11-18):** Die erste Play-Fassung bringt das
  Backend mit; es ist schon auf DSGVO und Ende-zu-Ende-Verschlüsselung ausgelegt (Kevin:
  „Playstore wird eh noch dauern und daher würde ich das gerne gleich mitnehmen.“).
  Ersetzt die Annahme „ohne Backend“ in playstore.md; die Variante ohne Backend (B4)
  entfällt. Was dazukommt, steht in playstore.md Abschnitt 6 (S1–S12): Sicherheitsphase,
  Freigabe der Anmeldung, Release-Client für
  „Mit Google anmelden“, Löschseite im Web, gemeinsame Datenschutzerklärung,
  Prüferzugang, Datensicherheit mit Metadaten.
  - **Anmeldung auf Einladung** (Kevin, 2026-11-18): Das Cloud-Backup nutzt nur, wer auf der
    Freigabeliste des Backends steht, im geschlossenen Test und in der Produktion. Alle
    anderen nutzen die App ohne Cloud; die App sagt ihnen das und bietet an, die Kennung per
    E-Mail zu schicken. Offen frühestens nach Backend B6 mit Play Integrity (playstore.md S2).
    In der App umgesetzt in V1.3c12: Text „nur auf Einladung“ statt eines Fehlers, Knopf
    *Einladung per E-Mail anfragen* (Betreff „Einladung zum Cloud-Backup“, Kennung im Text),
    Hinweis unter dem Schalter und im Einschalt-Dialog.
- Was für den Play Store noch fehlt (Prüfung 2026-11-15, ergänzt 2026-11-18 um das
  Backend): [playstore.md](playstore.md).
- Backend-Stand und was es für die App bedeutet: [backend.md](backend.md); Einbindung des
  Cloud-Backups: [cloud-entscheidungen.md](cloud-entscheidungen.md) (bestätigt 2026-11-07).

### 7.29 Ankunft im Zeitraum mit WLAN (2026-11-04, ohne GPS seit 2026-11-05)
> Umgesetzt (2026-11-11); wie genau: design.md 6.

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
    auch wenn die App nicht läuft, aber nicht zuverlässig sofort (Gerätetest W1,
    geraetetest-archiv.md: oft erst 15–26 min später); dann findet es die nächste
    Prüfung. Kommt die Meldung, zählt sie im Zeitraum sofort als Ankunft. Meldet Android
    eine Verbindung, die schon bestand (bei jedem Start der App), ist das keine Ankunft
    (2026-11-12).
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

### 7.31 Erledigung zurücknehmen (2026-11-07)
Manchmal verklickt man sich: *Erledigt* statt *Später*, oder bei mehreren Uhrzeiten die
falsche. Zurücknehmen soll schnell gehen, aber kein Schlupfloch sein.
- ZR1 **Wo:** Nach *Erledigt* oder *Auslassen* im Alarm, in der Benachrichtigung oder auf
  der Uhr kommt eine stille Benachrichtigung „‹Titel› erledigt · Rückgängig“, die nach
  1 min verschwindet. Danach steht auf *Heute* unter *Erledigt* bei jedem Eintrag
  *Zurücknehmen* (mit Rückfrage). Über die Schnittstelle für Claude gibt es kein
  Zurücknehmen.
- ZR2 **Was danach gilt:** Der Termin ist wieder offen und klingelt in 10 min. Das zählt
  nicht als *Später* oder *Verschieben* (keine Limits verbraucht).
- ZR3 **Was geht:** *Erledigt* (Ziel, Minimum, ohne Nachweis), *Auslassen* von Hand und
  *Abbruch* einer Sitzung vor ihrer maximalen Dauer (dann läuft sie weiter, 7.11 D3).
  Was die App selbst setzt (*verpasst*, *entfällt*, ausgelassen wegen Pause, Notaus,
  Planänderung, Verwerfen, Wochenziel erreicht), bleibt.
- ZR4 **Wie lange:** Nur der letzte erledigte Termin eines Reminders, solange der nächste
  noch nicht geklungen hat und der zurückgenommene noch vor der Tagesgrenze (mehrmals
  täglich: vor der nächsten Uhrzeit, Frist: vor dem Fensterende) klingeln kann. Ein
  pausierter Reminder lässt sich nicht zurücknehmen.
- ZR5 **Mehrere Uhrzeiten:** Nur diese eine Uhrzeit geht zurück; der schon geplante
  nächste Termin fällt weg und kommt nach der neuen Erledigung wieder.
- ZR6 **Checklisten:** Nur der Haken, der die Liste abgeschlossen hat, geht zurück
  (bei *Alles erledigt* alle dabei gesetzten); frühere Haken bleiben.
- ZR7 **Nachweise** bleiben gespeichert. War er vollständig, zählt er beim nächsten
  *Erledigt* wieder; die App fragt nicht noch einmal danach (auch nicht „gleicher Text
  wie beim letzten Mal“).
- ZR8 **Verlauf und Zahlen:** Das Ereignisprotokoll bleibt unverändert, dazu kommt das
  Ereignis *zurückgenommen*. Statistik, Wochenziel und Serie zählen den Termin erst
  wieder, wenn er neu erledigt ist. Eine Vorbereitungsaufgabe, die zurückgenommen
  wird, öffnet ihren Blocker wieder.

### 7.32 Vorab verschieben und auslassen – „heute später“ (2026-11-07)
Oft ist früh klar, dass man erst später dazu kommt. Dann soll man den Reminder schon
vorher schieben oder auslassen können, wie man Checklisten-Punkte vorab abhakt.
- VB1 **Vorab-Bildschirm:** *Vorab* (bisher *Erledigen*) auf *Heute* bei *Später heute*
  und *Wenn du zuhause bist* öffnet den Alarm-Bildschirm vor der Fälligkeit. Er bietet
  *Erledigt*, *Minimum*, *Verschieben* und *Auslassen*, aber kein *Später* (es klingelt
  noch nichts). Es klingelt nichts, das Nachweis-Fenster startet nicht.
- VB2 **Heute später:** *Verschieben* bietet vorab zuerst „+30 min“, „+1 Std“ und „+2 Std“
  ab der **geplanten** Zeit (18:00 → 19:00, egal wann man tippt), dann wie bisher
  *Morgen* und *Datum und Uhrzeit wählen*. Die Knöpfe für heute erscheinen nur, wenn die
  neue Zeit vor der Tagesgrenze liegt (mehrmals täglich: vor der nächsten Uhrzeit, Frist:
  vor der Frist).
- VB3 **Konto:** Vorab verschieben zählt wie Verschieben im Alarm (Limit pro Termin);
  jedes weitere Verschieben zählt wieder. Ist das Limit erreicht, ist *Verschieben*
  ausgegraut. Bei *streng* fragt die App vorab nach: „Wirklich jetzt schon auf 19:00?“
- VB4 **Ort:** Ein vorab verschobener Reminder mit Ankunft behält seine neue Zeit, auch
  wenn man früher ankommt. Ist man zur neuen Zeit nicht da (oder *unklar*), wartet er auf
  die Ankunft und ist zur Tagesgrenze *verpasst*, er *entfällt* nicht (wie 7.29 OZ5).
- VB5 **Vorab-Reminder (T6)** wandern mit der neuen Zeit mit und bekommen den Knopf
  *Verschieben …*, der den Vorab-Bildschirm öffnet.
- VB6 **Sichtbar:** Auf *Heute* steht bei einem verschobenen Eintrag „verschoben von 18:00“.
- VB7 **Schlupfloch:** Der Hinweis bei ständigem *Später* (NT2) zählt Verschieben mit,
  auch vorab. Vorab ausgelassen oder erledigt lässt sich zurücknehmen (7.31); der Termin
  kommt dann zur geplanten Zeit, nicht schon in 10 min.

### 7.33 Wiederherstellungsschlüssel im Passwortdienst (2026-11-08)
Beim ersten Gerätetest war das Aufschreiben des Wiederherstellungsschlüssels umständlich
(langen Code abtippen). Die App soll das Sichern deshalb selbst anbieten.
- WS1 **Auf Knopfdruck angeboten** (geändert 2026-11-12, vorher automatisch; Hauptweg
  wieder seit 2026-11-15): Zeigt die App einen neuen Wiederherstellungsschlüssel, ist
  *Im Passwortmanager speichern* der Hauptknopf: Ein Ende-zu-Ende-verschlüsselter
  Passwortmanager geht nicht so leicht verloren wie Papier, weil man ihn regelmäßig nutzt.
  Der Knopf zeigt vorher den Hinweis (WS4) und öffnet erst nach *Speichern* den
  Speicher-Dialog des Passwortdienstes (Credential Manager,
  `CreatePasswordRequest`; Standard ist der Google Passwortmanager, andere Dienste wie
  Bitwarden gehen genauso). Name: „avlreminder Wiederherstellung“, als Nutzername das
  Konto-Pseudonym. Gespeichert → fertig, eine Bestätigung genügt. Ohne Passwortmanager:
  *auf Papier aufgeschrieben* mit Rückfrage. Von selbst öffnet sich kein Speicher-Dialog.
- WS2 **Automatisch geholt:** Fragt die App nach dem Schlüssel (neues Gerät, App-Daten
  gelöscht), bietet sie zuerst den gespeicherten aus dem Passwortdienst an
  (`GetPasswordOption`); Eintippen bleibt möglich.
- WS3 **Nie anders:** Der Schlüssel geht nur an den Passwortdienst, nie ins Log, in die
  Zwischenablage oder ans Backend.
- WS4 **Hinweis:** Der Google Passwortmanager verschlüsselt nur mit eingeschalteter
  **Geräteverschlüsselung** Ende-zu-Ende; ohne sie könnte Google Schlüssel und (in GCP
  liegende) Sicherung zusammenbringen. Die App sagt das **vor** dem Speichern und nennt
  die Folge: Wer das Google-Konto übernimmt, kann dann ein eigenes Gerät freischalten
  und alles entschlüsseln (Backend `anforderungen.md` Kap. 11, 2026-11-12). Empfohlen sind
  Passwortmanager, die immer Ende-zu-Ende verschlüsseln (z. B. Bitwarden, 1Password); die
  App kann nicht erkennen, welcher speichert. Dazu, vor und nach dem Speichern: den
  Passwortmanager selbst mit seinem Wiederherstellungscode auf Papier schützen, denn dort
  liegt das größte Risiko (2026-11-15).

### 7.34 Repo veröffentlichen (geplant, 2026-11-14)
Kevin will das App-Repo `FairPlayer4/avlreminder` und das Backend-Repo in den nächsten
Wochen öffentlich machen. Prüfung vom 2026-11-14 (Arbeitsbaum, alle 226 Commits auf allen
Branches, CI): **keine Secrets** (keine Schlüssel, Tokens, Keystores, Passwörter; gelöschte
Dateien ohne echte BSSIDs oder Koordinaten). Das einzige CI-Secret `BACKEND_DEPLOY_KEY`
bleibt auch im öffentlichen Repo verborgen und geht nicht an PRs aus Forks.

Was vorher zu tun ist, in dieser Reihenfolge (Vorschlag, noch nicht entschieden):
- OV1 **Server absichern:** Laut Backend kann jedes Google-Konto ein Konto anlegen. Mit
  der öffentlichen Server-URL und Web-Client-ID (`CloudConfig.kt`, beide kein Secret)
  könnten Fremde sichern: Kosten bis zum Kill-Switch, danach ist Kevins Backup gesperrt,
  und fremde Daten lägen ohne Datenschutzerklärung (PS3) auf dem Server. Gehört zur
  Sicherheitsphase ([backend.md](backend.md)). Präzisiert 2026-11-14:
  - **Zugang nur nach Freigabe** (Backend ID7): Der Server nimmt nur Google-Konten an,
    deren Kennung (HMAC aus Pfeffer und `sub`, bleibt über *Konto löschen* hinweg gleich)
    auf einer Liste steht; leer heißt niemand. Sonst legt er nichts an und antwortet mit
    der Kennung. Die App zeigt dann „Zugang nicht freigegeben. Schick diesen Code an den
    Betreiber: …“, Kevin trägt ihn ein. Wer mehr will, betreibt einen eigenen Server.
    **Umgesetzt und deployt 2026-11-14** (Backend PR #25; App: Fehlertext mit Kennung,
    markierbar zum Kopieren; ein später entzogener Zugang beendet die Sitzung wie eine
    abgelehnte Anmeldung und meldet sich sofort).
  - **Konfigurierbarer Server:** URL und Web-Client-ID als Gradle-Property bzw. aus
    `local.properties`; ohne Wert ist das Cloud-Backup im Build ausgeblendet, fremde
    Builds treffen nicht Kevins Server. **Umgesetzt 2026-11-14** (`avlCloudServerUrl`,
    `avlCloudWebClientId`; Einrichtung in cloud-entscheidungen.md): ohne Werte keine
    Cloud-Einstellungen, die stündliche Sicherung stoppt, der Build warnt.
  - **Kosten bei Angriffen** (Backend ID8): Die Adresse gilt nicht als Geheimnis, sie
    bleibt auch nach dem Zurücksetzen der Sicherheitsphase gleich (gleiches Projekt).
    Geschützt wird über Grenzen: signierte Sitzungs-Token, damit gefälschte kein
    Firestore-Lesen kosten, und ein Alarm auf die Anfragezahl, der den Kill-Switch
    direkt auslöst, statt auf die verzögerten Budgetdaten zu warten. **Umgesetzt und
    deployt 2026-11-14** (Kill-Switch ab mehr als 10.000 Anfragen je Stunde).
- OV2 **Echte Testdaten nicht veröffentlichen:** In `testdata/real-*.json` sind Texte
  ersetzt und Orte verschoben, aber die Zeiten echt; öffentlich verraten sie Kevins
  Tagesablauf (z. B. Ankunft zuhause). Die Dateien bleiben lokal (`.gitignore`);
  `RealDataTest` überspringt sich ohne Datei. **Umgesetzt 2026-11-14**; in den alten
  Commits steht die Datei weiter, deshalb OV5.
- OV3 **Lizenz** (`LICENSE`) wählen; ohne sie darf niemand den Code verwenden.
  **Entschieden 2026-11-14: Apache-2.0** für App und Backend (freizügig mit Patentklausel;
  GPL verträgt sich schlecht mit den proprietären Play-Services-Bibliotheken der App).
  `LICENSE` und Hinweis im README in beiden Repos.
- OV4 **Backend:** Muss nicht öffentlich werden, aber ohne `client/` und `proto/` kann
  niemand außer Kevin die App bauen (Composite Build, cloud-entscheidungen.md A-2), und
  PR-Builds aus Forks scheitern am Deploy-Key. Empfehlung: das Backend-Repo nach eigener
  Prüfung mit veröffentlichen (die Verschlüsselung aus `client/protokoll` steckt ohnehin in
  der APK, „der Server sieht keine Inhalte“ wird erst so überprüfbar); danach Deploy-Key
  und Secret löschen. Alternative: nur `client/` und `proto/` in ein eigenes öffentliches
  Repo. Zugleich die Neuentscheidung zu A-2. **Obsolet 2026-11-14:** Das Backend wird
  ganz mit veröffentlicht; Deploy-Key und Secret danach löschen bleibt (OV6/OV7).
- OV5 **Historie bereinigen statt verwerfen:** 45 Commits vom 18. bis 31.10. tragen Kevins
  Arbeits-Adresse (`@arbeitgeber.example`); falls diese Anfänge auf dem Firmenrechner entstanden
  sind, vorher klären, ob der Arbeitgeber Rechte daran haben könnte (§ 69b UrhG). Mit
  `git filter-repo` die Autoren per `--mailmap` auf die GitHub-noreply-Adresse setzen und
  `testdata/real-*` aus allen Commits entfernen; Commits und Nachrichten bleiben. Das
  Ergebnis in ein **neues** GitHub-Repo pushen, nicht per Force-Push ins alte: GitHub hält
  alte Commits über `refs/pull/*` erreichbar. Das alte Repo bleibt privat als Archiv (mit
  PRs und CI-Läufen). Kurz vor der Veröffentlichung, danach alle Klone und Worktrees neu.
  **Kevin, 2026-11-14:** nur bei Bedarf, kurz vorher prüfen. Stand der Prüfung vom
  2026-11-14: Für die **App** ist er gegeben (90 Autor-/Committer-Einträge mit der
  Arbeits-Adresse, ein Commit mit `testdata/real-*.json`, ältere Doku-Stände mit einem
  echten Reminder-Titel und Benutzerpfaden). Das **Backend** braucht ihn nicht: nur die
  Gmail-Adresse und GitHub-Adressen, keine Testdaten, kein Rechnungskonto in der Historie.
  Die Mailmap setzt die Arbeits-Adresse auf die Gmail-Adresse (nicht noreply, siehe OV7).
- OV6 **CI** (erst kurz vorher): wieder bei PRs bauen (für öffentliche Repos kostenlos, Kap. 9, 2026-11-13);
  „Require approval for fork PR workflows“ lassen.
- OV7 **GitHub-Einstellungen** (erst kurz vorher): Secret Scanning mit Push Protection,
  Dependabot-Alerts, Branch-Schutz für `main`. **Keine noreply-Adresse** (Kevin,
  2026-11-14): `kevin.bartik@gmail.com` wird eine rein geschäftliche, öffentliche Adresse
  und steht schon in fast allen Commits. „Keep my email addresses private“ bleibt aus,
  sonst blockt GitHub Pushes mit dieser Adresse.
- OV8 **Durchsehen (optional):** `CLAUDE.md`, `docs/` und `.review/` nennen durchgehend
  Kevin, sein Handy und `%USERPROFILE%`; heikle Lebensdetails stehen nicht drin. Mit dem
  Repo werden auch alte CI-Logs und Artefakte (Debug-APK, 14 Tage) und die PR-Texte
  öffentlich, beim neuen Repo aus OV5 nicht. **Bereinigt 2026-11-14:** Benutzerpfade
  durch `%USERPROFILE%` ersetzt, der echte Reminder-Titel in `stand.md` entfernt; Kevin,
  das S24 und Testtitel bleiben (kein Geheimnis). Das Backend war schon sauber. Ältere
  Stände stehen in der Historie, das deckt OV5 ab.

Reihenfolge (Kevin, 2026-11-14): OV2, OV3, OV8 jetzt in App und Backend; OV1 Rest
(konfigurierbarer Server) vorher, erledigt; OV5 kurz vorher prüfen (für die App nötig); OV6, OV7
kurz davor.

### 7.35 Schnellerinnerung per Widget und Sprache (2026-11-15) [Version offen]
Anlass: Man bekommt etwas gesagt, kann es nicht sofort tun und hat es ein paar Minuten
später vergessen. Festhalten muss in Sekunden gehen, mit einem Tipp.
- SR1 **Widget mit Zeitknöpfen *+5 / +20 / +60 min***: Ein Tipp legt die Zeit fest und
  startet die Spracheingabe nur für den Text. Daneben ein Knopf *Sprache*, bei dem auch
  die Zeit aus dem Satz kommt („in 20 Minuten Anna zurückrufen“).
- SR2 **Spracherkennung über Android** (`RecognizerIntent` bzw. `SpeechRecognizer`),
  möglichst auf dem Gerät (offline). Das Widget öffnet dafür eine durchsichtige Activity.
- SR3 **Lokaler Parser in `core`** trennt Zeitangabe und Text: „in 5 Minuten“, „in 20 min“,
  „in einer Viertelstunde / halben Stunde / Stunde“, „um 15 Uhr“. Tabellengetriebene
  Tests mit deutschen Sätzen, auch Ablehnungsfälle (keine Zeit, Zeit außerhalb von SR6).
- SR4 **Festhalten scheitert nie**: Die Erinnerung entsteht sofort lokal, ohne Rückfrage
  vorher und ohne Netz. Findet der Parser keine Zeit, gilt eine Standardzeit (Vorschlag
  10 min) mit dem ganzen Satz als Text. Kommt bei einem Zeitknopf kein Text an (Abbruch,
  nichts erkannt), entsteht die Erinnerung trotzdem mit einem Platzhaltertext; der Text
  lässt sich über *Ändern* nachtragen.
- SR5 **Bestätigung als Benachrichtigung**: „Erinnerung in 5 min: Müll rausbringen“ mit
  *Rückgängig* und *Ändern*.
- SR6 **Grenzen**: Zeit zwischen 1 min und 7 Tagen; sonst Standardzeit wie in SR4.
- SR7 **Cloud-KI als Rückfall, nur nach Bestätigung**: Hat der Parser keine Zeit
  gefunden, bietet die Benachrichtigung zusätzlich *Mit Cloud-KI auswerten*. Ein Tipp
  zeigt den Satz, der gesendet wird, mit dem Hinweis auf Cloud-KI (Modell, EU-Region),
  und sendet erst nach Bestätigung, jedes Mal neu (Backend K3, K7). Die Antwort ersetzt
  Zeit und Text der schon angelegten Erinnerung. Die App prüft sie selbst noch einmal
  (SR6) und rechnet die Uhrzeit selbst aus. Nur mit eingeschaltetem Backend; ohne Netz
  oder bei einem Fehler bleibt die lokale Erinnerung, wie sie ist. Kommt mit der
  Backend-Etappe B5.
- SR8 Lokale KI (N8, z. B. Gemini Nano) erst, wenn der Parser im Alltag oft scheitert.
- Offen: welche Strenge-Stufe eine Schnellerinnerung bekommt (Vorschlag: *normal*,
  einmalig, ohne Nachweis).
- **Einfachste Fassung zum Ausprobieren (V1.3c11, 2026-11-16):** eigenes Widget, ein
  Tipp startet die Aufnahme, *Fertig* beendet sie, dann eine Zusammenfassung mit
  *Anlegen*, *Nochmal* oder *Abbrechen*. Festes Muster auf dem Bildschirm: „Zeit: in 5
  Minuten. Name: Tee machen“. Umgesetzt sind SR2, SR3, SR6 und SR4 in der Form „Standard
  mit Hinweis in der Zusammenfassung“; vorläufig *normal*, einmalig, ohne Nachweis.
  Abweichend von SR4 bestätigt Kevin vor dem Anlegen (sein Wunsch für den Alltagstest).
  Danach je nach Erfahrung SR1, SR5, SR7 (design.md 11.2).

### 7.36 Rückmeldung: Diagnose und Vorschläge senden (2026-11-18) [Version offen]
Nutzer sollen aus der App Vorschläge (7.24, heute nur lokal) und Diagnosedaten an den
Entwickler schicken können, transparent und nur auf Einladung. Entwurf:
[rueckmeldung-entwurf.md](rueckmeldung-entwurf.md).
- RM1 **Aktiv wählen:** Zustimmung je Sendung, kein Haken vorab gesetzt. Fester
  Mindestumfang plus wählbare Kategorien, jede mit einem Satz, was sie enthält; der
  Titel eines Reminders nur auf eigenen Wunsch.
- RM2 **Genaue Vorschau** dessen, was rausgeht, erzeugt aus derselben Nutzlast.
- RM3 **Ehrlicher Hinweis auf jedem Sende-Bildschirm:** nicht Ende-zu-Ende verschlüsselt
  wie das Cloud-Backup, der Entwickler kann lesen; die Daten werden teilweise von einer KI
  ausgewertet (Claude, betrieben von Google Cloud in der EU, RM8), daher nur begrenzter
  Datenschutz; die
  Verschlüsselung (RM7) ist nur ein einfacher Schutz; keine privaten oder sensiblen
  Angaben schicken.
- RM4 **Gesendet-Liste** mit Löschen je Sendung (auch auf dem Server).
- RM5 **Auf Einladung** wie das Cloud-Backup (7.28 PS16), aber getrennt davon: Die
  Google-Anmeldung reicht, ohne Backup und Wiederherstellungsschlüssel.
- RM6 **Stufen:** (1) Vorschläge per E-Mail mit Auswahl, Vorschau und Hinweis; (2) Diagnose
  für Tester über das Backend, Logger mit wählbaren Kategorien (Alarm, Berechtigungen,
  Energiesparen, Absturz), weiter ohne Inhalte (Backend E-D-1); (3) Vorschläge über das
  Backend.
- RM7 **Verschlüsselung (Zukunft):** Die App verschlüsselt jede Sendung mit einem
  öffentlichen Schlüssel; wer die gespeicherten Daten sieht, kann ohne Schlüssel nichts
  damit anfangen. Den **Diagnose-Schlüssel** kann Claude über ein Skript benutzen, ohne
  ihn zu sehen (Cloud KMS). Den **Vorschlags-Schlüssel** kennt zunächst nur Kevin (offline,
  im Passwortmanager), nicht in KMS unter seiner gcloud-Anmeldung, die Claude mitbenutzt.
  Gegen Claude schützt das organisatorisch, solange Kevin auf diesem PC entschlüsselt.
- RM8 **KI-Auswertung von Tester-Daten nur über Vertex AI in der EU** (2026-11-18): Ein
  Skript entschlüsselt die Sendungen (KMS) und gibt sie an Claude über Vertex AI, Region
  `eu`, aus Kevins GCP-Projekt. Es gilt Googles Auftragsverarbeitungsvertrag, den das
  Projekt schon hat; kein Prompt-Logging, kein Caching, kein Search-Grounding. Zurück kommt
  nur eine Auswertung ohne Personenbezug. Tester-Daten kommen **nie** in eine
  Claude-Code-Sitzung; Kevins eigene Diagnosen darf Claude dort weiter direkt lesen.
  Ersetzt die Voraussetzung „Auftragsverarbeitungsvertrag mit Anthropic“ (Backend P9).
- Offen: vor dem Einrichten Googles aktuelle Bedingungen für das gewählte Modell prüfen
  (Prompt-Logging aus, keine Weitergabe an Anthropic, EU verfügbar); Datensicherheit in
  der Play Console (S5), Datenschutzerklärung (S7), Prüfung durch jemanden mit
  Datenschutzerfahrung (S10).

## 8. Zielplattform und Technik

### 8.1 Zielgerät
- **Samsung Galaxy S24 Ultra, Android 17** (API 37), nur Privatgeräte
- Galaxy Watch vorhanden → Integration in V1.3 über Benachrichtigungen, eigene Watch-App in V1.4 (siehe 7.14)
- **Entscheidung:** targetSdk = 37, **minSdk = 34** (Android 14, seit 2026-11-15; vorher 37).
  Für den geschlossenen Test mit Freunden (7.28 PS1) reichen damit fast alle Handys, die
  noch Updates bekommen. Die App nutzt keine API über Stufe 34; Lint (NewApi) hält das
  fest, die Alarm-Tests laufen unter Robolectric mit Android 14 und 17. Tiefer nicht: Unter
  34 bräuchten Alarm, Vollbild, Vordergrunddienst und WLAN Versionsweichen
  (playstore.md 3). Moderne APIs und Energiespar-Mechanismen nutzen.

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

### 8.3 Tech-Stack
Siehe design.md 1 (*Tech*) und [entwicklungsumgebung.md](entwicklungsumgebung.md).

### 8.4 Daten
- Alle Daten inkl. Fotos und Audio liegen **lokal** auf dem Gerät. Sie verlassen es nur
  **Ende-zu-Ende verschlüsselt** im Cloud-Backup und nur, wenn es eingeschaltet ist
  (7.28 PS8, präzisiert 2026-11-07); Fotos und Audio gar nicht. Dafür braucht die App
  die Berechtigung `INTERNET` (cloud-entscheidungen.md A-3).
- **Sensible Daten** (Fotos, Audio, Standorte, Bewegungsdaten) bleiben grundsätzlich
  **auf dem Gerät**. Gespeicherte Orte und WLANs verlassen es nur **Ende-zu-Ende
  verschlüsselt** im Backup B2b (2026-11-05); Fotos, Audio und Bewegungsdaten gar nicht.
  Synchronisation mit anderen Geräten im Haushalt (7.18) nur für ausdrücklich geteilte
  Inhalte.
- Inventar (7.23) zählt zu den sensiblen Daten. Was außerhalb der App mit Fotos und
  Cloud-KI für den Import vorbereitet wird, verantwortet der Nutzer selbst.
- Rohdaten der Bewegungssensoren (7.16) nach einer einstellbaren Frist löschen;
  das Ergebnis (Dauer, erkanntes Muster) bleibt.
- **Nutzer-Backup** ist das Cloud-Backup des Backends (B2b, [backend.md](backend.md),
  Einbindung [cloud-entscheidungen.md](cloud-entscheidungen.md)): Ende-zu-Ende verschlüsselt, ohne
  Fotos und Audio, nur nach ausdrücklichem Einschalten (7.28 PS8). Dazu die
  **Sicherungsdatei** (7.28 PS14): mit eigenem Passwort verschlüsselt, ohne Fotos und Audio;
  wohin sie geht, wählt der Nutzer in Androids Dateidialog. Die Kopie der Datenbank vor jeder Migration
  (design.md 13) ist nur eine Sicherung gegen Datenverlust beim Update, kein Backup.
- Aufbewahrung: vorerst unbegrenzt.

## 9. Entscheidungen

Neueste zuerst. Kap. 7 enthält immer den gültigen Stand; diese Tabelle ist die Historie. Überholte Einträge sind mit „→ ersetzt“ bzw. „→ präzisiert“ markiert. Einträge bis 2026-10-31: [anforderungen-archiv.md](anforderungen-archiv.md).

| Datum | Entscheidung |
|---|---|
| 2026-11-18 | **Tester-Daten nur über Vertex AI in der EU auswerten** (Kevin: „Idealerweise würde ich für solche Auswertungen ZDR API Token benutzen. Wenn Anthropic das nicht anbietet, könnte man das bestimmt über GCP hinbekommen“; nach Empfehlung „Ja, festhalten“): Anthropics Auftragsverarbeitungsvertrag gilt zwar für API-Konten, nicht aber für Claude Code mit Abo; ZDR gibt es bei Anthropic nur auf Antrag. Claude über Vertex AI, Region `eu`, fällt unter Googles Vertrag des bestehenden GCP-Projekts. Auswertung per Skript, Tester-Daten nie in einer Claude-Code-Sitzung; der Hinweis nennt „Claude, betrieben von Google Cloud in der EU“ (7.36 RM3, RM8). Ersetzt die Voraussetzung „Auftragsverarbeitungsvertrag mit Anthropic“ (Backend P9) |
| 2026-11-18 | **Rückmeldung senden mit aktiver Wahl und ehrlichem Hinweis** (Kevin: „Deine Vorschläge sind gut, der Nutzer soll aktiv wählen“; „es soll transparent sein, dass der Nutzer weiß, dass diese Daten (Diagnose/Vorschläge) nicht den E2E Schutz wie das Backend haben“; „soll immer dran stehen, dass die Daten teilweise von KI ausgewertet werden“): Zustimmung je Sendung ohne vorgesetzte Haken, Vorschau, Gesendet-Liste, Einladung getrennt vom Backup, fester Hinweis auf jedem Sende-Bildschirm (7.36 RM1–RM6) |
| 2026-11-18 | **Verschlüsselung der Rückmeldung als Zukunft** (Kevin: „Können wir das so einrichten, dass du die Diagnose Daten entschlüsseln kannst … aber die Vorschläge erstmal nicht (nur ich kenne den Schlüssel)?“; „klar sein, dass dies nur ein wackliger Schutz ist“): Umschlag-Verschlüsselung mit öffentlichen Schlüsseln in der App; Diagnose-Schlüssel in Cloud KMS, von Claude per Skript benutzbar, ohne ihn zu sehen; Vorschlags-Schlüssel offline bei Kevin, nicht in KMS, weil Claude Kevins gcloud-Anmeldung mitbenutzt (7.36 RM7, rueckmeldung-entwurf.md) |
| 2026-11-18 | **Play-Fassung mit Cloud-Backup** (Kevin: „Kannst du die Anforderungen vom Playstore ergänzen, mit Annahme, dass das Backend mitkommt?“): ersetzt die Annahme „ohne Backend“; Variante ohne Backend (B4) entfällt; neue Punkte S1–S12 in playstore.md 6. Offen: eigene Domain (S4) (7.28 PS16) |
| 2026-11-18 | **Cloud-Backup nur auf Einladung** (Kevin: „Ja, auf Einladung“, nach Empfehlung): Freigabeliste im geschlossenen Test und in der Produktion; offen frühestens nach B6 mit Play Integrity, weil sonst neue Konten das Budget leeren und der Kill-Switch das Backup für alle abschaltet (7.28 PS16, playstore.md S2) |
| 2026-11-16 | **Schnellerinnerung zuerst in der einfachsten Fassung** (Kevin: „Können wir die einfachste Version versuchen umzusetzen? Ich würde gerne im Alltag testen, vielleicht reicht lokal erstmal.“): ein Widget-Tipp startet die Aufnahme, *Fertig* beendet sie, Zusammenfassung mit Bestätigung, *Nochmal* oder *Abbrechen*; festes Muster „Zeit: … Name: …“ auf dem Bildschirm; nur lokal. Umgesetzt in V1.3c11 (7.35) |
| 2026-11-16 | **Foto-Nachweis mit den Lautstärketasten auslösen** (Kevin: „Können wir das Nachweis Foto mit dem Leiser Button auslösen lassen so wie bei der Kamera App“): beide Tasten, nur solange die Kamera zu sehen ist; die Tasten ändern dann keine Lautstärke (7.5 N2a). Umgesetzt in V1.3c10 |
| 2026-11-15 | **Schnellerinnerung per Widget und Sprache** (Kevin: „Ja alle Punkte passen, erst lokal, dann per Bestätigung mit Hinweis Cloud KI und auch die Option mit Widget +5 / +20 / +60 mit Text über Sprache ist auch passend.“): Widget mit *+5 / +20 / +60 min* und Spracheingabe für den Text, dazu ein Knopf nur mit Sprache. Spracherkennung und Zeitangabe lokal, die Erinnerung entsteht sofort und scheitert nie (Standardzeit, Rückgängig in der Benachrichtigung). Cloud-KI nur als Rückfall nach Bestätigung je Anfrage (Backend K7). Version offen (7.35) |
| 2026-11-15 | **minSdk 34 statt 37** (Kevin: „Ja, auf 34 senken“): Mit Android 17 als Minimum fänden sich kaum 12 Tester für den geschlossenen Test. Mit minSdk 34 laufen Lint, Bau und alle Tests ohne Codeänderung; darunter bräuchte es Versionsweichen. Ersetzt „Android 17 bleibt Minimum“ vom 2026-11-02 (8.1, 7.28 PS1, playstore.md 3). Umgesetzt in V1.3c9 |
| 2026-11-15 | **Kontaktadresse ist die Gmail-Adresse** (Kevin: „Da läuft GCP drüber und ich versuche die für alles mit der App zu verwenden.“): `kevin.bartik@gmail.com` für App, Store-Eintrag und Datenschutzerklärung; sie steht ohnehin als Autor im öffentlichen Repo (OV5). Konto mit Passkey bzw. Sicherheitsschlüssel schützen, da auch GCP und Play Console daran hängen (7.28 PS15). Umgesetzt in V1.3c9 |
| 2026-11-15 | **Einstieg für Tester, wiederholbar** (Kevin: „Du kannst einen Vorschlag machen und ich spiele es nach Umsetzung am Handy durch. Am besten mit Knopf zum Triggern (Wiederholbarkeit).“): vier Seiten mit Erklärung, Berechtigungen der Reihe nach, Probealarm und erstem Reminder; von selbst nur ohne Reminder, sonst über *Einstellungen → Hilfe* (7.28 PS15, playstore.md N3). Umgesetzt in V1.3c9 |
| 2026-11-15 | **Sicherungsdatei mit Passwort, ohne Fotos und Audio** (Kevin wählte „Mit Passwort“ und „Erstmal ohne“): Export und Import als Datei in den lokalen Kopien; AES-256-GCM mit einem Schlüssel aus dem Passwort (PBKDF2, 600 000 Runden), mindestens 12 Zeichen; Import wie jedes Wiederherstellen (7.28 PS14, playstore.md N4). Umgesetzt in V1.3c8 |
| 2026-11-15 | **Standort im Hintergrund behalten und begründen** (Kevin: „Wir sollten es nur bei Ablehnung streichen und bei der Einreichung begründen.“): Ort per WLAN reicht heute, der Geofence muss ohnehin überarbeitet werden; da auch das WLAN „Immer erlauben“ braucht, hieße Streichen: Ankunft im Play-Build aus (7.28 PS13, playstore.md P5). Hinweis vor Androids Dialog umgesetzt in V1.3c7 |
| 2026-11-15 | **Akku-Optimierung nur empfohlen, ohne direkten Dialog** (Kevin: „Theoretisch sollte die App auch mit Battery Optimizations funktionieren nur ohne Garantie.“): kein Pflichtpunkt mehr, keine Warnung auf *Heute*; der Knopf öffnet die App-Info statt Androids Dialog; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` entfällt (7.28 PS12, playstore.md P4). Umgesetzt in V1.3c6 |
| 2026-11-15 | **Name „AuViLo-Reminder“** (Kevin: „Erstmal nur Anzeigename ändern damit keine Neuinstallation nötig ist. Restliche Teile ändern wir vor einem Playstore Antrag.“): sichtbarer Name sofort, `applicationId`, Paket und Logo vor dem Antrag; Marke wahrscheinlich, falls der Play Store kommt (7.28 PS11, playstore.md N1). Anzeigename umgesetzt in V1.3c5 |
| 2026-11-15 | **Umstieg auf `SCHEDULE_EXACT_ALARM`** in allen Builds (Kevin: „Es muss dem Nutzer klar kommuniziert werden, dass die App ohne die Berechtigung nicht richtig funktioniert“): Pflichtpunkt *Wecker und Erinnerungen*, ohne ihn ein eigener Hinweis auf *Heute* und eine Benachrichtigung; Alarme kommen dann ungenau statt gar nicht (7.28 PS10). Umgesetzt in V1.3c4 |
| 2026-11-15 | **Positionierung als Wecker und Timer**, später gemeinsamer Kalender (Kevin: „Die App ist prinzipiell als Wecker und Timer gedacht“); der Store-Eintrag beschreibt sie so. Ein zusätzlicher Schritt für exakte Alarme (`SCHEDULE_EXACT_ALARM`) ist in Ordnung, solange die Kernfunktion nicht leidet (7.28 PS10, playstore.md P1) |
| 2026-11-15 | **A-15 neu: Passwortmanager als Hauptweg** (Kevin: „realistisch ist es sicherer wenn man es in einen (E2E) Passwortmanager packt“): Hauptknopf *Im Passwortmanager speichern*, davor ein Hinweis (Ende-zu-Ende-Verschlüsselung, beim Google Passwortmanager nur mit Geräteverschlüsselung, sonst reicht das Google-Konto; den Passwortmanager selbst mit seinem Wiederherstellungscode auf Papier schützen). Papier bleibt als Ausweg mit Rückfrage. Kein Speicher-Dialog mehr von selbst. Ändert den Eintrag vom 2026-11-12 (7.33 WS1, WS4). Umgesetzt in V1.3c3 |
| 2026-11-15 | **Displaysperre vor dem Löschen** (Backend SE5, Kevin: „SE5 angehen“): *Konto löschen* und *Sicherung löschen* fragen nach dem eingetippten Wort die Displaysperre ab (BiometricPrompt, starke Biometrie oder Geräte-PIN), bevor etwas den Server erreicht. *Sicherung löschen* dazu, weil es wie *Konto löschen* alle Fassungen endgültig löscht (Empfehlung, Kevin kann widersprechen). Ohne Displaysperre geht es weiter und wird protokolliert: Es gibt nichts zum Bestätigen, und Löschen (Art. 17) muss möglich bleiben. Abbrechen, Fehlversuche oder fehlender Sensor heißen nein. *Gerät sperren* gibt es in der App nicht als eigene Aktion (nur *Dieses Gerät abmelden*, das nichts löscht). Umgesetzt in V1.3c2 |
| 2026-11-14 | ***Konto löschen* ersetzt *Cloud-Daten löschen*** (R-3, Backend B1/K1, Kevin nach Vorschlag): freigeschaltet sofort, sonst Antrag mit 7 Tagen, in denen jedes freigeschaltete Gerät abbrechen kann; das Handy vergisst die Schlüssel erst nach der Bestätigung des Servers. Einen beantragten Termin zeigt die App mit Benachrichtigung; der stündliche Lauf fragt dafür spätestens nach 23 Stunden beim Server nach, auch bei unveränderten Daten. Neu anfangen heißt: Konto löschen und neu anmelden. Die Bestätigung mit der Displaysperre (SE5) gilt damit für *Konto löschen* und *Gerät sperren*. Außerdem zeigt *Cloud-Backup* die **Konto-ID** zum Kopieren: Nur mit ihr lässt sich eine Anfrage auf Auskunft (DSGVO Art. 15) zuordnen (Backend `docs/datenschutz/`). Umgesetzt in V1.3c2 |
| 2026-11-14 | OV1 präzisiert (7.34, Backend ID7/ID8): **Zugang nur nach Freigabe** über eine Liste von Kennungen; nicht Freigegebene sehen ihren Code und fragen bei Kevin an, andere betreiben bei Bedarf einen eigenen Server. Server-URL kein Geheimnis und nach dem Zurücksetzen unverändert; Schutz über signierte Sitzungs-Token und einen Anfragealarm, der den Kill-Switch auslöst |
| 2026-11-14 | **App-Repo soll öffentlich werden** (in den nächsten Wochen oder Monaten). Prüfung: keine Secrets in Repo, Historie und CI. Checkliste OV1–OV8 in 7.34: Server auf erlaubte Konten beschränken und URL konfigurierbar, echte Testdaten lokal halten, Lizenz, `client/`/`proto/` (oder das Backend) mit veröffentlichen, Historie mit `git filter-repo` bereinigen (Arbeits-Adresse, Testdaten) und in ein neues Repo pushen, PR-Builds wieder an. Offen: Lizenz, Umfang beim Backend, Art der Historie |
| 2026-11-14 | OV1 Teil Freigabe und Kostengrenzen umgesetzt (Backend ID7/ID8, deployt): App zeigt bei nicht freigegebenem Konto die Kennung zum Kopieren; Entzug beendet die Sitzung mit sofortiger Meldung. Offen in OV1: konfigurierbarer Server |
| 2026-11-14 | Veröffentlichen (7.34, Kevin): **App und Backend** in den nächsten Wochen; Lizenz **Apache-2.0**; OV4 obsolet (Backend ganz öffentlich); OV5 nur bei Bedarf, für die App nötig (Arbeits-Adresse, Testdaten); **keine noreply-Adresse**, Gmail wird geschäftlich; OV2 und OV8 umgesetzt; OV6/OV7 erst kurz davor |
| 2026-11-14 | OV1 konfigurierbarer Server umgesetzt: Adresse und Web-Client-ID aus Gradle-Property oder `local.properties`; ohne beide kein Cloud-Backup in der App und Warnung im Build, halb gesetzt bricht ab. Die CI baut ohne Server |
| 2026-11-13 | Verschiebe-Limit wahlweise pro Termin, Woche oder Monat (Z2b, nach Vorschlag): Kalenderwoche Mo–So bzw. Kalendermonat, gezählt zum Zeitpunkt des Verschiebens über alle Termine des Reminders; zählt *Verschieben* (auch vorab) und ein zählendes *Rest auf morgen*, *Später* bleibt frei; Bestand, Vorlagen und Strenge-Startwerte pro Termin; der Alarm zeigt, was bleibt und ab wann es wieder geht. Umgesetzt in V1.3c1 |
| 2026-11-13 | Antworten auf Review-Lauf 6 (`.review/runs/2026-11-13.md`), beide nach Vorschlag: (1) Ändert sich die Ankunftsregel, nachdem ein *verschobener* Termin schon angekommen ist, wartet er wieder auf die Ankunft, jetzt nach der neuen Regel (`awaitsArrival`, zur Tagesgrenze *verpasst*); ergänzt Frage 5 vom 2026-11-12. (2) Ohne Bildschirm (Benachrichtigung, Uhr) gibt es *Teilschritt* wie *Erledigt* nicht, wenn ein Nachweis mit Foto, Text oder Audio nötig ist. Außerdem Wunsch: Das Verschiebe-Limit soll sich auch wöchentlich oder monatlich erneuern können (Z2b, Details in Klärung) |
| 2026-11-13 | **CI nur nach dem Merge auf `main`** (GitHub-Minuten, privates Repo): kein Build bei PRs und auf `develop`, reine Doku-Merges ohne Lauf; vor dem PR lokal mit `-PbackendStreng=true` bauen. Actions auf Commit-SHA festgelegt, Dependabot meldet monatlich neue Versionen. Mit der Veröffentlichung als Open Source wieder bei PRs bauen |
| 2026-11-12 | Antworten auf Review-Lauf 5 (`.review/runs/2026-11-12-2.md`), alle nach Vorschlag: (1) Umbau-Etappe **V1.3c0** vor V1.3c (Review-Empfehlungen E17–E21, E23): Ende eines Alarms als Effekt der Engine, Sitzungsantworten aus einer Quelle, `AppGraph` entschlackt, `Scheduler` intern geteilt, `Database.kt` geteilt. (2) Architektur: Wahrheit sind Room und DataStore; Gerätezustand außerhalb des Backups ist eine eigene Kategorie; Zeit, nach der die App entscheidet, kommt auch in `:app` über die Uhr der Engine; Effekte sind planen, klingeln und beenden. (3) Schließt das System einen Termin (*verpasst*, Notaus-Skip), verschwinden auch Klingeln und Benachrichtigung. (4) F2: Eine Ortsprüfung, die vor dem Start des Ankunftszeitraums geplant war und erst danach kommt, zählt als Prüfung zum Start (7.29). (5) Jede Änderung an der Ankunftsregel (Ort, Puffer, Vorlauf, Regel weg) leert `arrivedAt`; reine Textänderungen nicht. (6) Formular: halbe Eingaben überstehen Design-, Schriftgrößenwechsel und Prozesstod; das Wochenlimit gilt im Formular auch für die Minimum-Dauer einer Sitzung; eine Startklar-Antwort lässt sich überschreiben, aber nicht leeren (Hinweis im Feld) |
| 2026-11-12 | Nach der Sicherheits- und Datenschutzprüfung des Backends (Backend `anforderungen.md` Kap. 11): **Papier ist der Standard** für den Wiederherstellungsschlüssel; den Passwortdienst bietet die App nur auf Knopfdruck nach dem Hinweis an (7.33 WS1, WS4; A-15). Vor der Sicherheitsphase kommen in die App außerdem *Konto löschen* und die Bestätigung mit der Displaysperre für *Cloud-Daten löschen*, *Konto löschen* und *Gerät sperren* (Backend B1, SE5) |
| 2026-11-12 | Antworten auf Review-Lauf 4 (`.review/runs/2026-11-12.md`): (1) Ein Vollbild zur Zeit, „+1 wartet“, *Rest später* (7.3, 7.4) auf V1.3c verschoben, nicht verworfen. (2) *Abgebrochen* aus Benachrichtigung oder Uhr ohne Rückfrage, aber 60 s zurücknehmbar wie *Erledigt* (7.31); danach läuft die Sitzung mit dem alten Start weiter; auf dem Bildschirm bleibt der Dialog. (3) F2 und WLAN-Meldung: die erste Prüfung des Zeitraums exakt auch im Doze; F2 gilt, wenn die *erste Prüfung dieses Zeitraums* einen schon da findet, egal wie spät sie kommt; eine WLAN-Meldung zählt nur als Ankunft, wenn vorher nicht dasselbe WLAN verbunden war. (4) Sitzungen zählen für Wochenziel, Minimum-Limit und Statistik in der Woche ihres Starts. (5) Doku: Cloud-Entwürfe zu `cloud-entscheidungen.md` zusammengelegt, `testsession.md` bis zur Überarbeitung vor V2 gelöscht, Kap.-9-Einträge bis 2026-10-31 nach `anforderungen-archiv.md`. Außerdem: *Teilschritt* bei aufgebrauchtem Minimum-Limit ausgeblendet, mit Hinweis; „Fassung wählen“ (R-4) in der App erst mit dem Wiederherstellen |
| 2026-11-11 | Sitzung mit Timer (7.11, 7.20 V2) in V1.3b1 umgesetzt (design.md 3.1). Bei der Umsetzung festgelegt: ohne Antwort am Ende *Minimum* mit erledigten Punkten, unter Notaus *abgebrochen*, sonst *verpasst*; unbeantwortete Nachfrage fragt nach höchstens 10 min erneut; *Teilschritt* nur mit Minimum; Schnitt aus den letzten 10 abgeschlossenen Sitzungen; während der Sitzung nur Texte änderbar; Vorlagen ohne Sitzung. Versionsname ab jetzt `1.3b<n>-backup<Schritt>` (erster: `1.3b1-backup5`, Code 17) |
| 2026-11-11 | Entwurf 7.29 Z.7 bestätigt und in V1.3b0.6 umgesetzt (design.md 6). Bei der Umsetzung festgelegt: F2 gilt, wenn die erste Prüfung im Zeitraum (bis 5 min nach Beginn) einen schon da findet (→ präzisiert 2026-11-12: die für den Beginn geplante Prüfung, egal wie spät sie kommt); Prüfungen im Raster Beginn + k·Prüfabstand, die späteste Uhrzeit prüft der Alarm des Termins selbst; der Puffer gilt auch für verschobene Termine (`awaitsArrival`), nicht für *Wenn ich ankomme*; Vorlauf 0 = genau das alte Verhalten (kein Puffer, keine Prüfungen); WLAN-Namen und BSSIDs bleiben auf dem Handy (nicht im Diagnose-Log, nicht in der Claude-Schnittstelle, im Testdaten-Export ersetzt); die WLAN-Sonde ist entfernt. Einstellungen *Orte*: Zuhause und ein weiterer Ort, *Hier festlegen* übernimmt Standort und verbundenes WLAN |
| 2026-11-09 | Nach Review-Lauf 10 (Backend-Repo `.review/runs/2026-11-09.md`, Fragen 1–3): (1) *Cloud-Daten löschen* sperrt **alle Geräte** des Kontos, nicht nur dieses (Ablauf in `protokoll`, R-3 in cloud-entscheidungen.md). (2) Lehnt der Server die Sitzung ab (`AnmeldungAbgelehnt`), benachrichtigt die App sofort „Bitte neu anmelden“; der 3-Tage-Hinweis (A-13) hängt am gemerkten Konto statt an der Sitzung; ohne Sitzung heißt der Löschknopf „Nur dieses Handy zurücksetzen“, mit dem Hinweis, dass auf dem Server nichts gelöscht wird. (3) Nach einem lokalen Wiederherstellen (Tageskopie, *Rückgängig*) gilt W-7: automatisch wird erst nach einem Sichern von Hand wieder gesichert |
| 2026-11-09 | Nach Review-Lauf 9 (Backend Kap. 11): Der Server prüft beim ersten Schlüssel-Backup zuerst die Version und schaltet ein angenommenes Gerät frei; die Einrichtung verwirft ein abgelehntes offenes Backup; die Testattrappe der App bildet die Regel nach. Dazu der Knopf „Mit Wiederherstellungsschlüssel freischalten“. Der Worker wertet eine unbeantwortete Rückfrage „übernehmen?“ wie „nein“ (A-10) |
| 2026-11-09 | → ersetzt 2026-11-11 (ab 1.3b1: `1.3b<n>-backup<Schritt>`, stand.md): Versionsname je auf dem Handy installiertem Stand: `1.3b0.<n>-backup<Schritt>`, `versionCode` +1 bei jeder Installation (ab `1.3b0.5-backup5`, Code 15). So zeigen Einstellungen und Diagnose-Log, welcher Stand läuft |
| 2026-11-09 | Gerätetests: Nach jeder Änderung ein kleines festes Set manueller Tests auf dem S24, größere Testschleifen mit dem Emulator. `geraetetest.md` wird dafür gesondert überarbeitet ([stand.md](stand.md)) |
| 2026-11-09 | Einmalige Reminder und Fristen gelten nach einem Wechsel der Zeitzone (Reise, Sicherung in einer anderen Zone wiederhergestellt) nicht als verschoben: Sie bleiben bei ihrem Termin und kommen nur wieder, wenn ihr Zeitpunkt im Formular geändert wird. Das Verschieben legt den neuen Termin ausdrücklich beim Speichern an (`Changes.save`); `ScheduleRules` plant für *einmalig* nur den ersten Termin. Ein Termin, der schon geklingelt hat, endet beim Verschieben als „Zeitplan geändert“ (wie bei Fristen). Keine Migration: der alte Vergleich steckte nur in der Rechnung, nicht in den Daten |
| 2026-11-09 | Cloud-Backup Schritt 5 (W-1…W-8, R-1…R-6 in cloud-entscheidungen.md) bestätigt: erst *Sicherung prüfen* in einer eigenen Prüfdatei, kein Umschalten zwischen Datenbanken; Wiederherstellen tauscht beim nächsten Start und behält die alten Daten 7 Tage zum Rückgängigmachen; nach dem Wiederherstellen von einem anderen Gerät sichert die App erst wieder nach *Jetzt sichern* mit Rückfrage. *Zurücksetzen* aufgeteilt in *Dieses Gerät abmelden* (löscht nichts auf dem Server) und *Cloud-Daten löschen* (nur mit eingetipptem „löschen“). Bremse: fehlen mehr als 10 oder mehr als die Hälfte der Reminder, sichert die App nicht automatisch. Lokale Kopien: 7 tägliche und 4 wöchentliche. Gerätetest des Wiederherstellens auf einem Emulator mit Google Play |
| 2026-11-08 | Cloud-Backup Schritt 4: Trifft das automatische Sichern auf die Sicherung eines anderen Geräts, ruht es sofort (wie nach „Nein“) und meldet das per Benachrichtigung; die Rückfrage „Überschreiben?“ kommt erst mit *Jetzt sichern* in den Einstellungen (cloud-entscheidungen.md A-10). Der Hinweis nach 3 Tagen zählt ab der letzten Stunde, in der die Sicherung aktuell war |
| 2026-11-08 | Android Lint in Build und CI (`:app:lintDebug`): Altfunde in `app/lint-baseline.xml`, jeder neue Fund (auch Warnung) macht den Build rot; Versions-Hinweise sind abgeschaltet. Go bleibt bei gofmt, go vet und golangci-lint im Backend; detekt erst, wenn Lint ruhig läuft, kein Qodana |
| 2026-11-08 | Wiederherstellungsschlüssel: Die App bietet automatisch an, ihn im Passwortdienst zu sichern, und holt ihn von dort beim Wiederherstellen (7.33, Wunsch aus dem ersten Gerätetest von Backup Schritt 2). Papier bleibt der Weg ohne Passwortdienst → ersetzt 2026-11-12 (Papier ist Standard) |
| 2026-11-07 | Cloud-Backup in der App: Entwurf A-1 bis A-14 (cloud-entscheidungen.md) bestätigt. Keine eigene App-Variante, solange nur Kevin die App nutzt (Schalter aus PS8 im normalen Build); Composite Build mit festem Backend-Commit und Deploy-Key in der CI; neue Berechtigung `INTERNET` (8.4 präzisiert); Credential Manager, Geräteschlüssel und Keysets im Android Keystore; ZIP aus Datenbank und Einstellungen; stündlich per WorkManager nur bei Änderung; Wiederherstellen ersetzt die Datenbank nach lokaler Kopie; Diagnose ans Backend nur mit eigenem Logger ohne Inhalte. App und `core` bauen für Java 21 statt 17 |
| 2026-11-07 | Vorab verschieben und auslassen (7.32): Alarm-Bildschirm vorab mit *Verschieben* und *Auslassen*, „heute später“ +30 min/+1/+2 Std ab der geplanten Zeit, zählt aufs Verschieben-Konto, bei *streng* mit Rückfrage; Ort behält die neue Zeit, wartet sonst auf die Ankunft; Vorab-Reminder wandert mit; NT2 zählt Verschieben mit. Umgesetzt in V1.3b0.4 |
| 2026-11-07 | Erledigung zurücknehmen (7.31): Rückgängig-Benachrichtigung 1 min, danach *Zurücknehmen* auf *Heute*; wieder offen, klingelt in 10 min ohne als *Später* zu zählen; nur der letzte Termin, bis der nächste klingelt bzw. bis zur Tagesgrenze; *Auslassen* auch, *verpasst*/*entfällt* nicht; Nachweis zählt wieder; neues Ereignis statt Löschen. Umgesetzt in V1.3b0.4 |
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
| 2026-11-02 | V1.3-Entwurf bestätigt (design.md, Git-Verlauf): Aufteilung a/b/c; mehrere Uhrzeiten – verpasst beim nächsten Termin, kein Wochenziel und keine Ankunft; NT2-Schwelle 3×2 (oder einmal 4×), Karte auf *Heute*, *Nein danke* 14 Tage; *Notaus* bricht keine Serie; für V1.3b: *abgebrochen* bricht die Serie, Nachfragen *locker*, max. 90 min. V1.3a gestartet |
| 2026-11-02 | Ziel offizieller Play-Store-Release für eine sehr kleine Zielgruppe, mit Backend (Backup, Sync) und vollem EU-Datenschutz; Android 17 bleibt Minimum (7.28) |
| 2026-11-02 | Verschlüsselung auf dem Gerät geklärt: Sandbox + Release-Build reichen, kein SQLCipher (7.28 PS9) |
| 2026-11-02 | Play Store ohne Eile: erst „fertig“ (mindestens bis V2, ggf. Backend); dafür Transparenz, Notausschalter, Backend nur nach Einschalten; Verschlüsselung auf dem Gerät wird bewertet (7.28 PS5–PS9) |
| 2026-11-02 | Hinweis bei wiederholtem *Später* bestätigt (7.27 NT2); Nutzung erst vor V2 mit der Testsession zählen (NT4) |
| 2026-11-02 | Vor V1.3 erst Nutzung beobachten: Funktionen trimmen statt erweitern, Rückfragen bei Schlupflöchern (7.27) |
| 2026-11-02 | V1.2c-Entwurf (design.md 12.4) bestätigt und umgesetzt: Fristen mit Ausführungsfenstern, Vorlagen, Vorab-Reminder, Blocker als Liste mit Vorbereitungsaufgaben, *Blocker gelöst* als Minimum, Nachfragen wenn es hakt |
| 2026-11-02 | Testsession erst vor V2 statt vor V1.3 – die App läuft im Alltag gut |
| 2026-11-01 | Schnittstelle für KI-Agenten in V2: nur lokal, für alle persönlichen Agenten, freiwillig mit Freigabe pro Agent; Agent erledigt nur als *delegierbar* markierte Reminder, nie Nachweis oder Alarm; App stößt den Agenten an; Datenschutz verantwortet der Nutzer, mit deutlichem Hinweis (7.26) |

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
| V1.3b0.2 | 2026-11-05 | Termin merkt sich, ob er geklungen hat; Speichern beim Schlummern verschiebt nicht mehr auf morgen; verschobenes Training wartet auf die Ankunft (7.29 OZ5); Ortsprüfung vor der Transaktion (Kap. 9, 2026-11-05) |
| V1.3b0.3 | 2026-11-05 | WLAN-Sonde im Debug-Build als Gerätetest für 7.29 |
| V1.3b0.4 | 2026-11-07 | Erledigung zurücknehmen (7.31), vorab verschieben und auslassen (7.32) |
| V1.3b0.5 | 2026-11-11 | Cloud-Backup Schritte 1–5 in `develop`, Fixes aus Review-Lauf 10 |
| V1.3b0.6 | 2026-11-11 | Ankunft im Zeitraum mit WLAN (7.29), weiterer Ort, Geofences nach `GEOFENCE_NOT_AVAILABLE` neu registriert |
| V1.3b1 | 2026-11-11 | Sitzung mit Timer und Uhr (7.11, 7.20 V2) |
| V1.3b2 | 2026-11-12 | Antworten auf Review-Lauf 4: Sitzung zählt in der Woche ihres Starts, Abbruch zurücknehmbar, F2 über die geplante erste Prüfung, wiederholte WLAN-Meldung keine Ankunft; Tests (Kap. 9, 2026-11-12) |
| V1.3b3 | 2026-11-12 | Antworten auf Review-Lauf 5, Etappe A: Formular (Entwurf übersteht Neuaufbau, Probleme sichtbar, Limit für Sitzungs-Minimum, Startklar nicht leerbar, Ankunft ohne Ort nicht still weg), F2 auch für eine vorher geplante Prüfung, geänderte Ankunftsregel entscheidet neu, Zurücknehmen einer Sitzung nur vor dem Maximum; Tests der UI-Verdrahtung (Kap. 9, 2026-11-12) |
| V1.3c0 | 2026-11-13 | Umbau-Etappe B aus Review-Lauf 5: Zeit über die Uhr der Engine und Lesen über das Repository (E21), `Database.kt` geteilt (E23), Ende eines Alarms als Effekt der Engine, auch wenn das System den Termin schließt (E17, Frage 3), Sitzungsantworten aus dem Core (E18), `AppGraph.rebuild`, „Hier festlegen“ und Blocker mit Aufgabe in der Engine, `CloudWiring` (E19), `Scheduler` intern geteilt (E20); Fixes aus Review-Lauf 6 (Entwurf beim Neuaufbau nach Start aus Benachrichtigung, Übersicht nicht bei jedem Öffnen neu geplant, Snooze auf altem Termin, Rückgängig nur vor dem Maximum, Nachfrage-Text); Antworten auf Lauf 6: verschobener Termin wartet nach neuer Ankunftsregel wieder, ohne Bildschirm kein *Teilschritt* mit Nachweis |
| V1.3c1 | 2026-11-13 | Verschiebe-Limit pro Termin, Woche oder Monat (Z2b): Formular, Alarm mit „noch 1× diese Woche“ und „wieder ab …“, Vorlagen, Schnittstelle für Claude; Schema 11 |
| V1.3c2–c7 | 2026-11-15 | Konto löschen und Displaysperre (R-3, SE5), Passwortmanager als Hauptweg, exakte Alarme mit `SCHEDULE_EXACT_ALARM`, Name „AuViLo-Reminder“ (7.28 PS11), Akku nur empfohlen (PS12), Hinweis vor dem Standort-Dialog (PS13) |
| V1.3c8 | – | Sicherungsdatei: Export und Import mit Passwort, ohne Fotos und Audio (7.28 PS14) |
| V1.3c9 | – | Einstieg für Tester mit Probealarm, wiederholbar in den Einstellungen (7.28 PS15); minSdk 34 (8.1) |
| V1.3c10 | – | Foto-Nachweis mit den Lautstärketasten auslösen (7.5 N2a) |
| V1.3c11 | 2026-11-16 | Schnellerinnerung per Sprache, einfachste Fassung: eigenes Widget, Aufnahme bis *Fertig*, Zusammenfassung mit Bestätigung (7.35) |
| V1.3c12 | – | Cloud-Backup nur auf Einladung: Text und *Einladung per E-Mail anfragen* mit Kennung (7.28 PS16) |

Details zum Verhalten: [design.md](design.md), Gerätetests: [geraetetest.md](geraetetest.md).

### 10.2 Plan

Was schon umgesetzt ist, steht in 10.1.

1. **Jetzt: Nutzung beobachten und trimmen** (7.27): welche Funktionen Kevin wirklich
   nutzt, Rückfragen bei Schlupflöchern. Fehler aus dem Alltag als Fix auf `develop`.
2. **V1.3c:** **Abläufe** für Training (7.12), dazu ein Vollbild zur Zeit mit *+1 wartet*
   (7.3) und Knöpfe je Art mit *Rest später* (7.4). Entwurf nach einer Woche Training mit
   der Sitzung (Skizze am Ende von design.md).
3. **V1.4:** **eigene Watch-App** für laufende Reminder (7.14, G5–G9).
   - Kandidat zum Vorziehen: Aufsteh-Wecker (7.22) als PoC mit Schritten – sinnvoll ab
     V1.4 (Schritte auf der Uhr), eine Handy-only-Variante auch früher.
   - Möglicher Kandidat: Einkaufsliste nur für einen selbst mit Geschäfts-Trigger (7.23,
     E1–E3), ohne Inventar, KI und Abgleich. Nicht festgelegt.
4. GPS für die Ortsprüfung (7.30), wenn WLAN im Alltag nicht reicht.
   - Unabhängig davon: **Repo veröffentlichen** (7.34), in den nächsten Wochen oder
     Monaten; OV1–OV3 schon vorher.
   - Kandidat zum Vorziehen: **Schnellerinnerung** (7.35 SR1–SR6), klein und rein
     lokal; die Cloud-KI (SR7) erst mit Backend-Etappe B5.
5. **V2** (Kap. 4), vorher Testsession und Nutzung zählen (NT4); danach erst der
   **Play-Store-Release** mit Backend für Backup und Sync, EU-Datenschutz (7.28 PS5).
6. Weitere Anforderungen ergeben sich aus der Nutzung.

## 11. Offene Fragen

Offen, aber klein:
- Zeigt die Uhr den Chronometer der gespiegelten Sitzungs-Benachrichtigung, oder nur die
  Startzeit (Gerätetest K28)?
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
- V2: Wie genau funktioniert die Freigabe für Agenten (Kopplung, Stufen, sensible
  Kategorien), und welche Agenten-Apps erreichen App Functions bzw. einen lokalen
  MCP-Server tatsächlich (7.26)?
