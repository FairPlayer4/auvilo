# Gerätetest – Prototyp V1.0 / V1.1a / V1.1b / V1.2a / V1.2b

> Ergänzt die automatischen Tests (`:core` JUnit + Kover, `:app` Robolectric/Compose).
> Alles hier braucht das echte Gerät. Reihenfolge so gewählt, dass nichts nachts klingelt.

## Vorbereitung

- [ ] Spike-App deinstalliert (sie nutzt dieselben Mechanismen, zwei Apps würden sich gegenseitig stören)
- [ ] Installation `app-debug.apk` (Paket `com.avlreminder`)
- [ ] Erster Start: rotes Banner "Berechtigungen fehlen" → Einstellungen → alle vier erteilen
- [ ] Banner verschwindet nach Rückkehr in die App

## Abläufe

| # | Test | Erwartung |
|---|---|---|
| 1 | Reminder *locker*, einmalig in 3 min | Banner + Vibration, kein Ton; "Erledigt" in der Benachrichtigung → Verlauf "Ziel" |
| 2 | Reminder *normal*, einmalig in 3 min | Vollbild + Vibrationsmuster, **kein** Ton; "+10 min" → nach 10 min erneut |
| 3 | Reminder *streng*, Foto, gesperrtes Handy, vor 18 Uhr | Vollbild über Sperrbildschirm, Vibrationsmuster, **kein** Ton |
| 4 | wie 3, ab 18 Uhr (V1.1a: bis zur Tagesgrenze 23 Uhr) | zusätzlich Alarmton |
| 5 | Im Vollbild "Erledigt" → Foto aufnehmen | Verlauf "Ziel · mit Foto"; Foto nicht in der Galerie |
| 6 | "Ohne Foto abschließen" | Verlauf "ohne Nachweis" |
| 7 | Verschieben bei Limit 1 | zweites Mal ausgegraut |
| 8 | Minimum bei Limit 1/Woche | zweites Mal in derselben Woche ausgegraut |
| 9 | Auslassen mit Grund | Verlauf "ausgelassen" mit Grund |
| 10 | Keine Reaktion bei *streng* | Runde endet nach 3 min, nächste Runde nach 10 min |
| 11 | Neustart des Handys mit geplantem Reminder | Alarm kommt trotzdem |
| 12 | Handy aus über die Fälligkeit, dann an | Reminder wird sofort nachgeholt (`MISSED_DETECTED`) |
| 13 | Widget: "Meeting +30" | Reminder "Meeting" in 30 min erscheint unter "Als Nächstes" |
| 14 | Widget: "+ Reminder" | öffnet direkt "Neuer Reminder" |
| 15 | Einstellungen: Hell / Dunkel / System | wirkt sofort; Alarm bleibt dunkel |

## V1.1a

Vorher: Version 1.0 mit echten Daten installiert lassen; 1.1a darüber installieren (nicht deinstallieren).

