# Testdaten

`real-<datum>.json` sind Kevins echte Daten, **pseudonymisiert**. Gemacht werden sie mit
[tools/testdata/export_testdata.py](../tools/testdata/export_testdata.py), bei
angeschlossenem Handy und installiertem Debug-Build:

```bash
python -I tools/testdata/export_testdata.py testdata/real-2026-11-12.json
```

- Alle freien Texte sind durch Fülltext gleicher Länge ersetzt: Titel, Warum, Ziel,
  Minimum, Checklisten-Punkte, Notizen, Nachweis-Texte, Startklar-Antworten, Blocker,
  Vorlagen, Ortsnamen und Ereignis-Details. Wörter mit einer Bedeutung in der App
  („Ja, sofort“, Gründe wie „Notaus“, Ergebnisse) bleiben stehen.
- Keine Medien (Fotos, Audios, Motivations-Audio), keine Verbesserungsvorschläge. Die
  Orte liegen an einer erfundenen Stelle.
- Zeiten, Zeitpläne, Zustände und Zählungen bleiben echt. Sie machen die Daten realistisch.
- Das Skript kopiert die Datenbank nur in einen temporären Ordner und löscht ihn wieder.
  Bevor es schreibt, prüft es, dass kein Originaltext mit 4 oder mehr Zeichen übrig ist.

Benutzt von `app/src/test/.../data/RealDataTest.kt`. Der Test lädt die neueste Datei in das
Schema ihrer Version, migriert auf das aktuelle Schema und spielt mit der Engine eine Woche
durch (`Simulation` aus den Testhilfen von `core`). Ohne Datei werden die Tests übersprungen.
