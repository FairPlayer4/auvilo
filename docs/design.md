# Design – avlreminder

> Beschreibt nur den **aktuellen** Stand, nach Themen. Welche Version was gebracht hat, steht
> in [stand.md](stand.md) und anforderungen.md Kap. 10.1; frühere Entwürfe und ihre
> Abweichungen im Git-Verlauf. Anforderungen: [anforderungen.md](anforderungen.md),
> Spike: [spike.md](spike.md).
> Neue Entwürfe kommen als eigener Abschnitt *Entwurf* ans Ende. Eine Etappe ist erst
> fertig, wenn ihr Entwurf in die Themen eingearbeitet und der Abschnitt gelöscht ist.

## 1. Architektur

```
┌──────────────────────────── :app (Android) ────────────────────────────┐
│  ui/        Compose-Screens, AppViewModel, MainActivity                 │
│  widget/    Glance-Widget                                               │
│  alarm/     AlarmPort, AlarmService, SessionService, AlarmActivity,     │
│             Benachrichtigungen, Übersicht, Vorab-Reminder, Kamera, Audio│
│  location/  Geofence, WLAN, Prüf-Alarme der Orte, Standortprüfung       │
│  system/    Boot/Update/Zeitänderung, Diagnose-Log                      │
│  data/      Room-DB, Repository, DataStore-Einstellungen, Vorschläge    │
│  claude/    Schnittstelle für Claude (Provider nur im Debug-Build)      │
│  cloud/, restore/  Cloud-Backup, Wiederherstellen, Tageskopien (16.)    │
└───────────────┬─────────────────────────────────────────────────────────┘
                │ nutzt
┌───────────────▼──────────── :core (reines Kotlin, kein Android) ────────┐
│  model/     Reminder, Schedule, Occurrence, SessionSpec, Arrival, …     │
│  rules/     ScheduleRules, Lifecycle, Escalation, Presets/Validation,   │
│             Proofs, WeeklyGoals, Stats, Deadlines, Blockers, Sessions,  │
│             ArrivalWindow, PlaceChecks, Checklists, Snoozing            │
│  ports/     ReminderRepository, AlarmPort, PlaceAlarmPort, GeofencePort,│
│             LocationPort, WifiPort, DeviceStatePort, PreNoticePort      │
│  usecases/  ReminderEngine (Fassade) mit Scheduler (+ Arrivals,         │
│             SessionTimer), AlarmViews, Changes, EngineContext;          │
│             AlarmView, RingPort; Overview, Digest                       │
└─────────────────────────────────────────────────────────────────────────┘
```

- **`:core` kennt kein Android.** Alle Regeln leben dort, abgesichert mit Unit-Tests
  (Kover ≥ 90 %, Build schlägt sonst fehl).
- **`ReminderEngine`** ist der einzige Weg, Reminder und Fälligkeiten zu ändern:
  Oberfläche, Benachrichtigungen, Receiver und später Agenten (Anf. 7.26) nutzen dieselben
  Befehle. Sie ist eine Fassade über drei interne Teile: `Scheduler` (Zeit:
  nächste Fälligkeit, Alarm planen, auslösen, eskalieren, Tagesgrenze; darin eigene Teile
  für Orte und Ankunft, `Arrivals`, und den Timer einer Sitzung, `SessionTimer`, E20), `AlarmViews` (was
  jetzt gilt: Limits, Woche, Strenge, Alarm-Ansicht) und `Changes` (was der Nutzer ändert:
  Reminder, Aktionen, Blocker, Hinweise).
- **Ein Befehl, eine Transaktion.** Jeder öffentliche Befehl läuft ganz oder gar nicht
  (`ReminderRepository.transaction`, in Room `withTransaction`); ein Befehl in einem
  anderen schließt sich ihm an. Die Wirkungen nach außen – **planen, klingeln und
  beenden** – laufen erst danach: Alarme über den `AlarmPort`, Klingeln über
  `RingPort.ring`, das Ende über `RingPort.end` (Klang aus, Benachrichtigung weg). Die
  Engine beendet jeden Termin, der nicht mehr klingeln soll: nach einer Aktion (außer
  „Nachweis verlassen“), wenn das System ihn schließt (verpasst, Tagesende, Ende einer
  Sitzung, Notaus-Skip; Frage 3), beim Löschen und Pausieren, und bei einem Knopf auf
  einem geschlossenen oder unbekannten Termin. Eine abgelehnte Aktion auf einem offenen
  Termin lässt ihn klingeln; was im selben Befehl wieder klingelt, wird nicht beendet. Die
  Aufrufer klingeln und beenden nicht selbst. Nach jedem Befehl (nicht nach dem Lesen)
  meldet `changed` der App, Widget und Sitzungs-Benachrichtigung neu aufzubauen (E17).
- **Die Alarm-Ansicht kommt aus `core`** (`AlarmView`): Alarm-Bildschirm, Benachrichtigung
  und der Knopf „✓ Punkt“ zeigen dieselben erlaubten Aktionen; `Ring` trägt sie mit der
  Lautstärke.
- **Wahrheit sind Room und DataStore** (Daten und Einstellungen; beides sichert das
  Cloud-Backup). Alarme, Geofences, Übersicht, Vorab-Reminder, Widget und die
  Benachrichtigungen von Sitzung und Pause sind abgeleitet und werden jederzeit daraus neu
  aufgebaut (`sync` bei Änderung, Alarm, App-Start, Neustart, Update, Zeitänderung).
- **Gerätezustand (nicht im Backup)** ist eine dritte Kategorie: Merker, die nur für dieses
  Gerät gelten, z. B. `CloudStore` (Sitzung, Sicherungsstand), die Entprellung der
  WLAN-Meldungen, Schlüsseldateien und Keystore. Ein Wiederherstellen bringt sie nicht mit;
  wer sie liest, kommt ohne sie zurecht.
- **Lesen geht über das Repository.** Auch die Flows der Bildschirme kommen aus
  `RoomReminderRepository.observe…`, mit derselben Abfrage und Umwandlung wie die Engine;
  „offen“ ist damit überall gleich. Direkt an die DAO gehen nur Vorlagen und Vorschläge,
  die keine Regeln haben. Neue Reminder starten überall mit `Presets.newReminder`
  (Formular, Schnittstelle für Claude, Widget).
- **Keine langsame I/O im Befehl.** Alarm- und Vorab-Änderungen sammelt `EngineContext`
  während des Befehls und führt sie erst nach dem Speichern aus; bei einem Rollback fallen
  sie weg. Braucht ein Befehl den Ort, wirft `EngineContext` beim ersten Mal
  `PositionNeeded`: Die Engine rollt zurück, fragt `LocationPort.check` außerhalb der
  Transaktion und startet den Befehl neu, jetzt mit dem Ergebnis. Höchstens eine Prüfung
  pro Ort und Befehl. Am Ende jedes Befehls plant `planChecks()` die Prüf-Alarme der Orte (6.).
- **Zeit kommt über die Uhr der Engine**, in `:core` als `Clock`, in `:app` als
  `AppGraph.clock` (`SystemZoneClock`). Das gilt für alles, wonach die App entscheidet:
  *Heute*-Ticks, Widget, Alarm-Bildschirm, Timer der Sitzung, Notaus-Ende, Alter des
  WLAN-Scans. Frei sind nur Log-Stempel und Dateinamen; `ClockRuleTest` hält die Liste.
  Keine DI-Bibliothek: ein kleiner Container (`AppGraph`). Engine-Aufrufe laufen
  nacheinander (`withEngine`).
- **Ein Rezept für den Neuaufbau:** `AppGraph.rebuild(reason)` macht `sync`, Pause-Hinweis
  und Übersichtszeiten, bei Neustart/Update/Zeitänderung auch die Prüfung der Orte. Jeder
  Auslöser (Start, `SystemReceiver`, Geofences verloren, Einstellungen, App im
  Vordergrund) ruft nur dieses Rezept. Regeln, was beim Speichern bleibt (*Hier
  festlegen*), und Befehle, die zusammengehören (Blocker samt Aufgabe), liegen in der
  Engine; die Cloud-Verdrahtung baut `cloud/CloudWiring` (E19).

**Tech:** AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09, Room 2.8 + KSP,
DataStore, Glance, CameraX, Play-Services-Location, Kover. minSdk = targetSdk = 37.
Paket `com.avlreminder`.

## 2. Datenmodell

### 2.1 Reminder
| Bereich | Felder |
|---|---|
| Grunddaten | `title`, `why`, `character` (`OVERCOME` Überwindung / `DOCUMENT` Dokumentation), `strictness` (`RELAXED` / `NORMAL` / `STRICT`), `active`, `archivedAt`, `createdAt` |
| Takt | `schedule` (2.2), `arrival` (Ort, Vorlauf, Puffer, Prüfabstand, 6.) |
| Ziel | `goal`, `minimum`, `minimumLimitPerWeek`, `weeklyGoal` (Ziel, höchstens, mindestens) |
| Sitzung | `session: SessionSpec?` (Nachfrage-Intervall und -Strenge, Ziel- und Minimum-Dauer, maximale Dauer, Reaktionszeit; 3.1) |
| Nachweis | `proof: ProofSpec` (5.) |
| Alarm | `postponeLimit` mit `postponePeriod` (`OCCURRENCE` / `WEEK` / `MONTH`, 4.), `snoozeMinutes` (1–2 Werte), `ignoreDnd` |
| Inhalt | `checklist`, `motivationAudio` |
| Vorab und Blocker | `preNotice` (`Evening(time)` / `Before(minutes)`), `prepFor` (Vorbereitungsaufgabe für Reminder x), `solvedBlockerIsMinimum` |

### 2.2 Takt (`Schedule`)
| Art | Felder | Beispiel |
|---|---|---|
| `Once` | Zeitpunkt | Arzt anrufen am 28.10. 09:00 |
| `Daily` | Uhrzeiten (1–6) | täglich 08:00; täglich 08:00, 13:00, 20:00 |
| `Weekdays` | Uhrzeiten (1–6), Tage | Mo–Fr 18:30 |
| `AfterDone` | Tage, Uhrzeit, erstmals am | 14 Tage nach letzter Erledigung, 19:00 |
| `OnArrival` | – | einmalig, wenn ich zuhause bin |
| `Deadline` | Frist, Tage, Fenster von–bis, Intervall | bis Fr 12:00, Mo–Fr 9–17, alle 60 min |