| # | Test | Erwartung |
|---|---|---|
| 20 | Erster Start nach dem Update | alle Reminder und der Verlauf sind da; Foto-Reminder stehen auf "Nachweis · Foto" |
| 21 | Backup prüfen (`adb shell run-as com.avlreminder ls files/backup`) | eine Datei `avlreminder-v1-….db` |
| 22 | "Nicht stören" an, Reminder *normal* fällig | kein Vollbild, keine Vibration; "Heute" zeigt "Nicht stören aktiv – 1 Reminder wartet" |
| 23 | "Nicht stören" wieder aus | nächste Runde (spätestens 10 min) alarmiert normal |
| 24 | Reminder mit "Auch bei Nicht stören", Ausnahme noch nicht erteilt | Banner "Berechtigungen fehlen"; "Erteilen" öffnet die Kanal-Einstellung |
| 25 | wie 24 mit erteilter Ausnahme, "Nicht stören" an | Alarm kommt trotzdem |
| 26 | Uhr: Reminder *normal* und *streng* | Kopie mit Aktionen auf der Uhr, Uhr vibriert; verschwindet mit dem Alarm |
| 27 | Später-Knöpfe "+10 / +30" | Benachrichtigung, Uhr und Vollbild zeigen beide; "+30" → nach 30 min erneut |
| 28 | Reminder um 22:45 fällig, Knöpfe "+30 / +60" | keine Später-Knöpfe (würden über die Tagesgrenze führen) |
| 29 | Nachweis Foto: "Erledigt" im Vollbild | Ton/Vibration stoppen sofort; Benachrichtigung "Nachweis läuft – Alarm pausiert bis …" |
| 30 | Nachweis offen lassen, 10 min nichts tun | Alarm kommt wieder |
| 31 | Nachweis öffnen, dann Home-Taste / Bildschirm aus | Alarm nach ca. 1 min; Rückkehr in den Nachweis pausiert wieder |
| 32 | Nachweis Text (50 Zeichen): zu kurz, dann gleicher Text wie letztes Mal | "Abschließen" bleibt aus, Hinweis wird angezeigt |
| 33 | Nachweis Audio (10 s): Mikrofon erlauben, 5 s, dann 12 s aufnehmen | erst "zu kurz", dann abschließbar; Verlauf "mit Audio" und ▶ spielt ab |
| 34 | Diktieren über die Tastatur im Textfeld | Text kommt an; Tastatur verdeckt "Abschließen" nicht |
| 35 | *Später heute* → "Erledigen" | Nachweis-Seite ohne Alarm; Verlauf "vor dem Reminder erledigt"; nächster Termin stimmt |
| 36 | Neuer Reminder *Überwindung* ohne Startklar-Antworten | Speichern verweigert: "Startklar-Fragen beantworten" |
| 37 | Startklar mit "weiß ich noch nicht" und Frage 3 nicht "Ja, sofort" | Alarm zeigt "Offen"; Ziel und erster Schritt erscheinen als Karten |
| 38 | Botschaft an mich aufnehmen, im Alarm "▶ abspielen" | spielt nur auf Tippen, nicht automatisch |
| 39 | Einstellungen: laut ab 19:00, Tagesgrenze 22:00 | *streng* um 18:30 ohne Ton; um 22:00 offen → verpasst |

## V1.1b

Vorher: 1.1a mit echten Daten installiert lassen; 1.1b darüber installieren. Einstellungen → Zuhause → "Aktuellen Standort als Zuhause" (zuhause ausführen), Standort zuerst "Bei Nutzung", dann "Immer erlauben".

| # | Test | Erwartung |
|---|---|---|
| 40 | Erster Start nach dem Update | Reminder, Verlauf und Nachweise sind da; Backup `avlreminder-v2-….db` (wie Test 21) |
| 41 | Reminder mit "Bei Ankunft zuhause" ohne "Immer erlauben" | Banner "Berechtigungen fehlen"; Eintrag "Standort „Immer erlauben“" |
| 42 | Training Mo–Fr 19:00, "Bei Ankunft zuhause, sonst entfällt"; vor 19:00 heimkommen | Alarm nach der Verweildauer (Standard 3 min), nicht schon beim Ankommen |
| 43 | Am Zuhause vorbeifahren (unter der Verweildauer) | kein Alarm |
| 44 | Wie 42, um 19:00 nicht zuhause | kein Alarm; Verlauf "entfallen" (neutral); nächster Termin morgen |
| 45 | Wie 44 mit "trotzdem klingeln" | Alarm um 19:00 |
| 46 | "Wenn ich zuhause bin" anlegen, während man zuhause ist | klingelt sofort (bis ca. 8 s) |
| 47 | "Wenn ich zuhause bin" unterwegs anlegen | steht unter "Heute → Wenn du zuhause bist"; klingelt nach Heimkehr + Verweildauer |
| 48 | Handy neu starten, während ein "Wenn ich zuhause bin" wartet und man zuhause ist | klingelt kurz nach dem Start |
| 49 | Checkliste (3 Punkte, ohne Nachweis): Benachrichtigung "✓ Punkt 1" | Alarm still; stille Benachrichtigung "✓ Punkt 2" erscheint |
| 50 | Checkliste im Vollbild: einen Punkt abhaken | Haken erscheint, Alarm still, Seite bleibt offen; "Heute" zeigt "1 von 3" |
| 51 | Checkliste mit Foto-Nachweis: Punkt antippen | Nachweis-Seite für diesen Punkt; danach zurück in der Liste |
| 52 | Nach einem Punkt "Rest auf morgen" | kommt morgen zur gleichen Uhrzeit wieder; zählt nicht als Verschieben |
| 53 | Ein Punkt erledigt, Rest bis zur Tagesgrenze offen | nicht verpasst; Rest morgen zur gleichen Uhrzeit |
| 54 | Letzten Punkt abhaken | erledigt; Verlauf zeigt die Uhrzeit jedes Punkts |
| 55 | Punkte bearbeiten (umbenennen, umsortieren, einen löschen) | erledigte Punkte bleiben erledigt |

