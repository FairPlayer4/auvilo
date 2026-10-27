# Gerätetest – Prototyp V1.0 / V1.1a

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
