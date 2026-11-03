# Review-Gedächtnis
Letzter Lauf: 2026-11-03 @ 488f7cc (Fixes auf review/2026-11-03 bis b4c1efa)   Laufzähler: 1

## Beabsichtigte Architektur
- **Schichten:** `:core` ist reines Kotlin (model, rules, ports, usecases) und kennt kein Android. `:app` ist Android (ui, alarm, data, location, system, widget, claude) und hängt von `:core` ab, nie umgekehrt. Regeln gehören nach `:core`, `:app` setzt sie nur um.
- **Änderungen:** Alle Änderungen an Remindern und Fälligkeiten laufen über `ReminderEngine`, auch aus UI, Receivern, Benachrichtigungen und der Claude-Schnittstelle.
- **Wahrheit:** Die Room-DB ist die einzige Wahrheit. Alarme, Geofences, Übersicht und Vorab-Reminder werden daraus abgeleitet und per `sync` neu aufgebaut.
- **Zeit und Container:** Zeit kommt nur über `Clock`. Es gibt keine DI-Bibliothek, nur den Container `AppGraph`. Engine-Aufrufe laufen nacheinander über `withEngine`.
- **Vertrauensgrenzen:**
  - adb-Provider `com.avlreminder.claude`: `DUMP`, nur im Debug-Build, Schalter in der App.
  - Exportierte Receiver und PendingIntents.
  - Migrationen (Datenverlust), mit Backup vor jeder Migration.
  - Private Inhalte (Nachweise, Positionen) dürfen nicht ins Log.

Vom Entwickler bestätigt am 2026-11-03.

## Bereiche
| Bereich | Pfade | Anforderungen/Doku | Tests | Zuletzt reviewt | Zustand |
|---|---|---|---|---|---|
| core-Regeln | core/…/rules | design.md 2–10 | core/src/test/…/rules (Kover ≥ 90 %) | 2026-11-03 (nur V1.3a-Teile) | gut; Lücke Mehrfachzeit nach Neustart |
| core-Engine | core/…/usecases | design.md 1, 3, 4 | core/src/test/…/usecases | 2026-11-03 (nur V1.3a-Teile) | Rundenplanung bei sync fehlerhaft (offen) |
| core-Modell/Ports | core/…/model, ports | design.md 2 | indirekt | – | – |
| app-Alarm | app/…/alarm, system | design.md 4, 5, 11.2, 12 | AlarmGlueTest (dünn) | 2026-11-03 | mehrere offene Minor-Funde, Service ungetestet |
| app-Daten | app/…/data | design.md 13 | MigrationTest, RoomReminderRepositoryTest | 2026-11-03 (nur Migration 6→7) | gut |
| app-UI | app/…/ui | design.md 11 | ui-Tests (Robolectric) | – | – |
| app-Claude | app/…/claude, app/src/debug | anforderungen.md 7.26 AG11, design.md 15 | ClaudeApiTest | 2026-11-03 | AG5-Frage offen; Freigabe-Prüfung ungetestet |
| app-Ort/Widget | app/…/location, widget | design.md 6, 11.2 | – | – | – |

## Bekannte Schulden und beobachtete Drift
- 2026-11-03: Regel-Logik in `:app`: `AlarmActivity.canDeferRest`, das Anbieten des nächsten Punkts in `ActionReceiver`. Beginnende Drift.
- 2026-11-03: Die Android-Schicht ist dünn getestet. `AlarmService`, `AlarmActivity`, Freigabe der Claude-Schnittstelle und Provider haben keine Tests; Mutant M3 (`enabled()` ohne `debuggable`) blieb unentdeckt.
- 2026-11-03: Engine-Aktionen sind nicht transaktional, und `AlarmActivity` ruft sie im `lifecycleScope` auf.
- 2026-11-03: Die Fälligkeit speichert den Zeitpunkt der nächsten Runde nicht; `plan()` rechnet ab „jetzt“.

## Offene Fragen an den Entwickler
Siehe .review/runs/2026-11-03.md, Fragen 1–7: AG5 gegen AG11, Termin nach „Handy aus“ bei mehreren Uhrzeiten, Notaus als Schlupfloch, Notaus bei Fristen, NT2 ohne Minimum, Titel im Log, Checkliste und Limit bei mehreren Uhrzeiten.

## Getroffene Entscheidungen
- 2026-11-03: `.review/` liegt im Repository.
- 2026-11-03: Das Architekturbild oben ist bestätigt.
- design.md 13: Titel dürfen ins Diagnose-Log (bis Frage 6 beantwortet ist).
