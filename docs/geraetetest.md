# Gerätetest – Kerntests

> Ergänzt die automatischen Tests (`:core` JUnit + Kover, `:app` Robolectric/Compose).
> Hier steht nur, was **nach jedem Update** auf dem echten Gerät geprüft wird. Die
> vollständigen Listen pro Version (Tests 1–96) stehen in
> [geraetetest-archiv.md](geraetetest-archiv.md); die Nummern in Klammern verweisen dorthin.
> Neue Funktionen bekommen beim Release einen eigenen Abschnitt am Ende; was davon
> dauerhaft wichtig ist, wandert danach in die Kerntests, der Rest ins Archiv.

## Vorbereitung
- [ ] Vorige Version mit echten Daten installiert lassen; neue mit `adb install -r`
      darüber installieren, **nie deinstallieren**.
- [ ] Build grün (alle Tests), Kevin hat die Installation freigegeben.

## A. Nach jedem Update (ca. 5 min)

| # | Test | Erwartung |
|---|---|---|
| K1 | Erster Start (20, 85) | Reminder, Verlauf, Nachweise da; bei Schema-Änderung Backup `avlreminder-v<alt>-….db` in `files/backup`; `diag.log` beginnt mit "state app start <version>" |
| K2 | Berechtigungen | kein rotes Banner auf *Heute*; im Diagnose-Log Berechtigungen ok |
| K3 | Alarme neu geplant | `adb shell dumpsys alarm` zeigt die nächsten Fälligkeiten; Widget zeigt die nächste |
| K4 | Reminder *normal*, einmalig in 3 min (2) | Vollbild + Vibration, kein Ton; „+10“ → nach 10 min erneut; danach *Erledigt* → Verlauf „Ziel“ |
| K5 | Darstellung Hell / Dunkel (15) | wirkt sofort; Alarm bleibt dunkel |

## B. Bei Änderungen am jeweiligen Bereich

| # | Bereich | Test | Erwartung |
|---|---|---|---|
| K10 | Eskalation | *streng*, gesperrt, vor und nach `loudFrom` (3, 4, 10) | Vollbild über Sperrbildschirm; Ton erst ab `loudFrom`; Runde 3 min, Pause 10 min |
| K11 | Nicht stören | DND an, dann aus (22, 23, 25) | still, *Heute* zeigt Hinweis; danach nächste Runde normal; mit Ausnahme trotzdem Alarm |
| K12 | Neustart | Handy neu starten bzw. über die Fälligkeit aus (11, 12) | Alarm kommt; Verpasstes wird sofort nachgeholt |
| K13 | Nachweis | Foto, Text, Audio; Nachweis offen lassen bzw. App verlassen (5, 29–33) | Alarm pausiert; nach Ablauf bzw. ca. 1 min wieder da; Prüfungen greifen |
| K14 | Uhr | Reminder *normal* (26) | Aktionen auf der Uhr, verschwinden mit dem Alarm |
| K15 | Ort | Heimkommen vor der Uhrzeit, nicht zuhause zur Uhrzeit (42, 44) | Alarm nach der Verweildauer; sonst „entfallen“ |
| K16 | Checkliste | Punkt abhaken, *Rest auf morgen* (49, 50, 52) | Alarm still; Rest morgen, zählt nicht als Verschieben |
| K17 | Wochenziel | Ziel erreicht, knapp (70, 71) | stille Angebote bzw. eine Stufe strenger |
| K18 | Übersicht, Vorab-Reminder | Übersicht morgens, Vorab 15 min (75, 92, 93) | stille Benachrichtigung; entfällt, wenn schon erledigt |
| K19 | Frist | Fenster, *Auf morgen*, Überfällig (87–89) | Ruhe außerhalb der Fenster; *Überfällig* auf *Heute* |
| K20 | Blocker | *Als Aufgabe planen*, Nachfrage (94, 96) | Aufgabe am Vorabend; Nachfrage ab der Schwelle |

## Neu in V1.3a