**Mehrmals täglich (VS1):** Weiterhin eine offene Fälligkeit pro Reminder; nach
dem Abschluss folgt die nächste Uhrzeit desselben Tages, sonst der nächste Tag. Ein
Termin gilt als **verpasst, sobald der nächste beginnt** (spätestens an der
Tagesgrenze); ein Alarm bis 2 min zu spät klingelt noch den gerade begonnenen Termin.
Wird das erst später bemerkt (Handy aus), folgt der Termin, der heute zuletzt begonnen
hat, und klingelt sofort (`ScheduleRules.currentTime`).
*Später* und *Verschieben* nur bis vor den nächsten Termin. Eine Checkliste läuft über
die Termine, ein Punkt pro Termin (8.). Das Wochenlimit für das *Minimum* zählt Tage
(`Limits.minimumToday`). Nicht kombinierbar mit Wochenziel und Ankunft (Validierung, das Formular
blendet beides aus). Vorab erledigen betrifft nur den nächsten Termin.

### 2.3 Fälligkeit (`Occurrence`)
| Bereich | Felder |
|---|---|
| Zeit | `dueAt` (geplant), `currentAt` (aktuell, nach Später/Verschieben), `postponeCount` |
| Zustand | `state` (`PLANNED`, `DUE`, `ESCALATING`, `RUNNING`, `DONE`, `CLOSED`), `resolvedAt`, `resolution` (`GOAL`, `MINIMUM`, `NO_PROOF`, `SKIPPED`, `MISSED`, `DROPPED`, `ABORTED`), `note` |
| Verlauf | `firedAt` (erstes Klingeln, `hasFired`; `PLANNED` allein sagt nicht, ob ein Termin schon geklungen hat), `pausedUntil` (Nachweis läuft), `firstProgressAt` (erster Checklistenpunkt) |
| Ort | `awaitsArrival` (wartet nach Später/Verschieben auf die Ankunft), `arrivedAt` (erkannte Ankunft im Zeitraum, 6.) |
| Sitzung | `startedAt` (bleibt nach dem Ende, für die Dauer), `nextCheckAt`, `finalCallAt` (wann der letzte Reminder hörbar kam; 3.1) |

Pro Reminder gibt es höchstens **eine offene** Fälligkeit; nach dem Abschluss legt die
Engine die nächste an. **Gezählt** (Wochenziel, Minimum-Limit, Statistik, Verlauf) wird
eine Fälligkeit an `countedAt`: eine Sitzung an ihrem Start, sonst an `resolvedAt`.

### 2.4 Weitere Tabellen
| Tabelle | Inhalt |
|---|---|
| `event` | unveränderliches Protokoll pro Fälligkeit: `CREATED`, `DUE`, `ESCALATED`, `SNOOZED`, `POSTPONED`, `COMPLETED`, `SKIPPED`, `MISSED`, `MISSED_DETECTED`, `PROOF_STARTED`, `PROOF_ABORTED`, `DROPPED`, `ITEM_DONE`, `DEFERRED`, `WINDOW_CLOSED`, `BLOCKER_ASKED`, `AWAITING_ARRIVAL`, `REOPENED` (Detail: die zurückgenommene Auflösung), `ARRIVED` (Detail: wann es klingelt), `STARTED`, `CHECK_ASKED`, `CHECK_ANSWERED`, `FINAL_CALL` (Detail `still`: nicht hörbar), `ABORTED` (Detail: die Auflösung) |
| `proof` | Foto, Text oder Audio pro Fälligkeit, optional pro Checklistenpunkt (`itemId`) |
| `checklist_item`, `item_done` | Punkte eines Reminders; erledigte Punkte pro Fälligkeit mit `withProof` |
| `prep_answer` | Startklar-Antworten, nur anhängen; aktuell ist die neueste pro Frage |
| `blocker` | Text, angelegt, gelöst am, Herkunft (`STARTKLAR` / `ASKED`), Vorbereitungsaufgabe |
| `place` | Orte (*Zuhause* und ein weiterer), Radius Standard 150 m |
| `place_wifi` | Zugangspunkte eines Orts: `bssid`, `ssid` nur zur Anzeige; privat (6.) |
| `template` | Vorlagen (11.3) |
| `suggestion` | Verbesserungsvorschläge (11.6), ohne Fremdschlüssel |

Globale Einstellungen liegen im DataStore: Darstellung, Alarmzeiten (`loudFrom` 18:00,
`dayEnd` 23:00), Nachfrage-Schwelle, Verweildauer, Übersicht, Sortierung der Liste.
Für das Backup B2b liefert `Settings.backup()` sie als JSON (`version`, `values`, Schlüssel
wie im DataStore); `restore` setzt sie zurück, Fehlendes auf den Standard. *Notaus* und
`claudeAccess` sind nicht dabei (Anf. 7.28 PS8a).

## 3. Lebenszyklus

```
PLANNED ──(Zeit/Ankunft)──► DUE ──(keine Reaktion)──► ESCALATING
  │  ▲                       │ ▲                         │
  │  │                       │ └─ Nachweis läuft (pausedUntil) ─┘
  │  └─ Später · Verschieben · Rest auf morgen · Fenster zu / Auf morgen (Frist)
  ├── Ich starte jetzt (auch vorab) ──► RUNNING ──► DONE | CLOSED (ABORTED, MISSED; 3.1)
  ├── Erledigt (auch vorab) ──► DONE (GOAL | MINIMUM | NO_PROOF)
  ├── Ort: nicht da zur Uhrzeit, nie geklungen ──► CLOSED (DROPPED, neutral)
  ├── Ort: nicht da nach Später/Verschieben ──► PLANNED + awaitsArrival ──Ankunft──► DUE
  ├── Auslassen mit Grund ──► CLOSED (SKIPPED, neutral)
  ├── Frist abgelaufen ──► CLOSED (MISSED) + neue offene Fälligkeit *überfällig* (7.)
  └── Tagesgrenze ohne Reaktion ──► CLOSED (MISSED, mit Notaus ggf. SKIPPED, 4.)
```

- Übergänge sind reine Funktionen (`Lifecycle.transition`) mit `Limits` als Kontext
  (Minimum der Woche, letzter Text-Nachweis, erledigte Punkte, Frist, gelöster Blocker).
- **Verpasst-Erkennung:** Jeder Sync prüft Fälligkeiten in der Vergangenheit. Kam kein
  Alarm (Akku, Force-Stop), wird `MISSED_DETECTED` vermerkt und sofort nachgeholt.
- System-Gründe beim Auslassen (`SkipReasons`): Wochenziel erreicht, pausiert, Takt
  geändert, verworfen, Notaus. Sie zählen nicht als „hängen“ (9.3).
- **Hat geklungen** (`firedAt`): `Fire` und die Ankunft setzen es beim
  ersten Klingeln. Ein Termin, der geklungen hat, kann nicht mehr *entfallen* (`Drop`
  wird abgelehnt).
- **Takt geändert** (`Changes.save`): Ein offener Termin, der nie geklungen hat und nie
  verschoben wurde, wandert auf die neue Zeit (oder wird mit *Takt geändert*
  ausgelassen). Ein geschlummerter oder verschobener behält seinen Tag – Speichern lässt
  den heutigen Termin nicht verschwinden (Review-Lauf 3 B1).
- **Fortsetzen** eines pausierten Reminders (`setActive`): Der offene Termin startet neu
  ab der nächsten Fälligkeit (`firedAt`, `awaitsArrival`, `pausedUntil` zurückgesetzt).
  Für die pausierte Zeit werden keine Verpasser nachgemeldet.
- **Zurücknehmen** (`ReminderEngine.reopen`, Anf. 7.31): `Action.Reopen(at)`
  macht aus `DONE` oder von Hand `SKIPPED` wieder `PLANNED` mit `currentAt = jetzt + 10 min`
  (`Changes.REOPEN_DELAY`); `postponeCount`, `firedAt` und der Fortschritt einer Checkliste
  aus früheren Haken bleiben, Ereignis `REOPENED`. `Lifecycle.canReopen` sagt, was geht.
  `Changes.reopen` lehnt ab, wenn es nicht der letzte aufgelöste Termin ist, der nächste
  schon geklungen hat oder verschoben ist, der Reminder pausiert ist oder die neue Zeit
  hinter `missedAt` des ursprünglichen Termins läge (Tagesgrenze, nächste Uhrzeit,
  Fensterende). Sonst in einer Transaktion: nächsten Termin löschen
  (`deleteOccurrence`, mit Alarm und Vorab-Reminder), Haken ab `resolvedAt` löschen
  (`deleteItemsDone`), einen dabei gelösten Blocker der Hauptaufgabe wieder öffnen,
  `syncReminder`. Nachweise bleiben; `Limits.proofCarried` (letztes `REOPENED` mit Detail
  `GOAL`/`MINIMUM`) lässt das nächste *Erledigt* ohne neuen Nachweis als Ziel zählen, die
  Alarm-Ansicht überspringt dann den Nachweis (`AlarmView.proofCarried`). Eine
  *abgebrochene* Sitzung läuft nach dem Zurücknehmen weiter (3.1).
- **Vorab verschieben und auslassen** (Anf. 7.32): `Postpone` und `Skip` gelten
  auch für einen geplanten Termin, der noch nicht geklungen hat. `Occurrence.rangOrMoved`
  (geklungen oder verschoben) ersetzt `hasFired` bei der Ortsentscheidung: Eine Ankunft vor
  der verschobenen Zeit lässt ihn nicht klingeln, zur Zeit nicht da → `AwaitArrival` statt
  `Drop`. `Occurrence.plannedAt` (nie geklungen: `currentAt`, sonst `dueAt`) ist die Basis
  für den Vorab-Reminder, er wandert also mit. NT2 zählt `POSTPONED` wie `SNOOZED`.
  Zurücknehmen eines vorab erledigten oder ausgelassenen Termins setzt ihn auf die geplante
  Zeit, wenn die später als in 10 min liegt. Die Alarm-Ansicht vorab bietet *Verschieben*
  mit +30 min/+1/+2 Std ab `currentAt`, nur vor `AlarmView.missedAt`; bei *streng* mit
  Rückfrage.
