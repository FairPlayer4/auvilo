# Review-Gedächtnis
Letzter Lauf: 2026-11-05 @ e31aa9f (Lauf 3: Doku-Review Anforderungen/Design, review/2026-11-05)   Laufzähler: 3
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
| core-Engine | core/…/usecases | design.md 1, 3, 4 | core/src/test/…/usecases, scenarios | 2026-11-03 (Architektur, Lauf 2); Lauf 3 Stichproben | Rundenplanung bei sync (offen); B1/B2 gefixt fd955e4 (Lauf 3, E5/E6; offen) |
| core-Modell/Ports | core/…/model, ports | design.md 2 | indirekt | – | – |
| app-Alarm | app/…/alarm, system | design.md 4, 5, 11.2, 12 | AlarmGlueTest (dünn) | 2026-11-03 | mehrere offene Minor-Funde, Service ungetestet |
| app-Daten | app/…/data | design.md 13 | MigrationTest, RoomReminderRepositoryTest | 2026-11-03 (nur Migration 6→7) | gut |
| app-UI | app/…/ui | design.md 11 | ui-Tests (Robolectric) | – | – |
| app-Claude | app/…/claude, app/src/debug | anforderungen.md 7.26 AG11, design.md 15 | ClaudeApiTest | 2026-11-03 | AG5-Frage offen; Freigabe-Prüfung ungetestet |
| app-Ort/Widget | app/…/location, widget | design.md 6, 11.2 | ReceiverGlueTest | – | Geofence-Cache (Lauf 3, minor) |
| Doku | docs/anforderungen.md, docs/design.md | – | – | 2026-11-05 (Lauf 3) | nachgezogen am 2026-11-05: Fragen beantwortet, Statuszeilen aktuell, Kap. 9 neu sortiert |

## Bekannte Schulden und beobachtete Drift
- 2026-11-03: Regel-Logik in `:app`: `AlarmActivity.canDeferRest`, das Anbieten des nächsten Punkts in `ActionReceiver`. Beginnende Drift. **Trend (Lauf 2):** Die Alarm-Ansicht gibt es viermal (`Ring`, `AlarmUi`, `AlarmInfo`, `ActionReceiver`); `ActionReceiver` nutzt `Checklists.round` nicht. Empfehlung E1.
- 2026-11-03 (Lauf 2): `ReminderEngine` 803 Zeilen mit vier Verantwortungen, höchste Änderungsdichte. Empfehlung E2 (intern teilen, Fassade behalten).
- 2026-11-03 (Lauf 2): Nachbearbeitung (`ring`, `checkPlacesAndRing`) wiederholt jeder Aufrufer; `ClaudeApi.save` prüft den Ort nicht. Empfehlung E4.
- 2026-11-03 (Lauf 2): Mehrfache Projektionen: Wochenstand zweimal (`weekStatus`, `WeeklyGoals.status`), *Heute* an vier Stellen geladen, Takt in fünf Darstellungen, Vorlagen-Mapping in der UI.
- 2026-11-03 (Lauf 2): Mutant M1 (`resume` ohne Wochenziel-Filter) unentdeckt.
- 2026-11-03: Die Android-Schicht ist dünn getestet. `AlarmService`, `AlarmActivity`, Freigabe der Claude-Schnittstelle und Provider haben keine Tests; Mutant M3 (`enabled()` ohne `debuggable`) blieb unentdeckt.
- 2026-11-03: Engine-Aktionen sind nicht transaktional, und `AlarmActivity` ruft sie im `lifecycleScope` auf. **Trend (Lauf 2):** wichtiger mit B2b-Backup. Empfehlung E3.
- 2026-11-03: Die Fälligkeit speichert den Zeitpunkt der nächsten Runde nicht; `plan()` rechnet ab „jetzt“.

