# Review-Gedächtnis
Letzter Lauf: 2026-11-12 @ 8a57338 (Lauf 4: Doku, Tests, Sitzung/Ankunft; review/2026-11-12, Fixes bis 7f16054)   Laufzähler: 4
Letztes Architektur-Review: Lauf 2 (2026-11-03); in Lauf 4 auf Kevins Fokus (Doku/Tests) verschoben, **in Lauf 5 fällig**

## Beabsichtigte Architektur
- **Schichten:** `:core` ist reines Kotlin (model, rules, ports, usecases) und kennt kein Android. `:app` ist Android (ui, alarm, data, location, system, widget, claude) und hängt von `:core` ab, nie umgekehrt. Regeln gehören nach `:core`, `:app` setzt sie nur um.
- **Änderungen:** Alle Änderungen an Remindern und Fälligkeiten laufen über `ReminderEngine`, auch aus UI, Receivern, Benachrichtigungen und der Claude-Schnittstelle. Die Engine ist eine Fassade mit internen Teilen (`Scheduler`, `AlarmViews`, `Changes`).
- **Wahrheit:** Die Room-DB und die Einstellungen im DataStore (die mit ins Backup gehen) sind die Wahrheit. Alarme, Geofences, Übersicht und Vorab-Reminder werden daraus abgeleitet und per `sync` neu aufgebaut.
- **Zeit und Container:** In `:core` kommt Zeit nur über `Clock`; in `:app` ist das ein Ziel, keine Regel. Es gibt keine DI-Bibliothek, nur den Container `AppGraph`. Engine-Aufrufe laufen nacheinander über `withEngine`.
- **Vertrauensgrenzen:**
  - adb-Provider `com.avlreminder.claude`: `DUMP`, nur im Debug-Build, Schalter in der App.
  - Exportierte Receiver und PendingIntents.
  - Migrationen (Datenverlust), mit Backup vor jeder Migration.
  - Private Inhalte (Nachweise, Positionen) dürfen nicht ins Log.

Vom Entwickler bestätigt am 2026-11-03, die Anpassungen aus Lauf 2 (Wahrheit, Zeit, Fassade) am 2026-11-09.

Das Cloud-Backup (`app/…/cloud`, `restore`) prüft der review-loop des Backend-Repos mit (Bereich „App: Wiederherstellen“, zuletzt Lauf 10 am 2026-11-09).