- **Rückgängig in der App** (`alarm/Undo.kt`): Nach *Erledigt*/*Auslassen* im Alarm oder
  in der Benachrichtigung, ebenso nach *Abbruch* einer Sitzung aus Benachrichtigung oder
  Uhr, eine stille Benachrichtigung mit *Rückgängig*, eigene Id,
  `setTimeoutAfter(60 s)`; das Ergebnis kommt als zweite stille Benachrichtigung. Auf
  *Heute* unter *Erledigt* *Zurücknehmen* mit Rückfrage, Ergebnis als Toast; eine
  abgebrochene Sitzung nur vor ihrer maximalen Dauer (`Lifecycle.canReopen(reminder,
  occurrence, now)`), die Rückfrage sagt dann, dass sie mit ihrem Start weiterläuft.

### 3.1 Sitzung mit Timer (Anf. 7.11, 7.20 V2)

```
PLANNED/DUE/ESCALATING ─Ich starte jetzt─► RUNNING ─Fertig──────► Nachweis? ─► DONE (GOAL)
                                            │ ▲   ├─Teilschritt─────────────► DONE (MINIMUM)
                              Noch dran ────┘ │   ├─Abbruch─────────────────► CLOSED (ABORTED)
                       Nachfrage alle x min ──┘   └─max. Dauer → letzter Reminder
                                                        └─ keine Antwort ─► MISSED | MINIMUM | ABORTED
```

- **Modell:** `SessionSpec` pro Reminder (aus = keine Sitzung): Nachfrage alle 10 min
  (2–120), Strenge der Nachfragen Standard *locker*, Ziel- und Minimum-Dauer optional,
  maximale Dauer 90 min, Reaktionszeit des letzten Reminders 5 min. Die Fälligkeit wird
  `RUNNING` mit `startedAt` und `nextCheckAt` (`Sessions`, Aktionen `Start`, `StillOn`,
  `Abort`, `FinalCall`, `EndSession`).
- **Start:** *Ich starte jetzt* beendet die Eskalation sofort; auch **vorab**, dann
  entfällt der Alarm wie beim Vorab-Erledigen.
- **Nachfragen** sind Alarme mit eigener Strenge; unbeantwortet fragen sie nach
  min(Intervall, 10 min) erneut, wie die Runden eines Alarms. Nie nach der maximalen
  Dauer; eine danach gefundene (Handy aus) ist der letzte Reminder. Vorschlag (D4):
  Ziel-Dauer erreicht → *Fertig*; ohne Ziel und Minimum-Dauer erreicht → *Teilschritt*;
  sonst *Noch dran*. Nur ein Vorschlag, nichts ist gesperrt.
- **Teilschritt** nur mit Minimum (Text oder Dauer) und solange das Minimum-Limit der
  Woche nicht aufgebraucht ist; sonst fehlt der Knopf und ein Hinweis sagt warum. Ohne
  Bildschirm (Nachfrage, Uhr, laufende Benachrichtigung) wie *Fertig* nur ohne Nachweis mit
  Foto, Text oder Audio (Review-Lauf 6, Antwort 2).
- **Eine Quelle für die Antworten (E18):** Welche Antworten in welcher Reihenfolge,
  liefert `Sessions.answers` (Vorschlag zuerst, kein *Noch dran* beim letzten Reminder,
  *Teilschritt* nur mit Minimum), ohne Bildschirm `Sessions.quickAnswers`;
  `AlarmView.sessionAnswers`, `quickDone`, `quickMinimum` und `minimumUsedUp` wenden es an.
  Bildschirm, Nachfrage, Uhr und die laufende Benachrichtigung (`SessionNote.of(AlarmView)`)
  rechnen nichts selbst nach.
- **Ende ohne Antwort** (`EndSession`, nach der Reaktionszeit): *Minimum*, wenn
  Checklistenpunkte erledigt sind; unter Notaus *abgebrochen* (ohne letzten Alarm); sonst
  *verpasst*. Der Alarm räumt die Benachrichtigung der Sitzung ab.
- **Abbruch** zählt nicht als Erfolg und bricht die Serie wie *verpasst*. Im Bildschirm mit
  Rückfrage; aus Benachrichtigung oder Uhr ohne, aber 60 s zurücknehmbar (3.): Die Sitzung
  läuft mit dem alten Start weiter, die nächste Nachfrage nach dem Intervall; nach der
  maximalen Dauer geht das nicht mehr.
- **Dauer** = `resolvedAt − startedAt`. Der Schnitt (D6) nimmt die letzten 10 mit Ziel oder
  Minimum abgeschlossenen Sitzungen; *Heute* zeigt bei erledigten die Dauer. Eine Sitzung
  zählt in Tag und Woche ihres Starts (`countedAt`, 2.3).
- **`RUNNING` gegen die anderen Regeln** (D7). Grundsatz: `missedAt` gilt nicht; eine
  Sitzung endet nur über eine Antwort oder die maximale Dauer.

  | Ereignis während `RUNNING` | Verhalten |
  |---|---|
  | Tagesgrenze | nicht verpasst; die Sitzung zählt für ihren Starttag |
  | nächster Termin (*mehrmals täglich*) | wartet; danach folgt der nächste noch nicht begonnene Termin |
  | Fensterende / Frist abgelaufen | kein `CloseWindow`, kein `Miss`; die Sitzung läuft weiter |
  | Notaus | Nachfragen still; maximale Dauer gilt, danach Minimum bzw. `ABORTED` ohne letzten Alarm |
  | „Nicht stören“ | Nachfragen still; die Reaktionszeit des letzten Reminders beginnt erst ohne DND (`FINAL_CALL` mit Detail `still`) |
  | Pausieren, Archivieren, Takt ändern | abgelehnt, die App sagt „Läuft gerade“; Titel, Warum, Ziel und Minimum darf sie ändern |
  | Schnittstelle (`save`, `setActive`, `archive`) | abgelehnt wie beim klingelnden Reminder (15.) |
  | anderer Reminder klingelt | sein Alarm läuft; die Sitzung läuft mit ihrer Benachrichtigung weiter |
  | Handy aus / Neustart | `sync` plant die nächste Nachfrage; war die maximale Dauer schon um, gilt sofort der letzte Reminder |

- **Android:** `SessionService` (Foreground-Service `systemExempted`; Sitzungen dauern zu
  lange für `shortService`) mit einer laufenden Benachrichtigung je Sitzung: Chronometer,
  am Handy höchstens drei Knöpfe (*Noch dran*, die vorgeschlagene Antwort zum Beenden,
  *Abbruch*), auf der Uhr alle vier (`WearableExtender`, zweite spiegelbare
  Benachrichtigung, weil die laufende nicht auf die Uhr kommt). *Fertig* dort nur ohne
  Foto-, Text- oder Audio-Nachweis. Der Dienst misst nichts selbst: Die App aktualisiert
  ihn nach jedem Befehl aus den laufenden Fälligkeiten; fällt er aus, bleibt eine normale
  laufende Benachrichtigung. Nachfragen und letzter Reminder kommen über den `AlarmPort`
  und den `AlarmService` (Alarm-Ansicht im Modus *Nachfrage*), neben dem Sitzungsdienst
  mit eigener Benachrichtigungs-Id. Sitzungsansicht mit großem Timer, erreichbar aus *Heute*
  (*läuft seit …*) und über die Benachrichtigung.
- Vorlagen speichern die Sitzung noch nicht.

## 4. Alarm und Eskalation

| Strenge | normal | „Nicht stören“ aktiv | … und `ignoreDnd` |
|---|---|---|---|
| `RELAXED` | Banner + Vibration | still | Banner + Vibration |
| `NORMAL` | Vollbild + Vibration (Wecker ohne Ton) | still | Vollbild + Vibration |
| `STRICT` | Vollbild + Vibration, ab `loudFrom` mit Alarmton | still | wie normal |

- **Runden:** Eine Runde klingelt 3 min (`Escalation.ROUND`); die nächste liegt 10 min
  (`REPEAT`) nach dem letzten Klingeln (`DUE`, `ESCALATED`), abgehakten Punkt oder
  *Zurücknehmen*, bis zur Reaktion; ein `sync` verschiebt sie nicht, eine verpasste Runde
  (Handy aus) klingelt sofort. Während „Nicht stören“ laufen die Runden still weiter.
- **Mehrere gleichzeitig:** Der zweite Alarm überschreibt den ersten im `AlarmService`;
  die Runden des ersten laufen weiter. *Ein Vollbild zur Zeit, +1 wartet* (Anf. 7.3) ist
  noch offen ([stand.md](stand.md)).
- **Tagesgrenze:** Ohne Reaktion gilt die Fälligkeit an `dayEnd` ihres Tages als verpasst
  (liegt sie danach, am Folgetag). Fristen haben eigene Regeln (7.), mehrere Uhrzeiten
  am Tag auch (2.2).
- **Wochenende (VS2):** `AlertSettings.weekend` (`DayTimes`: *laut ab*, Tagesgrenze) gilt
  Sa–So; `AlertSettings.on(date)` liefert die Werte des Tages, die Tagesgrenze kommt vom
  Tag, auf den sie fällt. Ohne Schalter wie Mo–Fr.
- **Notaus (PS7):** `alarmsPausedUntil` (1 Stunde, bis morgen 06:00 oder bis zum
  Einschalten, `PAUSED_FOR_GOOD`). Während der Pause klingelt alles still (`SILENT`), der
  nächste Alarm liegt am Pausenende (bzw. an der Tagesgrenze). Bleibt eine Fälligkeit
  bis zur Tagesgrenze offen, wird sie *ausgelassen* mit System-Grund *Notaus* (bricht
  keine Serie, zählt nicht als *hängen*), aber nur, wenn die Pause vor ihrer Fälligkeit
  begann (`alarmsPausedFrom`, `excusedByPause`); sonst *verpasst*. Eine
  verlängerte Pause behält ihren Beginn. Ein laufender Alarm stoppt beim Pausieren.
  Endet die Pause, klingelt eine offene Fälligkeit sofort, auch beim Einschalten von Hand
  (`ReminderEngine.resume`).
  Fristen sind hart: Eine Frist, die während der Pause abläuft, ist *verpasst* und bleibt
  *überfällig* wie ohne Pause (7.); der Notaus macht sie nicht neutral (Anf. 7.2, PS7).
- **Später** (+x min): Knöpfe pro Reminder; Knöpfe über die Tagesgrenze werden
  ausgeblendet; ohne Limit, jedes ist ein `SNOOZED`.
- **Verschieben** auf einen Zeitpunkt: zählt gegen `postponeLimit`. Ist es erreicht,
  bleiben Erledigt, Minimum und Auslassen.
- **Zeitraum des Limits** (`postponePeriod`, Z2b): pro Termin zählt `postponeCount`; pro
  Woche (Mo–So) oder Monat zählt `repo.countPostponed` die Ereignisse `POSTPONED` und die
  `DEFERRED` mit Markierung `Lifecycle.COUNTED` aller Termine des Reminders im Zeitraum um
  *jetzt* (`PostponePeriods.range`, `Limits.postponesInPeriod`). Aufgebraucht nennt
  `AlarmView.postponeAgainAt` den Beginn des nächsten Zeitraums („wieder ab Mo 02.11.“).
  Verschieben ist nicht zurücknehmbar, deshalb reichen die Ereignisse als Zählung.
- **Minimum:** ausgegraut, wenn das Wochenlimit erreicht ist (mehrere Uhrzeiten: Tage mit Minimum).
- **Startwerte der Strenge** (`Presets`, pro Reminder änderbar): locker – Quittieren,
  Verschieben unbegrenzt; normal – Quittieren, 3× Verschieben; streng – Foto, 1×
  Verschieben, Minimum 1×/Woche. Streng verlangt immer einen Nachweis.
- **Stufe hoch:** *knappes* Wochenziel (10.1) und letzter Tag einer Frist (7.) machen
  den Alarm eine Stufe strenger.

## 5. Nachweis

`ProofSpec`: `kind` (`NONE` / `CONFIRM` / `EVIDENCE`), dazu bei `EVIDENCE` kombinierbar
`photo`, `textMinChars` (ohne Leerzeichen, 1–2000), `audioMinSeconds` (1–600) und
`windowMinutes` (1–60, Standard 10).
- `:core` prüft Text (Mindestzeichen, nicht nur ein Zeichen wiederholt, nicht gleich dem
  letzten Text) und Audio-Dauer. Fehlt etwas, geht *ohne vollständigen Nachweis
  abschließen* → `NO_PROOF` (zählt als nicht erfolgreich).
- **Alarm pausiert während des Nachweises:** `StartProof` setzt `pausedUntil` = jetzt +
  `windowMinutes` (höchstens bis zur Tagesgrenze). Zurück oder App verlassen
  (`AbortProof`) → Alarm in 1 min; bei der Rückkehr startet die Frist neu.
- Fotos (CameraX) in `filesDir/proofs/`, Audio (`MediaRecorder`, AAC) in
  `filesDir/audio/` – nur im App-Speicher, nicht in der Galerie.

## 6. Ort: Ankunft im Zeitraum (Anf. 7.29)

- `Reminder.arrival = Arrival(placeId, whenAway, leadMinutes, bufferMinutes, checkMinutes)`:
  **Der Takt bestimmt den Tag, die Ankunft den Moment.** Die Uhrzeit des Takts ist die
  *späteste Uhrzeit*; `whenAway` ist *entfällt* (`DROP`, Standard) oder *trotzdem
  klingeln* (`RING`). Nicht bei Fristen und nicht mit mehreren Uhrzeiten (2.2). Orte:
  *Zuhause* und ein weiterer (`place`), je mit Radius und WLANs (`place_wifi`).
- **Zeitraum** = [`currentAt` − Vorlauf, `currentAt`] (Vorlauf Standard 90 min, bis 12 h),
  solange `arrivedAt` leer ist. Eine erkannte Ankunft im Zeitraum (`ArrivalWindow.decide`,
  Ereignis `ARRIVED`) setzt `arrivedAt` und legt `currentAt` auf Ankunft + Puffer (0–30,
  Standard 5 min); liegt das schon hinter jetzt, klingelt es sofort. Kurz wieder weg ändert
  daran nichts. *Später*, *Verschieben*, *Rest auf morgen*, Fenster schließen,
  Zurücknehmen und Klingeln leeren `arrivedAt`. Bearbeiten leert es, wenn sich die
  Fälligkeit oder die Ankunftsregel ändert (Ort, Puffer, Vorlauf, Prüfabstand, *wenn weg*,
  Regel weg): Der Termin geht auf seine Uhrzeit zurück, die nächste Prüfung entscheidet neu.
  War er schon verschoben oder geschlummert, gibt es diese Uhrzeit nicht mehr: Er wartet
  wieder auf die Ankunft, jetzt nach der neuen Regel (OZ5, Review-Lauf 6); ohne *entfällt*
  oder ohne Regel klingelt er zu seiner Zeit. Reine Textänderungen lassen es (Review-Lauf 5).

  | Wann | da | klar weg | unklar |
  |---|---|---|---|
  | Ankunft vor dem Zeitraum (Geofence, WLAN) | ignoriert, Diag „too early“ | – | – |
  | erste Prüfung des Zeitraums | klingelt zur spätesten Uhrzeit (F2) | weiter prüfen | weiter prüfen |
  | im Zeitraum (Prüfung, WLAN, Geofence) | `arrivedAt`, Alarm nach dem Puffer | weiter prüfen | weiter prüfen |
  | späteste Uhrzeit (Alarm des Termins, mit Rückfall) | klingelt | `DROP`: entfällt (F1); `RING`: klingelt | klingelt |
  | nach Später/Verschieben (`awaitsArrival`) | Alarm nach dem Puffer | weiter prüfen | weiter prüfen; Tagesgrenze → verpasst |

- **F2:** Findet die Prüfung, die für den Beginn des Zeitraums geplant war, einen schon da
  (Homeoffice), klingelt es zur spätesten Uhrzeit, auch wenn Android sie verspätet zustellt:
  Der Prüf-Alarm trägt seine geplante Zeit (`plannedAt`). Als Prüfung zum Beginn zählt
  auch eine, die davor geplant war und erst im Zeitraum kommt (zwei Reminder an einem Ort,
  eine Prüfung pro Ort, Doze; Review-Lauf 5). Eine Verbindung oder ein Geofence ist dagegen
  eine echte Ankunft (Puffer).
- **Prüfungen** im Raster Beginn + k·Prüfabstand (15–30, Standard 20 min), jeweils vor der
  spätesten Uhrzeit; die späteste Uhrzeit prüft der Alarm des Termins selbst. Wartende
  verschobene Termine prüfen ab `currentAt` bis zur Tagesgrenze. Mehrere Reminder am Ort:
  Die früheste fällige Prüfung gilt. `planChecks()` setzt am Ende jedes Befehls je Ort einen
  Prüf-Alarm (`PlaceAlarmPort`) oder löscht ihn: die erste Prüfung eines Zeitraums exakt
  auch im Doze (`setExactAndAllowWhileIdle`, sie entscheidet F2), die übrigen `setWindow`
  2 min. `PlaceCheckReceiver` ruft `onPlaceCheck`; das Diagnose-Log nennt eine Verspätung
  (`late=…min`).
- **Ortsergebnis** (`PlaceCheck` mit Quelle und Alter der Suche; `WifiLocation` aus dem
  `WifiPort`): verbunden mit einer BSSID des Orts → `AT`; Ort ohne WLAN oder keine Suche →
  `UNKNOWN`; WLAN des Orts nicht in einer Suche ≤ 10 min → `AWAY`, älter → `UNKNOWN`. Nur
  zur spätesten Uhrzeit (`check(fallback = true)`) fällt `UNKNOWN` auf die aktive Prüfung
  zurück (Geofence-Stand, ein frischer Fix, höchstens 8 s): sicher drin → Alarm, sicher
  draußen → `DROPPED`, unklar → Alarm. Kein GPS (Anf. 7.30, später). Die Prüfung läuft
  außerhalb der Transaktion (1., `PositionNeeded`).
- **Vorlauf 0** = kein Zeitraum: kein Puffer, keine Prüfungen; die Ankunft klingelt die
  heutige, noch geplante Fälligkeit vor ihrer Uhrzeit sofort. *Wenn ich ankomme*
  (`OnArrival`) klingelt bei Ankunft sofort; eine WLAN-Verbindung zählt wie eine Ankunft.
- **Nach Später oder Verschieben** (OZ5): Hat der Termin schon geklungen oder ist er
  verschoben (`rangOrMoved`) und ist man zur neuen Zeit nicht sicher da (*weg* oder
  *unklar*), wird er nicht `DROPPED`, sondern wartet (`AwaitArrival`, `awaitsArrival`,
  Ereignis `AWAITING_ARRIVAL`). Die Ankunft lässt ihn nach dem Puffer klingeln; sein Alarm
  liegt an der Tagesgrenze, dort wird er *verpasst*. *Heute* zeigt ihn unter *Wenn du
  zuhause bist* („verschoben – klingelt bei Ankunft, sonst heute verpasst“).
- **WLAN:** `AndroidWifi.watch` registriert bei jedem Prozessstart einen `NetworkCallback`
  mit PendingIntent auf `WifiReceiver` (ruft `onWifi`). Android meldet dabei eine schon
  bestehende Verbindung erneut; `WifiConnections` merkt sich das Handle der zuletzt
  gemeldeten Verbindung (`Network.networkHandle`), und nur eine neue Verbindung zählt als
  Ankunft. Suchergebnisse nur über `getScanResults`, nie `startScan`. BSSID und SSID gibt es
  nur mit Standort-Berechtigung, im Hintergrund mit „Immer erlauben“; eine geschwärzte
  BSSID gilt als keine.
- **Geofence** *Dwell* pro benötigtem Ort, Verweildauer 1/3/5/10 min (Standard 3, nur für
  ihn); ohne „Immer erlauben“ zählt nur die Uhrzeit. Neu registriert, wenn Orte (nur Kreis,
  nicht WLAN) oder Verweildauer sich ändern und nach Neustart. `AndroidGeofences` merkt sich
  im Prozess, was registriert ist. Meldet ein Geofence `GEOFENCE_NOT_AVAILABLE` (Standort
  aus), vergisst die App das und registriert sofort neu (`GeofenceReceiver.meaning`,
  `lost`); klappt das nicht, versucht es der nächste Sync.
- **Oberfläche:** Formular *Bei Ankunft* mit Ortswahl (ab zwei Orten), *Ankunft zählt ab*
  30/60/90/120 min/*andere…* und *entfällt*/*trotzdem klingeln*; Puffer und Prüfabstand
  unter *Feineinstellungen*. Einstellungen *Orte*: *Hier festlegen* übernimmt Standort und
  das verbundene WLAN mit allen Zugangspunkten derselben SSID aus der letzten Suche; was
  gerade fehlt, bleibt wie es war, ein neuer Ort braucht den Standort
  (`ReminderEngine.setPlaceHere`).
  *Heute*: „ab 18:00 bei Ankunft zuhause, sonst 19:30“ bzw. „angekommen – klingelt 18:35“.