## V1.2a

Vorher: 1.1b mit echten Daten installiert lassen; 1.2a darüber installieren (nicht deinstallieren).

| # | Test | Erwartung |
|---|---|---|
| 56 | Erster Start nach dem Update | Reminder, Verlauf und Nachweise sind da; Backup `avlreminder-v3-….db` (wie Test 21) |
| 57 | Glühbirne auf Heute, Reminder, Verlauf, Einstellungen und beim Bearbeiten | Dialog "Verbesserung vorschlagen"; nach Speichern bleibt man auf dem Bildschirm |
| 58 | Im Alarm ganz unten "Verbesserung vorschlagen" | Alarm läuft weiter; Vorschlag zeigt "Alarm · „Titel“" |
| 59 | Einstellungen → Verbesserungen → "Vorschläge ansehen" | offene zuerst, wichtige oben, mit Bildschirm, Zeit und Version |
| 60 | "Alle offenen kopieren" und "Teilen" | Markdown-Liste in der Zwischenablage bzw. im Teilen-Menü; nichts wird von selbst gesendet |
| 61 | Vorschlag antippen → "Umgesetzt" | wandert unter "Umgesetzt und verworfen"; "Wieder öffnen" holt ihn zurück |
| 62 | Reminder-Liste mit erledigtem Einmal-Reminder und pausiertem Reminder | Abschnitte aktiv, "Pausiert", eingeklappt "Abgeschlossen (n)" |
| 63 | Sortierung "Name" wählen, App neu starten | Sortierung bleibt erhalten |
| 64 | Reminder bearbeiten → "Archivieren" | pausiert, unter "Abgeschlossen"; keine Alarme mehr |
| 65 | Archivierten "Wiederherstellen" | steht unter "Pausiert"; erst Einschalten plant ihn wieder ein |
| 66 | Abgeschlossenen Einmal-Reminder auf später heute legen | wieder aktiv, steht unter "Heute", klingelt zur neuen Zeit |

## V1.2b

Vorher: 1.2a mit echten Daten installiert lassen; 1.2b darüber installieren (nicht deinstallieren).
Ablauf für eine Stunde am Stück: [testsession-v1.3.md](testsession-v1.3.md).

| # | Test | Erwartung |
|---|---|---|
| 67 | Erster Start nach dem Update | Daten da; Backup `avlreminder-v4-….db`; `diag.log` beginnt mit "state app start 1.2b-prototyp" |
| 68 | Täglichen Reminder bearbeiten → Zeile "Wochenziel" antippen, Ziel 3, höchstens 4, mindestens 1 | Auswahl bleibt stimmig (mindestens ≤ Ziel ≤ höchstens); Heute zeigt "Diese Woche" |
| 69 | Ein Angebot erledigen | "Diese Woche": "1 von 3–4, Minimum erreicht" |
| 70 | Knapp: Ziel = restliche Tage inkl. heute, noch nichts erledigt | Alarm eine Stufe strenger als eingestellt; "Diese Woche" rot hervorgehoben |
| 71 | Ziel erreicht, weiteres Angebot am selben Tag oder später | kommt nur als stille Benachrichtigung; offen bis Tagesende → "ausgelassen – Wochenziel erreicht", nicht verpasst |
| 72 | Höchstens erreicht | nächste Fälligkeit erst am Montag |
| 73 | Mo–Fr-Reminder mit Minimum 1, bis Freitag nichts erledigt | Sa 10:00 strenger Alarm, So 10:00 noch einmal; nach Erledigung keine weitere Rettung |
| 74 | Bearbeiten → "Statistik und Serie" | Woche/Monat/Jahr/Gesamt stimmen mit dem Verlauf; Serie und beste Serie plausibel |
| 75 | Übersicht morgens (Einstellungen → Übersicht) | stille Benachrichtigung "Heute: n Reminder" mit Uhrzeiten; Tipp öffnet die App |
| 76 | Übersicht abends | Rückblick (erledigt/verpasst/offen), Wochenziele, morgen |
| 77 | Übersicht auf "aus" | kommt nicht mehr; nach Neustart weiterhin nicht |
| 78 | Nach dem Test | Claude liest `AvlDiag` (logcat) und `files/diag/diag.log` per `run-as` |