## Bereiche
| Bereich | Pfade | Anforderungen/Doku | Tests | Zuletzt reviewt | Zustand |
|---|---|---|---|---|---|
| core-Regeln | core/…/rules | design.md 2–10, 3.1, 6 | core/src/test/…/rules (Kover ≥ 90 %, 98,7 %) | 2026-11-12 (Sessions, ArrivalWindow, PlaceChecks) | gut, Mutanten entdeckt; Sitzung zählt seit V1.3b2 in der Woche ihres Starts (`countedAt`) |
| core-Engine | core/…/usecases | design.md 1, 3, 4 | core/src/test/…/usecases, scenarios | 2026-11-12 (Sitzung/Ankunft) | Runden bei sync behoben aabd19f; `arrivedAt` beim Bearbeiten gefixt 7248e01; F2 über die geplante erste Prüfung (3f49b58), am Gerät noch prüfen |
| core-Modell/Ports | core/…/model, ports | design.md 2 | indirekt | – | – |
| app-Alarm | app/…/alarm, system | design.md 3.1, 4, 5, 11.2, 12 | AlarmGlueTest, ReceiverGlueTest, SessionGlueTest, AlarmServiceTest | 2026-11-12 (Tests, Sitzung) | Verdrahtung seit 5f2c6a2 über echte PendingIntents getestet (E13); Schlussruf-Benachrichtigung entfernt (55c7ac2) |
| app-Daten | app/…/data | design.md 13 | MigrationTest, RoomReminderRepositoryTest, RoomRepositoryCasesTest | 2026-11-03 (nur Migration 6→7) | gut |
| app-UI | app/…/ui | design.md 11 | ui-Tests (Robolectric) | – | – |
| app-Claude | app/…/claude, app/src/debug | anforderungen.md 7.26 AG11, design.md 15 | ClaudeApiTest | 2026-11-03 | AG5-Frage offen; Freigabe-Prüfung ungetestet |
| app-Ort/Widget | app/…/location, widget | design.md 6, 11.2 | ReceiverGlueTest (PlaceCheck über echten PendingIntent) | 2026-11-12 (Ankunft, WLAN) | wiederholte WLAN-Meldung keine Ankunft (3f49b58); Geofence-Test in 5f2c6a2; Widget ohne Test |
| Doku | CLAUDE.md, README, docs/*.md | – | – | 2026-11-12 (Lauf 4, alle Dokumente) | nach Lauf 4 umgebaut: design.md nach Themen (3.1 Sitzung, 6 Ankunft), Cloud-Entwürfe → cloud-entscheidungen.md, Kap.-9-Archiv, testsession.md gelöscht |
| Tests gesamt | core/src/test, testFixtures, app/src/test | design.md 14 | – | 2026-11-12 (Lauf 4) | core sehr gut; E13–E16 umgesetzt (5f2c6a2, 247b44b, Testdaten Schema 10 am 2026-11-12); Mutationslauf: 21/21 entdeckt nach cc337b4 |

## Bekannte Schulden und beobachtete Drift
- 2026-11-03: Regel-Logik in `:app`: `AlarmActivity.canDeferRest`, das Anbieten des nächsten Punkts in `ActionReceiver`. Beginnende Drift. **Trend (Lauf 2):** Die Alarm-Ansicht gibt es viermal (`Ring`, `AlarmUi`, `AlarmInfo`, `ActionReceiver`); `ActionReceiver` nutzt `Checklists.round` nicht. Empfehlung E1.
- 2026-11-03 (Lauf 2): `ReminderEngine` 803 Zeilen mit vier Verantwortungen, höchste Änderungsdichte. Empfehlung E2 (intern teilen, Fassade behalten).
- 2026-11-03 (Lauf 2): Nachbearbeitung (`ring`, `checkPlacesAndRing`) wiederholt jeder Aufrufer; `ClaudeApi.save` prüft den Ort nicht. Empfehlung E4.
- 2026-11-03 (Lauf 2): Mehrfache Projektionen: Wochenstand zweimal (`weekStatus`, `WeeklyGoals.status`), *Heute* an vier Stellen geladen, Takt in fünf Darstellungen, Vorlagen-Mapping in der UI.
- 2026-11-03 (Lauf 2): Mutant M1 (`resume` ohne Wochenziel-Filter) unentdeckt.
- 2026-11-03: Die Android-Schicht ist dünn getestet. `AlarmService`, `AlarmActivity`, Freigabe der Claude-Schnittstelle und Provider haben keine Tests; Mutant M3 (`enabled()` ohne `debuggable`) blieb unentdeckt; seit 2026-11-11 fängt ihn `ClaudeAccessTest`, die DUMP-Prüfung des Providers `ClaudeProviderTest`; `AlarmServiceTest` deckt die Knöpfe des Dienstes ab (fand den verlorenen `nextItem`).
- 2026-11-03: Engine-Aktionen sind nicht transaktional, und `AlarmActivity` ruft sie im `lifecycleScope` auf. **Trend (Lauf 2):** wichtiger mit B2b-Backup. Empfehlung E3.
- 2026-11-03: Die Fälligkeit speichert den Zeitpunkt der nächsten Runde nicht; `plan()` rechnet ab „jetzt“.

## Bekannte Schulden und beobachtete Drift (Lauf 3)
- 2026-11-05 (**behoben fd955e4**, E5): `PLANNED` ist überladen (nie fällig vs. geschlummert/verschoben). Folgefehler B1, B2. Empfehlung E5. **Trend:** knüpft an „Fälligkeit speichert die nächste Runde nicht“ (Lauf 1) an – der Zustand einer Fälligkeit ist zu implizit.
- 2026-11-05 (**behoben fd955e4**, E6): Langsame Port-I/O (Ortsabfrage bis 8 s) in Transaktion und Mutex. Empfehlung E6.
- 2026-11-05 (**nachgezogen am selben Tag**, E9/E10): Doku-Drift: Kopfzeilen, 7.10, E.5, Kover-Wert; gültige Regeln stehen teils nur in Kap. 9. Empfehlung E9, E10.

## Bekannte Schulden und beobachtete Drift (Lauf 4)
- 2026-11-12, **Trend (Lauf 3 → 4): Doku-Drift wächst mit jeder Etappe.** Umgesetzte Entwürfe (E.3, Z.8) werden nicht in design.md eingearbeitet, Statuszeilen in sechs Dokumenten veralten. Empfehlung E11 (Version nur in stand.md, Einarbeiten als Teil von „fertig“).
- 2026-11-12, **Trend (Lauf 1 → 2 → 4): Android-Schicht dünn getestet.** Neu belegt mit 7 von 8 unentdeckten App-Mutanten (A2–A6, M4, M5); Tests bauen Intents nach, statt die erzeugten PendingIntents auszulösen. Empfehlung E13.
- 2026-11-12: Fake weicht von Room ab (Checklisten-Punkte, unbekannte Id). E14. **Behoben 247b44b** (gemeinsame `RepositoryCases`).
- 2026-11-12: Glue-Tests nutzen die echte Uhr und werden 22:30–0:30 übersprungen. E15. **Behoben 5f2c6a2** (`TestClock`).
- 2026-11-12: `testdata/` auf Schema 7. E16. **Behoben** (`real-2026-11-12.json`, Schema 10).

## Offene Fragen an den Entwickler
Keine aus Lauf 4 (alle am 2026-11-12 beantwortet).

Kleine offene Unklarheiten (Sommerzeit, Handy aus im Zeitraum, *Auslassen* bei mehrmals täglich, Begriff „Pause“) stehen in anforderungen.md Kap. 11.

## Getroffene Entscheidungen
- 2026-11-03: `.review/` liegt im Repository.
- 2026-11-03: Das Architekturbild oben ist bestätigt; 2026-11-09 die Anpassungen aus Lauf 2.
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
- 2026-11-12 (Lauf 4): Kevin wollte Doku und Tests im Fokus; das Architektur-Review ist deshalb auf Lauf 5 verschoben (Hauptagent).
- 2026-11-12 (Lauf 4, Fragen 1–8, alle nach Vorschlag): Vollbild/*+1 wartet*/*Rest später* → V1.3c; *Abgebrochen* 60 s zurücknehmbar; F2 über die geplante erste Prüfung, wiederholte WLAN-Meldung keine Ankunft; Sitzung zählt in der Woche ihres Starts; Doku zusammengelegt und gekürzt; *Teilschritt* nach aufgebrauchtem Limit ausgeblendet. Umgesetzt in V1.3b2 (anforderungen.md Kap. 9, 2026-11-12).