- **Privat:** WLAN-Namen und BSSIDs bleiben auf dem Handy. Das Diagnose-Log nennt je
  Prüfung Ort-Id, Quelle, Ergebnis und Alter der Suche, keine Koordinaten und keine WLANs;
  die Claude-Schnittstelle kennt Vorlauf, Puffer und Prüfabstand, aber keine WLANs; der
  Testdaten-Export ersetzt SSIDs und BSSIDs.

## 7. Fristen

`Schedule.Deadline(due, days, from, until, intervalMinutes)`; Formular: *Frist am …
um …*, Fenstertage, *von / bis*, Intervall (Standard Mo–Fr 9–17, 60 min).
- **Eine** Fälligkeit: Sie klingelt zu Beginn jedes Fensters und dann im Intervall.
  Außerhalb der Fenster ist Ruhe. Neu oder mit verschobener Frist muss vor der Frist
  mindestens ein Fenster liegen.
- **Fensterende** ohne Erledigung: still bis zum nächsten Fenster (`WINDOW_CLOSED`),
  zählt nicht als Verschieben. **Auf morgen** im Alarm: dasselbe ab morgen.
  *Verschieben* nur bis zur Frist.
- **Letzter Fenstertag:** eine Stufe strenger.
- **Nach der Frist** bleibt die Fälligkeit offen, steht in *Heute* unter *Überfällig*
  und klingelt in den folgenden Fenstern weiter, bis sie erledigt oder *verworfen* ist
  (ausgelassen mit Grund *verworfen*, mit Rückfrage).

