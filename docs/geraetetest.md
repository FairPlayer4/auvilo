# Gerätetest – Prototyp V1.0

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
| 1 | Reminder *locker*, einmalig in 3 min | stille Benachrichtigung, kein Ton, keine Vibration; "Erledigt" in der Benachrichtigung → Verlauf "Ziel" |
| 2 | Reminder *normal*, einmalig in 3 min | Banner + Vibration; "+10 min" → nach 10 min erneut |
| 3 | Reminder *streng*, Foto, gesperrtes Handy, vor 18 Uhr | Vollbild über Sperrbildschirm, Vibrationsmuster, **kein** Ton |
| 4 | wie 3, zwischen 18 und 21 Uhr | zusätzlich Alarmton |
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