## Bekannte Schulden und beobachtete Drift (Lauf 3)
- 2026-11-05 (**behoben fd955e4**, E5): `PLANNED` ist überladen (nie fällig vs. geschlummert/verschoben). Folgefehler B1, B2. Empfehlung E5. **Trend:** knüpft an „Fälligkeit speichert die nächste Runde nicht“ (Lauf 1) an – der Zustand einer Fälligkeit ist zu implizit.
- 2026-11-05 (**behoben fd955e4**, E6): Langsame Port-I/O (Ortsabfrage bis 8 s) in Transaktion und Mutex. Empfehlung E6.
- 2026-11-05 (**nachgezogen am selben Tag**, E9/E10): Doku-Drift: Kopfzeilen, 7.10, E.5, Kover-Wert; gültige Regeln stehen teils nur in Kap. 9. Empfehlung E9, E10.

## Offene Fragen an den Entwickler
Keine. Kleine offene Unklarheiten (Sommerzeit, Handy aus im Zeitraum, *Auslassen* bei mehrmals täglich, Begriff „Pause“) stehen in anforderungen.md Kap. 11.

## Getroffene Entscheidungen
- 2026-11-03: `.review/` liegt im Repository.
- 2026-11-03: Das Architekturbild oben ist bestätigt.
- 2026-11-03 (Fragen 1–4, nach Vorschlag, umgesetzt in V1.3a3): Die Schnittstelle ändert, pausiert und archiviert keinen klingelnden oder überfälligen Reminder. Nach „Handy aus“ klingelt der heute zuletzt begonnene Termin. *Notaus* ist nur neutral, wenn die Pause vor der Fälligkeit begann. Fristen bleiben im Notaus hart.
- 2026-11-03 (Frage 5): Die NT2-Karte braucht kein *Minimum*; keine Abweichung mehr.
- 2026-11-03 (Frage 7a, 7c, umgesetzt in V1.3a3): Checkliste mit mehreren Uhrzeiten läuft über die Termine, ein Punkt pro Termin; das Minimum-Limit zählt Tage.
- 2026-11-03 (Frage 7b, nach Vorschlag, umgesetzt in V1.3a3): Notaus von Hand aus klingelt sofort, wie beim automatischen Pausenende.
- 2026-11-03 (Lauf 2, Frage 2): Einstellungen kommen ins Backup B2b, als JSON neben dem DB-Abzug (`Settings.backup()`/`restore`); *Notaus* und `claudeAccess` bleiben auf dem Gerät.
- 2026-11-03 (Lauf 2, Frage 1): Umbau-Etappe V1.3b0 vor V1.3b, ja. Umgesetzt E1–E4 auf `feature/v1.3b0` (3b86eab): Engine als Fassade mit `Scheduler`/`AlarmViews`/`Changes`, Befehle in einer Transaktion, `RingPort`, `AlarmView` in core; Mutant M1 jetzt durch `EngineCommandTest` abgedeckt. Optionale Punkte (Paket `alarm`, Wochenstand, *Heute*) offen.
- 2026-11-03 (Frage 6): Titel im Diagnose-Log sind in der Entwicklungsphase erlaubt; vor einem offiziellen Release wird das ausgebaut.
- 2026-11-05 (Lauf 3, B1, B2, E5, E6, umgesetzt in V1.3b0.2, fd955e4): Termin mit `firedAt`/`awaitsArrival`; Speichern verschiebt nur nie geklungene Termine; verschobener Ankunfts-Termin wartet auf die Ankunft und ist zur Tagesgrenze *verpasst*; Ortsprüfung außerhalb der Transaktion.
- 2026-11-05 (Lauf 3, Fragen 1–7, nach Vorschlag): siehe anforderungen.md Kap. 9 (2026-11-05). Frage 6 beruhte auf einer falschen Prämisse (Fristen werden ohne und mit Notaus *verpasst* und bleiben *überfällig*); nur Klarstellung.
- 2026-11-05 (Lauf 3, E7–E10): angenommen und in der Doku umgesetzt (Entwurf 7.29, Tabelle `RUNNING` in E.3, Statuszeilen, Kap. 9 neueste zuerst).