## 8. Checklisten

- Bis 20 Punkte. Punkte werden einzeln abgehakt (`CheckItem`), auch vorab und jederzeit
  aus *Heute* und dem Reminder (Anf. 7.13 C8). Der letzte
  schließt: `GOAL`, wenn jeder Punkt seinen Nachweis hat, sonst `NO_PROOF`. *Alles
  erledigt* schließt mit einem Nachweis für den Rest; *Minimum* schließt mit `MINIMUM`,
  ohne die Punkte abzuhaken.
- **Über mehrere Tage:** Wurde an einem Tag ein Punkt erledigt, kommt der Rest am
  nächsten Tag zur selben Uhrzeit wieder (zählt nicht als Verschieben); sonst verpasst.
  *Rest auf morgen* im Alarm ist nach einem Punkt am selben Tag frei, sonst eine
  Verschiebung (dann trägt das `DEFERRED`-Ereignis die Markierung für das Limit pro Woche
  oder Monat, 4.).
- **Mehrere Uhrzeiten am Tag:** Die Liste läuft über die Termine. Ein
  abgehakter Punkt schließt den Termin (`GOAL` bzw. `NO_PROOF` nach dem Nachweis dieses
  Punkts), der nächste Termin zeigt nur die offenen Punkte. Erledigt ist, was seit dem
  letzten vollständigen Durchlauf abgehakt wurde (`Checklists.round` über
  `itemsDoneOfReminder`, für *Heute* `Checklists.doneByOccurrence`); danach beginnt die Liste von vorn. Ein Termin ohne Punkt ist
  *verpasst*, kein *Rest auf morgen*. Im Alarm: „Erledigt: ‹nächster Punkt›“, „Danach:
  Rest um …“, klein *Alles auf einmal erledigt*.
- *Nach Erledigung* zählt ab dem **ersten** erledigten Punkt (`firstProgressAt`).
- Benachrichtigung: „✓ nächster Punkt“ nur ohne Nachweis; danach eine stille
  Benachrichtigung mit dem nächsten Punkt.
- Bearbeiten: Gebliebene Punkte behalten Id und Fortschritt; entfernte nehmen ihren
  Fortschritt mit, ihre Nachweise bleiben. Punkte mit fremder Id werden neu angelegt; eine
  unbekannte Reminder-Id speichert nichts (`ReminderRepository.saveReminder`, für Fake und
  Room mit denselben `RepositoryCases` geprüft, 14.).

## 9. Startklar, Blocker und Nachfragen

### 9.1 Startklar-Fragen
Vier Fragen (Anf. 7.21): Was hast du davon? Was könnte dich abhalten? Kannst du sofort
starten, sonst was fehlt? Erster Schritt? Antwort *weiß ich noch nicht* ist erlaubt.
Pflicht bei *Überwindung* beim Speichern; gespeichert werden nur geänderte Antworten.
Der Alarm zeigt Ziel, ersten Schritt und offene Punkte; dazu das Motivations-Audio auf
Tipp.

### 9.2 Blocker
- Eigene Liste pro Reminder. Eine Antwort auf Frage 3, die etwas nennt, wird zum
  Blocker (Herkunft *Startklar*); *Ja, sofort* löst die Startklar-Blocker. Im Formular
  unter *Blocker*: offene Blocker, *gelöst*, neue eintragen.
- **Als Aufgabe planen (S5):** lockerer Einmal-Reminder (*Dokumentation*) mit
  `prepFor`, vorgeschlagen am Vorabend der nächsten Fälligkeit um 19:00. Erledigt →
  Blocker gelöst; *gelöst* von Hand löscht die Aufgabe. Aufgaben stehen in *Heute*, nicht
  in der Reminder-Liste.
- **Blocker gelöst als Minimum (S7):** pro Reminder einschaltbar. Wurde seit der letzten
  Erledigung ein Blocker gelöst, bietet der Alarm *Blocker gelöst – zählt als Minimum*;
  zählt wie ein Minimum inkl. Wochenlimit.
- Der Alarm zeigt offene Blocker als *Noch offen*, der Vorab-Reminder (11.2) ebenso.

### 9.3 Nachfragen, wenn es hakt (S8)
Nur bei *Überwindung*, nicht bei Vorbereitungsaufgaben und nicht beim Vorab-Erledigen.
Gezählt werden aufeinanderfolgende Fälligkeiten, die *hängen*: verpasst, ausgelassen (ohne
System-Grund), verschoben, oder 3× *Später* in einer Fälligkeit. Eine Erledigung beendet
die Serie. Ab der Schwelle (Standard 2, Einstellungen 1–5) fragt der Alarm einmal pro
Fälligkeit *Was hält dich gerade ab?* (`BLOCKER_ASKED`). Antwort → Blocker (Herkunft
*Nachfrage*), auf Wunsch gleich als Aufgabe; überspringbar.

## 10. Ziele über die Zeit

### 10.1 Wochenziel
`WeeklyGoal(target, max, minimum)`, nur bei *täglich* und *Wochentage*;
`minimum ≤ target ≤ max ≤ Angebotstage`. Woche Mo–So in der Zone des Geräts.
- Zählen: *Ziel* und *Minimum*, nicht *ohne Nachweis*; maßgeblich ist `countedAt` (2.3).
- **Ziel erreicht:** weitere Angebote nur als stille Benachrichtigung; offen bis zur
  Tagesgrenze → *ausgelassen – Wochenziel erreicht*.
- **Höchstens erreicht:** nächstes Angebot erst nächste Woche.
- **Knapp:** restliche Angebotstage inkl. heute ≤ fehlende → eine Stufe strenger.
- **Rettung:** fehlt das Minimum, Sa und So 10:00 *streng*, nur bei Wochentags-Plänen
  ohne diese Tage; ignoriert *entfällt*.

### 10.2 Serien und Statistik
- **Serien:** *Tage* bei täglich/Wochentagen ohne Wochenziel (Joker pro Kalenderwoche),
  *Wochen* mit Wochenziel (Woche zählt mit erreichtem Minimum; Joker pro Monat), *Mal* bei
  *nach Erledigung* (mehr als einen Tag zu spät = Verpasser; Joker pro Monat).
  Ausgelassen, entfallen und ohne Nachweis halten die Serie. Mit mehreren Uhrzeiten
  zählt ein Tag, sobald ein Termin *Ziel* oder *Minimum* hat (sonst verpasst, wenn einer
  verpasst ist); die Statistik zählt Termine.
- **Hinweis bei ständigem *Später* (NT2, `Snoozing`):** Hatten die letzten 3 abgeschlossenen
  Fälligkeiten je mindestens 2× *Später* (oder eine 4×), zeigt *Heute* eine Karte: neue
  Uhrzeit (Median der Erledigungszeiten, auf 15 min gerundet, nur wenn später als jetzt),
  Strenge eine Stufe senken, *Nein danke* (14 Tage Ruhe). Jede Antwort setzt
  `hintQuietUntil`, ab dann wird neu gezählt. Nur eine Uhrzeit am Tag (täglich,
  Wochentage, nach Erledigung), aktiv, keine Vorbereitungsaufgabe. *Verschieben* zählt
  nicht mit.
- **Statistik** pro Reminder (beim Bearbeiten *Statistik und Serie*): Ziel, Minimum, ohne
  Nachweis, vorab, ausgelassen, entfallen, verpasst für Woche, Monat, Jahr, gesamt;
  aktuelle und beste Serie.

## 11. Bedienung