| # | Test | Erwartung |
|---|---|---|
| N1 | Reminder täglich mit zwei Uhrzeiten in 3 und 8 min anlegen; ersten Alarm nicht beantworten | ab der zweiten Uhrzeit ist der erste *verpasst*, der zweite klingelt sofort; *Später*/*Verschieben* bieten nichts über die zweite Uhrzeit hinaus |
| N2 | Formular: zweite Uhrzeit hinzufügen, entfernen; mit Wochenziel bzw. Ankunft | Hinweis „Jede Uhrzeit gilt als verpasst …“; mit Wochenziel/Ankunft kein *+ weitere Uhrzeit* |
| N3 | Einstellungen: *Am Wochenende andere Zeiten* an, Sa–So-Felder ändern, wieder aus | Felder erscheinen mit den Mo–Fr-Werten; `diag.log` zeigt die Übersicht mit der Wochenend-Zeit geplant |
| N4 | Notaus *1 Stunde* bei einem gerade klingelnden Reminder; dann *Wieder einschalten* in der Benachrichtigung | Rückfrage; Alarm stoppt, rotes Banner auf *Heute*, stille Benachrichtigung; nach dem Einschalten Banner weg, nächste Runde normal |
| N5 | Notaus *bis ich einschalte*, Gerät neu starten | Pause und Benachrichtigung bleiben; Fälligkeiten nur still |
| N6 | Hinweiskarte (nur, wenn ein Reminder 3× in Folge mind. 2× geschoben wurde) | Karte auf *Heute* mit Uhrzeit/Strenge/*Nein danke*; nach einer Antwort weg |

## Neu in V1.3a2

| # | Test | Erwartung |
|---|---|---|
| N7 | Claude ruft `status` und `reminders` auf, solange der Schalter aus ist | `enabled: false`; `reminders` antwortet „nicht freigegeben …“ |
| N8 | Einstellungen: *Schnittstelle für Claude* einschalten | Rückfrage; danach liefert `reminders` die Liste, `diag.log` zeigt `claude reminders ok` ohne Titel |
| N9 | Claude legt *Gerätetest* an | Reminder in der Liste wie selbst angelegt, nächster Alarm 12:30 bzw. 19:15 |

## Neu in V1.3a3

| # | Test | Erwartung |
|---|---|---|
| N10 | Während *Gerätetest* klingelt, ruft Claude `setActive` mit `false` auf | „läuft gerade – nur in der App“, der Alarm klingelt weiter |
| N11 | Notaus erst einschalten, wenn ein Alarm schon klingelt, bis nach der Tagesgrenze | am nächsten Tag im Verlauf *verpasst*, nicht *ausgelassen (Notaus)* |
| N12 | *Gerätetest* mit Checkliste (ein Test pro Punkt): beim Alarm „Erledigt: ‹Punkt›“ tippen | Alarm endet, „Danach: Rest um …“ stimmte; in *Heute* steht der Fortschritt, der nächste Termin bietet den nächsten Punkt |
| N13 | Während *Gerätetest* klingelt: Notaus *bis ich einschalte*, nach 1 min *Wieder einschalten* | Alarm stoppt beim Pausieren und klingelt sofort nach dem Einschalten wieder |

## Neu in V1.3b0 (Umbau, kein neues Verhalten)

| # | Test | Erwartung |
|---|---|---|
| N14 | K4 (einmalig in 3 min) und ein Alarm mit Checkliste: Punkt in der Benachrichtigung abhaken | Alarm wie bisher; nach dem Abhaken bietet die Benachrichtigung still den nächsten Punkt an |
| N15 | Zuhause: Reminder *wenn ich zuhause bin* speichern | klingelt sofort nach dem Speichern |

## Neu in V1.3b0.1

| # | Test | Erwartung |
|---|---|---|
| N16 | Reminder mit Checkliste bearbeiten → *Punkte abhaken ›*, einen Punkt abhaken, *Fertig für jetzt* | Punkt bleibt abgehakt; Termin und Uhrzeit unverändert; zur Zeit klingelt er nur mit dem Rest |
| N17 | Alarm mit langem Checklisten-Punkt | Punkt als schlichte Zeile, umbricht lesbar |
| N18 | Morgen-Übersicht mit einem Reminder mit mehreren Uhrzeiten | jede Uhrzeit steht einzeln drin |
| N19 | Titel, Ziel, Minimum, Checklisten-Punkt lang tippen | Feld wächst mit; Tastatur zeigt Vorschläge; Enter beendet statt Zeilenumbruch |

### Wochenplan V1.3a (03.–08.11.2026)
Reminder *Gerätetest*: täglich 12:30 und 19:15, *normal*, Minimum „Test nur
vorbereiten“ (legt Claude nach N9 über die Schnittstelle an). Pro Alarm ein Test, freie Termine zum Nachholen.

| Wann | Test | Dauer |
|---|---|---|
| Di 12:30 | T1 = N2: Formular, zweite Uhrzeit hinzufügen/entfernen, mit Wochenziel | 2 min |
| Di 19:15 | T2 = N4: Wenn *Gerätetest* klingelt → Notaus *1 Stunde*, Banner und Benachrichtigung ansehen, *Wieder einschalten* | 3 min |
| Mi 12:30 | T3 = K4 + K5: einmalig in 3 min, „+10“, *Erledigt*; Hell/Dunkel | 15 min, meist warten |
| Mi 19:15 | T4 = N1: Test-Reminder täglich in 3 und 8 min, ersten nicht beantworten | 10 min |
| Do 12:30 | T5 = K14: Aktionen auf der Uhr | 3 min |
| Do 19:15 | T6 = K11: *Nicht stören* an, Reminder in 2 min, dann aus | 15 min |
| Fr 19:15 | T7 = N3: Wochenend-Zeiten an, Übersicht Sa–So 9:00; Sa früh prüfen | 2 min |
| Sa 19:15 | T8 = N5: Notaus *bis ich einschalte*, Handy neu starten, dann **sofort wieder einschalten** | 5 min |

Fr 12:30, Sa 12:30 und So: frei oder nachholen. Danach liest Claude die Logs (C.).

## C. Nach dem Test
- Claude liest `adb logcat -d -s AvlDiag` und `files/diag/diag.log` (per `run-as`) und
  gleicht sie mit den Erwartungen ab. Simulationen (DND, Standort, Zeit) zurücksetzen.
- Ablauf für eine Stunde am Stück: [testsession.md](testsession.md) (vor V2).
