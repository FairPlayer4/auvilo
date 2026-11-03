# Review-Gedächtnis
Letzter Lauf: 2026-11-03 @ 08a865c (Lauf 2: Architektur-Review, review/2026-11-03-2)   Laufzähler: 2
Letztes Architektur-Review: Lauf 2 (2026-11-03)

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

Anpassungsvorschläge aus Lauf 2 (noch nicht bestätigt): „Wahrheit“ = Room plus DataStore-Einstellungen; „Zeit nur über `Clock`“ gilt bisher nur in `:core`; Engine als Fassade mit internen Teilen.

## Bereiche
| Bereich | Pfade | Anforderungen/Doku | Tests | Zuletzt reviewt | Zustand |
|---|---|---|---|---|---|
| core-Regeln | core/…/rules | design.md 2–10 | core/src/test/…/rules (Kover ≥ 90 %) | 2026-11-03 (nur V1.3a-Teile) | gut; Lücke Mehrfachzeit nach Neustart |
| core-Engine | core/…/usecases | design.md 1, 3, 4 | core/src/test/…/usecases | 2026-11-03 (Architektur, Lauf 2) | Rundenplanung bei sync fehlerhaft (offen) |
| core-Modell/Ports | core/…/model, ports | design.md 2 | indirekt | – | – |
| app-Alarm | app/…/alarm, system | design.md 4, 5, 11.2, 12 | AlarmGlueTest (dünn) | 2026-11-03 | mehrere offene Minor-Funde, Service ungetestet |
| app-Daten | app/…/data | design.md 13 | MigrationTest, RoomReminderRepositoryTest | 2026-11-03 (nur Migration 6→7) | gut |
| app-UI | app/…/ui | design.md 11 | ui-Tests (Robolectric) | – | – |
| app-Claude | app/…/claude, app/src/debug | anforderungen.md 7.26 AG11, design.md 15 | ClaudeApiTest | 2026-11-03 | AG5-Frage offen; Freigabe-Prüfung ungetestet |
| app-Ort/Widget | app/…/location, widget | design.md 6, 11.2 | – | – | – |

## Bekannte Schulden und beobachtete Drift
- 2026-11-03: Regel-Logik in `:app`: `AlarmActivity.canDeferRest`, das Anbieten des nächsten Punkts in `ActionReceiver`. Beginnende Drift. **Trend (Lauf 2):** Die Alarm-Ansicht gibt es viermal (`Ring`, `AlarmUi`, `AlarmInfo`, `ActionReceiver`); `ActionReceiver` nutzt `Checklists.round` nicht. Empfehlung E1.
- 2026-11-03 (Lauf 2): `ReminderEngine` 803 Zeilen mit vier Verantwortungen, höchste Änderungsdichte. Empfehlung E2 (intern teilen, Fassade behalten).
- 2026-11-03 (Lauf 2): Nachbearbeitung (`ring`, `checkPlacesAndRing`) wiederholt jeder Aufrufer; `ClaudeApi.save` prüft den Ort nicht. Empfehlung E4.
- 2026-11-03 (Lauf 2): Mehrfache Projektionen: Wochenstand zweimal (`weekStatus`, `WeeklyGoals.status`), *Heute* an vier Stellen geladen, Takt in fünf Darstellungen, Vorlagen-Mapping in der UI.
- 2026-11-03 (Lauf 2): Mutant M1 (`resume` ohne Wochenziel-Filter) unentdeckt.
- 2026-11-03: Die Android-Schicht ist dünn getestet. `AlarmService`, `AlarmActivity`, Freigabe der Claude-Schnittstelle und Provider haben keine Tests; Mutant M3 (`enabled()` ohne `debuggable`) blieb unentdeckt.
- 2026-11-03: Engine-Aktionen sind nicht transaktional, und `AlarmActivity` ruft sie im `lifecycleScope` auf. **Trend (Lauf 2):** wichtiger mit B2b-Backup. Empfehlung E3.
- 2026-11-03: Die Fälligkeit speichert den Zeitpunkt der nächsten Runde nicht; `plan()` rechnet ab „jetzt“.

## Offene Fragen an den Entwickler
Aus .review/runs/2026-11-03-2.md: (2) Einstellungen aus dem DataStore ins Backup B2b? Vorschlag ja, als JSON neben dem DB-Abzug.

## Getroffene Entscheidungen
- 2026-11-03: `.review/` liegt im Repository.
- 2026-11-03: Das Architekturbild oben ist bestätigt.
- 2026-11-03 (Fragen 1–4, nach Vorschlag, umgesetzt in V1.3a3): Die Schnittstelle ändert, pausiert und archiviert keinen klingelnden oder überfälligen Reminder. Nach „Handy aus“ klingelt der heute zuletzt begonnene Termin. *Notaus* ist nur neutral, wenn die Pause vor der Fälligkeit begann. Fristen bleiben im Notaus hart.
- 2026-11-03 (Frage 5): Die NT2-Karte braucht kein *Minimum*; keine Abweichung mehr.
- 2026-11-03 (Frage 7a, 7c, umgesetzt in V1.3a3): Checkliste mit mehreren Uhrzeiten läuft über die Termine, ein Punkt pro Termin; das Minimum-Limit zählt Tage.
- 2026-11-03 (Frage 7b, nach Vorschlag, umgesetzt in V1.3a3): Notaus von Hand aus klingelt sofort, wie beim automatischen Pausenende.
- 2026-11-03 (Lauf 2, Frage 1): Umbau-Etappe V1.3b0 vor V1.3b, ja. Umgesetzt E1–E4 auf `feature/v1.3b0` (3b86eab): Engine als Fassade mit `Scheduler`/`AlarmViews`/`Changes`, Befehle in einer Transaktion, `RingPort`, `AlarmView` in core; Mutant M1 jetzt durch `EngineCommandTest` abgedeckt. Optionale Punkte (Paket `alarm`, Wochenstand, *Heute*) offen.
- 2026-11-03 (Frage 6): Titel im Diagnose-Log sind in der Entwicklungsphase erlaubt; vor einem offiziellen Release wird das ausgebaut.