### 11.1 Bildschirme
Klickbare Mocks (Stand V1.0, inzwischen veraltet; maßgeblich ist die App):
https://claude.ai/artifact/PXA9YgWi8XoHupYnN7Z3cS
- **Heute:** Läuft (Sitzungen), Jetzt fällig, Überfällig (Fristen), Später heute (mit
  *Erledigen* = vorab), Wenn du zuhause bist, Diese Woche (Wochenziele, knappe
  hervorgehoben), Erledigt.
- **Verlauf:** Erledigungen nach Tagen mit Ergebnis und Nachweisen; „vor dem Reminder
  erledigt“ aus `resolvedAt < dueAt`.
- **Reminder:** Gruppen *aktiv*, *Pausiert*, *Abgeschlossen* (archiviert oder ein
  Einmal-/Ankunfts-Reminder ohne offene Fälligkeit, eingeklappt). Sortierung nächste
  Fälligkeit (Standard), Name, zuletzt angelegt. Archivieren pausiert;
  Wiederherstellen lässt pausiert.
- **Formular:** Was, Charakter, Wann, Wie streng; der Rest klappbar mit Zusammenfassung
  in der Kopfzeile (Wochenziel, Ziel und Minimum, Vorab-Reminder, Blocker, Checkliste,
  Nachweis, Warum und Botschaft, Startklar, Feineinstellungen). Zahlen als Chips plus
  *andere…*, ihre Grenzen aus den Konstanten in `:core` (`ProofSpec`, `Arrival`,
  `SessionSpec`, …), die auch die Prüfung nutzt. Was die Engine beim Speichern ablehnt,
  steht unter dem festen Kopf, also auch weit unten im Formular sichtbar. Der Entwurf
  übersteht Design-, Schriftgrößenwechsel und Prozesstod (`rememberSaveable`,
  `ReminderForm` ist `Serializable`); gebaut wird das Formular erst, wenn Reminder,
  Startklar-Antworten und Orte geladen sind (`AppState.formReady`). Das Wochenlimit des
  Minimums gilt auch für die Minimum-Dauer einer Sitzung. Eine Startklar-Antwort lässt sich
  überschreiben, nicht leeren: Ein geleertes Feld sagt, dass die bisherige bleibt. Eine
  Ankunft ohne Ort fällt nicht still weg, die Engine nennt, was fehlt (Review-Lauf 5).
- **Alarm (Vollbild, immer dunkel):** Titel, Warum, Botschaft, Startklar, Blocker,
  Woche, Ziel/Minimum, Checkliste; Ich starte jetzt, Erledigt, Minimum, Blocker gelöst,
  Rest auf morgen, Auf morgen, Später, Verschieben, Auslassen; Nachweis als eigener
  Schritt. Als *Nachfrage* einer Sitzung die Antworten statt Erledigt/Später.
- **Sitzung:** großer Timer, die vier Antworten, die vorgeschlagene hervorgehoben (3.1).
- **Einstellungen:** Vorschläge, Darstellung, Alarmzeiten, Nachfragen, Vorlagen,
  Übersicht, Zuhause, Berechtigungen. Zahnrad auf allen Tabs.

### 11.2 Benachrichtigungen, Uhr, Widget
- **Alarm-Kanäle:** Vollbild über Foreground-Service; zusätzlich eine normale
  Benachrichtigung mit denselben Aktionen für die Uhr (Kanal *Alarm auf der Uhr*, ohne
  Ton), die laufende ist `setLocalOnly`.
- **Übersicht:** stille Benachrichtigung morgens (heute) und abends (Rückblick,
  Wochenziele, morgen; jede Uhrzeit eines Reminders einzeln, Anf. O1),
  Standard 7:30 und 20:30, einzeln *aus*, Sa–So mit eigenen
  Zeiten (VS2, derselbe Schalter wie bei den Alarmzeiten). Inexakter Alarm
  (`setWindow`, 10 min), Inhalt beim Auslösen aus der DB.
- **Notaus (PS7):** dauerhafte stille Benachrichtigung *Alle Alarme pausiert …* mit
  *Wieder einschalten*, verschwindet am Pausenende (`setTimeoutAfter`); dazu ein rotes
  Banner auf *Heute*. Einschalten in den Einstellungen ganz oben, mit Rückfrage.
- **Vorab-Reminder (T6, S6):** *am Vorabend* (Uhrzeit) oder *x min vorher*, nicht bei
  *wenn ich zuhause bin*. Stiller Kanal, inexakt (`setWindow`, 5 min), mit erstem Schritt
  und offenen Blockern; entfällt, wenn schon erledigt. Tipp öffnet den Reminder.
- **Sitzung:** laufende Benachrichtigung des `SessionService` und eine spiegelbare für die
  Uhr (3.1).
- **Widget** (Glance, folgt dem System-Modus): *+ Reminder* und nächste Fälligkeit.

### 11.3 Vorlagen
Übernehmen Charakter, Strenge, Nachweis, Minimum mit Limit, Später-Knöpfe,
Verschiebe-Limit mit Zeitraum, *Nicht stören*-Ausnahme und die Art des Takts mit Zeiten (auch
mehrere Uhrzeiten). Nicht: Titel,
Warum, Startklar, Botschaft, Datum. *+* und Widget zeigen die Auswahl (zuletzt benutzte
oben, dazu *Leer*), sofern es Vorlagen gibt. Umbenennen und Löschen in den Einstellungen.

### 11.4 Darstellung
*System* (Standard), *Hell*, *Dunkel*; sofort wirksam. Ein `AvlTheme` mit eigenen Tokens
(`AvlColors`), keine festen Farben in Screens; kein Dynamic Color, damit die
Strenge-Farben gleich bleiben. Alarm immer dunkel. Text-Kontrast ≥ 4,5 : 1.

| Token | Hell | Dunkel |
|---|---|---|
| Hintergrund | `#F5F6F8` | `#101216` |
| Fläche | `#FFFFFF` | `#1B1E24` |
| Text | `#16191F` | `#ECEEF2` |
| Text gedämpft | `#5B6270` | `#A9B0BC` |
| Linie | `#E2E5EA` | `#2E323B` |
| Akzent (Text, Rahmen) | `#1F4FD1` | `#8AA8FF` |
| Akzent (Fläche, weiße Schrift) | `#1F4FD1` | `#2F5FE0` |
| Streng / verpasst | `#B4400A` | `#FF9A62` |

### 11.5 Einstieg (Anf. 7.28 PS15)
`ui/screens/Intro.kt`, Route `intro` ohne Tabs: vier Seiten (*Willkommen*,
*Berechtigungen*, *Probealarm*, *Dein erster Reminder* mit *Rückmeldung*), oben
*Überspringen* und „1 / 4“, unten *Zurück* und *Weiter* bzw. *Fertig*. Die Berechtigungen
sind dieselbe Liste wie in den Einstellungen (`PermissionRow`), die nötigen zuerst, die
übrigen unter „Später“; nach dem Erteilen in Androids Einstellungen aktualisiert
`onResume` die Liste. Von selbst kommt der Einstieg, wenn `introSeen` in den Einstellungen
fehlt und die Reminder geladen und leer sind (`AppState.intro`, in `MainActivity`); jedes
Verlassen setzt `introSeen`. *Einstellungen → Hilfe → Einstieg zeigen* öffnet ihn immer.
Der Probealarm (`AppViewModel.probeAlarm`) speichert über die Engine einen Einmal-Reminder
„Probealarm“ (normal, *Dokumentieren*) zwei Minuten nach der nächsten vollen Minute; lehnt
die Engine ab, sagt es der Hinweis unten wie bei anderen abgelehnten Aktionen.

### 11.6 Verbesserung vorschlagen
Glühbirne oben rechts auf jedem Bildschirm, im Alarm ein Textknopf ganz unten (der Alarm
läuft weiter). Gespeichert mit Bildschirm, Reminder, App-Version; Status offen /
umgesetzt / verworfen. Liste in den Einstellungen, *Teilen* als Markdown.

## 12. Android-Anbindung

| Thema | Lösung |
|---|---|
| Alarm | `setAlarmClock()` für normal/streng, `setExactAndAllowWhileIdle()` für locker; Übersicht und Vorab-Reminder mit `setWindow()`; Prüf-Alarme der Orte exakt bzw. `setWindow()` (6.) |
| Exakte Alarme | `SCHEDULE_EXACT_ALARM` (Anf. 7.28 PS10), vom Nutzer in *Wecker und Erinnerungen* erteilt und entziehbar. Alles Exakte läuft über `ExactAlarms.set`: ohne Erlaubnis `setAndAllowWhileIdle()` (kommt auch in Doze, bis zu einer Stunde zu spät), Eintrag im Diagnose-Log und eine Benachrichtigung „Erlaubnis fehlt“. Erteilen schickt `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` an den `SystemReceiver` (Neuaufbau, Benachrichtigung weg); Entziehen beendet die App, und der nächste Start plant ungenau |
| Dauer-Alarm, Sitzung | Foreground-Services Typ `systemExempted` (`AlarmService`, `SessionService`); der Typ braucht exakte Alarme, ohne Erlaubnis starten sie nicht, sondern zeigen eine Benachrichtigung |
| Vollbild | Full-Screen-Intent + Activity mit `showWhenLocked`/`turnScreenOn` |
| Was vom Alarm bleibt | Löschen, Pausieren, Archivieren und Verwerfen aus der App nehmen Klingeln und Benachrichtigung des offenen Termins weg, ebenso verpasst, Tagesende und Notaus-Skip; das macht die Engine (`RingPort.end`, Abschnitt 1), eine abgelehnte Änderung nicht. *Notaus* stoppt das Klingeln und lässt eine stille Benachrichtigung, wie nach einer Runde. Ein anderer Alarm, während ein Nachweis offen ist, beendet diesen Nachweis (N0) |
| Neuaufbau | `sync` bei Änderung, Alarm, App-Start, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, Zeit-/Zonenänderung, Erlaubnis für exakte Alarme erteilt |
| Ort | WLAN (`NetworkCallback` mit PendingIntent, letzte Suchergebnisse), Play-Services Geofencing (Dwell), Fused Location für die aktive Prüfung |
| Berechtigungen | Seite in den Einstellungen, Warnung auf *Heute*: Wecker und Erinnerungen, Benachrichtigungen, Vollbild; ohne Warnung (empfohlen bzw. bei Bedarf): Akku, Kamera, Mikrofon; Standort „Immer“ und DND-Ausnahme nur, wenn ein Reminder sie braucht. *Akku „Nicht eingeschränkt“* öffnet die App-Info, nicht Androids direkten Dialog (`Battery`, Anf. 7.28 PS12; ohne `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). *Standort „Immer erlauben“* zeigt erst den eigenen Hinweis (`LocationDisclosure`, Anf. 7.28 PS13); erst *Weiter* fragt Android, ohne Standort zuerst den genauen, sonst den im Hintergrund. Fehlt *Wecker und Erinnerungen* (Kernfunktion), sagt *Heute* statt der allgemeinen Warnung, was dann nicht geht, und das Antippen öffnet direkt Androids Schalter |

## 13. Daten, Migration, Diagnose

- **Room** mit exportierten Schemas (`app/schemas/`), aktuell Version 11. Jede Version hat
  eine explizite Migration (`AvlDatabase.MIGRATIONS`), kein destruktiver Fallback;
  Migrationen möglichst nur mit `ADD COLUMN` und neuen Tabellen.
- **Fremdschlüssel mit `ON DELETE CASCADE`:** `occurrence`, `blocker`, `checklist_item`,
  `prep_answer` → `reminder`; `event`, `proof`, `item_done` → `occurrence`; `item_done` →
  `checklist_item`. Wer eine Tabelle neu aufbaut (kopieren, löschen, umbenennen), schaltet
  vorher `PRAGMA foreign_keys=OFF`, prüft danach `PRAGMA foreign_key_check` und schaltet
  wieder ein – sonst löscht das `DROP TABLE` alle abhängigen Zeilen. Migration 5→6 übernimmt
  die neueste Antwort auf Frage 3 als Blocker. Migration 6→7: `reminder.moreTimes` und
  `template.moreTimes` (weitere Uhrzeiten als Minuten, z. B. `780,1200`; die erste bleibt
  in `timeMinutes`), `reminder.hintQuietUntil`. Migration 7→8:
  `occurrence.firedAt` (aus dem ersten `DUE`-Ereignis nachgetragen) und
  `occurrence.awaitsArrival`. Migration 8→9: `reminder.arrivalLead`/`arrivalBuffer`/
  `arrivalCheck` mit den Standardwerten (bestehende Ankunfts-Reminder bekommen den
  Zeitraum, *Heute* zeigt einmal einen Hinweis), `occurrence.arrivedAt`, Tabelle
  `place_wifi`. Migration 9→10: `reminder.session…` (leer = keine Sitzung),
  `occurrence.startedAt`/`nextCheckAt`/`finalCallAt`. Migration 10→11:
  `reminder.postponePeriod` und `template.postponePeriod` (Standard `OCCURRENCE`, Z2b).
  Ereignistypen und Auflösungen stehen
  als Name in der Datenbank; neue brauchen keine Migration.
- **Einstellungen** (DataStore): Wochenend-Zeiten als eigene Schlüssel (fehlen = wie
  Mo–Fr), `alarmsPausedUntil` und `alarmsPausedFrom` in Millisekunden (fehlt = keine
  Pause bzw. Beginn unbekannt), `claudeAccess` (1 = Schnittstelle für Claude an, Kap. 15).
- **Backup vor jeder Migration** (Schutz gegen Datenverlust beim Update, kein
  Nutzer-Backup, Anf. 8.4) per `VACUUM INTO` nach
  `filesDir/backup/avlreminder-v<alt>-<Zeit>.db`; scheitert es, startet die App trotzdem.
- **Beschädigte Datenbank:** Die App löscht sie nie (anders als Androids Standard).
  `KeepDamaged` lässt die Datei stehen, nur die Abfrage auf der kaputten Seite scheitert;
  die Rohdateien kommen einmal am Tag nach `filesDir/backup/beschaedigt-<Tag>/` zur Rettung.
- **Diagnose-Log** (`Diag`): logcat-Tag `AvlDiag` und `files/diag/diag.log` (rotiert bei
  512 KB). Zeiten, IDs, Titel, Zustände, Berechtigungen; keine Nachweis-Inhalte, keine
  Positionen. Titel nur in der Entwicklungsphase; vor einem offiziellen Release
  werden sie entfernt (Entscheidung 2026-11-03).
- Alle Daten bleiben auf dem Gerät; nur das Cloud-Backup bringt sie, wenn es
  eingeschaltet ist, Ende-zu-Ende verschlüsselt zum Backend (Anf. 8.4, 16.).

## 14. Tests und Qualität

| Bereich | Test-Art | Ziel |
|---|---|---|
| `:core` | JUnit, Fake-Clock, Fake-Repository | Kover ≥ 90 % (aktuell 98,7 %) |
| `:core` Szenarien | `scenarios/Simulation`: ganze Tage mit Uhr, Alarmen, Ort und Handlungen; prüft nach jedem Schritt die Invarianten | je gemeldetem Fehler ein Tag |
| `:app` data | Robolectric + Room, `MigrationTest` gegen die Schemas; `RoomRepositoryCasesTest` | jede Migration, Backup |
| `:app` echte Daten | `RealDataTest` mit `testdata/real-*.json`: Migration, Alarm-Ansicht, eine Woche mit allen Remindern | bei jeder neuen Version neu ziehen |
| `:app` Verbindung | `ReceiverGlueTest`, `AlarmGlueTest`, `SessionGlueTest`: Broadcast (Alarm, Knopf, Ankunft, WLAN, Geofence, Neustart) → Engine mit echter Room-DB → Benachrichtigung, Dienst, Alarm; feste Uhrzeit über `TestClock` | jeder Receiver und Knopf |
| `:app` ui | Compose-UI-Tests mit Robolectric für Kernabläufe, auch Ablehnungen der Engine (`SaveResult.Invalid`) und Neuaufbau (`StateRestorationTester`); `AppViewModelTest` prüft die Verdrahtung zur Engine (z. B. Benachrichtigung weg nach Löschen); Previews hell und dunkel | keine Zahl |
| Gerät | [geraetetest.md](geraetetest.md), Diagnose-Log | vor jeder Freigabe |

- **Szenario-Tests:** `Simulation` stellt die Uhr vor und löst jeden Alarm
  zu seiner Zeit aus, `arrive`/`leave` spielen Ort und Geofence nach, `act` die Handlungen,
  `phoneOff` ein ausgeschaltetes Handy. Nach jedem Schritt gilt: je aktivem Reminder genau
  ein offener Termin, jeder offene Termin hat einen Alarm (außer er wartet nur auf eine
  Ankunft), geschlossene haben keinen, laut erst ab *laut ab* des Tages. Ein Fehler aus der echten
  Nutzung wird zuerst als Tag in `DayScenarioTest` nachgestellt. Tests mit „(open point)“
  im Namen halten das heutige Verhalten fest, über das noch entschieden wird.
- **Emulator** nur gezielt (der Rechner ist langsam): exakte Alarme im Doze-Modus, Vollbild
  auf dem Sperrbildschirm, echte Geofences, Neustart. Noch nicht eingerichtet.
- **Fake wie Room:** Fälle, deren Ergebnis davon abhängt, wie das Repository speichert
  (`scenarios/RepositoryCases` in den Testhilfen), laufen in `core` gegen das
  Fake-Repository und in `:app` gegen Room.
- **Echte Daten:** Kevins Daten pseudonymisiert als JSON in
  `testdata/` ([README](../testdata/README.md)), nur lokal (Anf. 7.34 OV2), erzeugt mit
  `tools/testdata/export_testdata.py` über adb, ohne Import/Export in der App.
  `RealDataTest` lädt sie in das Schema ihrer Version, migriert, baut die Alarm-Ansicht
  jedes offenen Termins, öffnet und speichert jeden Reminder im Formular (Round-Trip) und
  spielt eine Woche sowie zwei Tage mit ausgeschaltetem Handy durch. `Fakes` und `Simulation` liegen dafür in den Testhilfen von `core`
  (`src/testFixtures`, `java-test-fixtures`), die `:app` in den Tests mitbenutzt.
- **Robolectric-Isolation:** Jeder Test bekommt eine neue, fertig gestartete App
  (`StartedAvlApp`, `robolectric.properties`) mit eigener Datenbank, statische Felder
  bleiben aber über Tests hinweg stehen. Deshalb baut `MainActivity` ihr ViewModel mit
  `AppViewModel.Factory` aus der eigenen Application; Androids Standard-Factory merkt sich die
  erste Application statisch, und das ViewModel las dann die Datenbank eines früheren Tests
  (2026-11-15). `MainActivityTest` belegt diesen Singleton vor jedem Test mit einer fremden
  Application.

- Tests entstehen mit dem Code; Regeln haben sprechende Testnamen.
- Ein Befehl für alles, lokal wie in CI:
  `./gradlew :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

## 15. Schnittstelle für Claude (Anf. 7.26 AG11)

- **Weg:** `ContentProvider` `com.avlreminder.claude`, nur im Debug-Source-Set
  (`app/src/debug`), geschützt mit `android.permission.DUMP` (hat nur die adb-Shell).
  `call()` prüft die Berechtigung selbst, weil das Manifest sie dort nicht erzwingt.
  Antwort als Bundle-Extra `json`.
  ```bash
  adb shell "content call --uri content://com.avlreminder.claude --method reminders"
  adb shell "content call --uri content://com.avlreminder.claude --method reminder --arg '{\"id\":3}'"
  ```
- **Freigabe:** Einstellung `claudeAccess` (DataStore), Standard aus; Schalter
  *Schnittstelle für Claude* in den Einstellungen mit Rückfrage, nur sichtbar, wenn die
  App `debuggable` ist. Ohne Freigabe beantwortet sie nur `status`.
- **Logik:** `ClaudeApi` (in `main`, testbar mit Robolectric) über `ClaudeApi.Access`, in
  der App `AppGraph`. Schreiben geht über `ReminderEngine.save` usw., also mit derselben
  Prüfung und Alarmplanung wie die Oberfläche.
- **Methoden:** `status`, `methods`, `reminders` (`{"archived":true}`), `reminder`
  (`{"id"}`: Reminder, nächste Fälligkeit, letzte 10, Statistik, Serie, Blocker,
  Startklar), `today`, `history` (`{"days":14}`), `save` (`{"reminder":{…},"prep":[…]}`;
  ohne `id` neu mit `title` und `schedule`, Strenge-Voreinstellung wie im Formular; mit
  `id` ändern sich nur die angegebenen Felder), `setActive`, `archive`, `addBlocker`.
  Kein Erledigen, Auslassen, Alarm beenden, Nachweis, Löschen (AG5). Klingelt der
  Reminder gerade, läuft eine Sitzung oder ist er überfällig (offene Fälligkeit nicht mehr
  `PLANNED`, `currentAt` erreicht oder wartet auf die Ankunft), lehnen `save`, `setActive`
  und `archive` ab: „läuft gerade – nur in der App“.
- **JSON** (`ClaudeJson`): Zeiten lokal ohne Zone (`2026-11-03T19:15`), Enums mit ihrem
  Namen. Takt z. B. `{"type":"daily","times":["12:30","19:15"]}`, `weekdays` mit `days`,
  `once` mit `at`, `afterDone` mit `intervalDays`/`time`/`firstOn`, `onArrival`,
  `deadline` mit `due`/`days`/`from`/`until`/`intervalMinutes`. Jede Antwort hat `ok`
  und `version` (1), Fehler in `error`.
- **Protokoll:** `claude <Methode> id=<ID> ok|error` im Diagnose-Log, keine Inhalte.

---

## 16. Cloud-Backup (Backend B2b)
Stand der Umsetzung; die Begründungen stehen in
[cloud-entscheidungen.md](cloud-entscheidungen.md) (A-1…A-15, W-1…W-8, R-1…R-6).

**Aufteilung.** Abläufe, die auch der Test-Client braucht (Einrichten, Sichern,
Wiederherstellen, W-7, Löschen, Diagnose), liegen im Backend-Modul `protokoll`; die
Anbindung an den Server in `verbindung`. Die App bindet beide als Composite Build ein,
Commit fest in `gradle/backend-commit.txt` (A-2). In der App (`app/…/cloud/`):

| Datei | Aufgabe |
|---|---|
| `CloudBackup.kt` | Zustand für die Oberfläche, je Aktion ein Schritt unter einem Mutex (`work`) |
| `CloudServer.kt` | Schnittstelle zum Server; `ConnectCloudServer` über `Anmeldung`, `withSession` erneuert eine abgelehnte Sitzung einmal; Zeitplan (WorkManager) und Hinweise |
| `CloudUpload.kt` | ein Hochladen über `Sicherungsablauf`, Hash und Bremse (R-2) |
| `AutomaticBackup.kt` | stündliches Sichern (A-9, A-10), 3-Tage-Hinweis (A-13) |
| `CloudErrors.kt` | Fehler → Text und Diagnose-Art (gesperrt, neu anmelden, Limit, Netz) |
| `Question.kt` | eine offene Rückfrage an die Person (Schlüssel, Ersetzen, Bremse) |
| `CloudPhase.kt` | Zustände und Berichte der Oberfläche |
| `PasswordSafe.kt` | Wiederherstellungsschlüssel im Passwortdienst (A-15) |
| übrige | `CloudStore` (Merker), `CloudLog` (Logger), `CloudWorker` (WorkManager), `GoogleSignIn` (A-4), `Keystore`/`KeyFile` (A-5, A-6), `BackupPayload` (A-8), `BackupStateFile`, `CloudConfig` |

**Schlüssel.** Geräteschlüssel ECDSA P-256 im Android Keystore (A-5); Keysets mit einem
Keystore-AES-Schlüssel verschlüsselt (A-6); Sitzung und Sicherungsstand in einem eigenen
DataStore (A-7). Die Adapter holen das Token vor jedem Aufruf (Backend R2), damit lange
Eingaben die Sitzung nicht überdauern.

**Sichern.** Nutzlast ZIP aus DB-Kopie (`VACUUM INTO`) und Einstellungen (A-8).
Automatisch stündlich nur, wenn sich der Hash geändert hat, das Gerät freigeschaltet ist
und zuletzt selbst gesichert hat; sonst ruht es mit Hinweis bis *Jetzt sichern* (A-10).
Die Bremse hält an, wenn seit der letzten Sicherung mehr als die Hälfte oder mehr als
10 Reminder fehlen (R-2). Während ein Wiederherstellen wartet, wird nichts hochgeladen.

**Prüfen und Wiederherstellen.** *Sicherung prüfen* vergleicht je Tabelle, ohne Inhalte
zu zeigen (W-1…W-3). Wiederherstellen holt die Fassung, prüft sie mit Room, fragt und
tauscht beim nächsten Start (W-5); vorher bleibt eine Tageskopie (R-1). Nach der
Fassung eines anderen Geräts fragt die nächste Sicherung vor dem Ersetzen (W-7, im
`Sicherungsablauf`). Ältere Fassungen liefert der Server über `FassungenAuflisten` und
`SicherungHolen` mit Version (R-4).

**Sicherungsdatei** (Anf. 7.28 PS14, `restore/ExportFile.kt`, `ui/screens/ExportViews.kt`).
In den lokalen Kopien, also auch ohne Cloud-Backup. *Exportieren …* fragt zuerst ein
Passwort (mindestens 12 Zeichen, zweimal), dann wählt Androids Dateidialog den Ort
(`CreateDocument`, Name `AuViLo-Sicherung-<Datum>.auvilo`). Inhalt ist dieselbe Nutzlast wie
beim Cloud-Backup (`AppBackupSource`, eigene Kopie `export.db` im Cache, damit sich Export
und stündliche Sicherung nicht in die Quere kommen), verschlüsselt mit AES-256-GCM; der
Schlüssel kommt per PBKDF2-HMAC-SHA256 aus dem Passwort (600 000 Runden, 16 Byte Salt,
12 Byte Nonce, beide je Datei neu). Kopf: Kennung `AUVILO-SICHERUNG`, Version 1, Runden,
Salt, Nonce; er ist Zusatzdaten der Verschlüsselung, Änderungen fallen also auf. Ein
falsches Passwort und eine veränderte Datei sehen gleich aus („Passwort falsch oder Datei
verändert“). *Importieren …* fragt das Passwort einmal, öffnet die Datei (`OpenDocument`) und
läuft dann wie jedes Wiederherstellen (`LocalRestore.fromFile`, Art `FILE`): Vergleich,
Rückfrage, Tausch beim nächsten Start, Tageskopie zum Rückgängigmachen; danach vergisst die
App den Stand der letzten Cloud-Sicherung wie bei einer Tageskopie. Das Passwort liegt nur
als `CharArray` im Speicher und wird nach dem Lauf überschrieben; es kommt in keine Datei
und kein Log.

**Abmelden und Löschen.** *Dieses Gerät abmelden* sperrt das Gerät beim Server.
*Konto löschen* (`deleteAccount`, R-3) löscht über `KontoLoeschen` das ganze Konto auf
dem Server; das Handy vergisst Schlüssel und Sitzung erst nach der Bestätigung des
Servers. Ein nicht freigeschaltetes Gerät beantragt nur (Termin in `CloudStatus.deletion`),
*Löschung abbrechen* (`cancelDeletion`) geht nur freigeschaltet. Den Termin meldet der
Server bei Anmeldung und Erneuerung (`Anmeldung.loeschenAb`); `CloudBackup` liest ihn
nach jedem Schritt vom Server dieses Schritts (`deletionNews`, null = nicht gefragt) und
meldet einen neuen Termin mit Benachrichtigung. Der stündliche Lauf erneuert die Sitzung,
wenn die letzte Antwort älter als 23 Stunden ist (`askAboutDeletion`), auch bei
unveränderten Daten. Vor *Konto löschen* und *Sicherung löschen* fragt `CloudBackup.confirmed` die Displaysperre ab
(`ScreenLock`, im Gerät `SystemScreenLock` mit BiometricPrompt; Backend SE5), bevor ein
Server entsteht; ohne Displaysperre geht es weiter (Ereignis `ohne_displaysperre`).
Ohne Sitzung setzt `resetPhone` nur das Handy zurück. Ein nicht
freigeschaltetes Gerät kann sich mit dem Wiederherstellungsschlüssel freischalten.

**Diagnose.** Eigener Logger mit festen Ereignissen und typisierten Feldern, ohne Texte
aus der App (A-12); *Diagnose senden* nur für Konten mit Diagnose-Erlaubnis.

**Tests.** `CloudBackupTest` (gegen `FakeServer` mit den Serverregeln fürs erste
Schlüssel-Backup), `RestoreFilesTest`, `LocalRestoreTest`, `CorruptCopyTest`,
`ExportFileTest` (Format festgenagelt), `ExportFileUiTest`; auf dem
Gerät [geraetetest.md](geraetetest.md) Abschnitt D.

## Entwurf V1.3c – Abläufe (Skizze)

> Grundlage: anforderungen.md 7.12 A1–A7; dazu aus 7.3/7.4 *ein Vollbild zur Zeit, +1
> wartet* und *Rest später* (verschoben am 2026-11-12). **[P]** = vorher auf dem Gerät
> prüfen. Genauer Entwurf nach einer Woche Training mit der Sitzung.

- Tabelle `routine` (Name, Schritte als JSON): Schritt = Art (*Zeit*, *offen*,
  *Pause*), Titel, Dauer, Signal; Gruppen mit Wiederholungen (A3). Der Reminder
  verweist per `routineId`; mehrere Reminder können denselben Ablauf nutzen (A7).
- Die Sitzung führt `stepIndex` und `stepStartedAt`; jeder Schrittwechsel ist ein
  Ereignis (`STEP_DONE`), ein Abbruch speichert den Stand (A5).
- Signale (A4): drei feste Vibrationsmuster (*Schritt vorbei*, *Pause vorbei*,
  *nächster Satz*), per spiegelbarer Benachrichtigung auch auf der Uhr. **[P]** ob die
  Uhr die Muster unterscheidbar wiedergibt.
- Minimum über den Ablauf (A6): „bis Schritt x“; ohne Antwort am Ende zählen erreichte
  Schritte wie erledigte Checklistenpunkte (3.1).
- Vor dem Start anpassbar (A7): Schritt überspringen, Pause verlängern – nur für diese
  Sitzung.
