# Design – avlreminder

> Stand: V1.3b0.2 (2026-11-05). Beschreibt nur den **aktuellen** Stand, nach Themen.
> Frühere Entwürfe und ihre Abweichungen stehen im Git-Verlauf (bis Commit `a33d59b`
> war das Dokument nach Versionen gegliedert). Anforderungen:
> [anforderungen.md](anforderungen.md), Spike: [spike.md](spike.md).
> Neue Entwürfe kommen als eigener Abschnitt *Entwurf* ans Ende und werden nach der
> Umsetzung in die Themen eingearbeitet.

## 1. Architektur

```
┌──────────────────────────── :app (Android) ────────────────────────────┐
│  ui/        Compose-Screens, AppViewModel, MainActivity                 │
│  widget/    Glance-Widget                                               │
│  alarm/     AlarmPort, AlarmService, AlarmActivity, Benachrichtigungen, │
│             Übersicht, Vorab-Reminder, Kamera, Audio                    │
│  location/  Geofence und aktive Standortprüfung                         │
│  system/    Boot/Update/Zeitänderung, Diagnose-Log                      │
│  data/      Room-DB, Repository, DataStore-Einstellungen, Vorschläge    │
│  claude/    Schnittstelle für Claude (Provider nur im Debug-Build)      │
└───────────────┬─────────────────────────────────────────────────────────┘
                │ nutzt
┌───────────────▼──────────── :core (reines Kotlin, kein Android) ────────┐
│  model/     Reminder, Schedule, Occurrence, Proof, Event, Blocker, …    │
│  rules/     ScheduleRules, Lifecycle, Escalation, Presets/Validation,   │
│             Proofs, WeeklyGoals, Stats, Deadlines, Blockers             │
│  ports/     ReminderRepository, AlarmPort, GeofencePort, LocationPort,  │
│             DeviceStatePort, PreNoticePort (Zeit: java.time.Clock)      │
│  usecases/  ReminderEngine (Fassade) mit Scheduler, AlarmViews, Changes,│
│             EngineContext; AlarmView, RingPort; Overview, Digest        │
└─────────────────────────────────────────────────────────────────────────┘
```

- **`:core` kennt kein Android.** Alle Regeln leben dort, abgesichert mit Unit-Tests
  (Kover ≥ 90 %, Build schlägt sonst fehl).
- **`ReminderEngine`** ist der einzige Weg, Reminder und Fälligkeiten zu ändern:
  Oberfläche, Benachrichtigungen, Receiver und später Agenten (Anf. 7.26) nutzen dieselben
  Befehle. Seit V1.3b0 ist sie eine Fassade über drei interne Teile: `Scheduler` (Zeit:
  nächste Fälligkeit, Alarm planen, auslösen, eskalieren, Tagesgrenze), `AlarmViews` (was
  jetzt gilt: Limits, Woche, Strenge, Alarm-Ansicht) und `Changes` (was der Nutzer ändert:
  Reminder, Aktionen, Blocker, Hinweise).
- **Ein Befehl, eine Transaktion.** Jeder öffentliche Befehl läuft ganz oder gar nicht
  (`ReminderRepository.transaction`, in Room `withTransaction`); ein Befehl in einem
  anderen schließt sich ihm an. Was dabei fällig wird, klingelt erst danach über den
  `RingPort` – die Aufrufer klingeln nicht selbst.
- **Die Alarm-Ansicht kommt aus `core`** (`AlarmView`): Alarm-Bildschirm, Benachrichtigung
  und der Knopf „✓ Punkt“ zeigen dieselben erlaubten Aktionen; `Ring` trägt sie mit der
  Lautstärke.
- **Die Datenbank ist die einzige Wahrheit.** Alarme, Geofences, Übersicht und
  Vorab-Reminder sind abgeleitet und werden jederzeit aus der DB neu aufgebaut
  (`sync` bei Änderung, Alarm, App-Start, Neustart, Update, Zeitänderung).
- **Keine langsame I/O im Befehl** (seit V1.3b0.2, Review-Lauf 3 E6). Alarm- und
  Vorab-Änderungen sammelt `EngineContext` während des Befehls und führt sie erst nach dem
  Speichern aus; bei einem Rollback fallen sie weg. Braucht ein Befehl den Ort, wirft
  `EngineContext.isAt` beim ersten Mal `PositionNeeded`: Die Engine rollt zurück, fragt
  `LocationPort.isAt` außerhalb der Transaktion und startet den Befehl neu, jetzt mit dem
  Ergebnis. Höchstens eine Prüfung pro Ort und Befehl.
- Zeit kommt in `:core` immer über `Clock` (in `:app` teils direkt). Keine DI-Bibliothek:
  ein kleiner Container (`AppGraph`). Engine-Aufrufe laufen nacheinander (`withEngine`).

**Tech:** AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09, Room 2.8 + KSP,
DataStore, Glance, CameraX, Play-Services-Location, Kover. minSdk = targetSdk = 37.
Paket `com.avlreminder`.

## 2. Datenmodell

### 2.1 Reminder
| Bereich | Felder |
|---|---|
| Grunddaten | `title`, `why`, `character` (`OVERCOME` Überwindung / `DOCUMENT` Dokumentation), `strictness` (`RELAXED` / `NORMAL` / `STRICT`), `active`, `archivedAt`, `createdAt` |
| Takt | `schedule` (2.2), `arrival` (Ort, 6.) |
| Ziel | `goal`, `minimum`, `minimumLimitPerWeek`, `weeklyGoal` (Ziel, höchstens, mindestens) |
| Nachweis | `proof: ProofSpec` (5.) |
| Alarm | `postponeLimit`, `snoozeMinutes` (1–2 Werte), `ignoreDnd` |
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

**Mehrmals täglich (VS1, V1.3a):** Weiterhin eine offene Fälligkeit pro Reminder; nach
dem Abschluss folgt die nächste Uhrzeit desselben Tages, sonst der nächste Tag. Ein
Termin gilt als **verpasst, sobald der nächste beginnt** (spätestens an der
Tagesgrenze); ein Alarm bis 2 min zu spät klingelt noch den gerade begonnenen Termin.
Wird das erst später bemerkt (Handy aus), folgt der Termin, der heute zuletzt begonnen
hat, und klingelt sofort (`ScheduleRules.currentTime`, seit V1.3a3).
*Später* und *Verschieben* nur bis vor den nächsten Termin. Eine Checkliste läuft über
die Termine, ein Punkt pro Termin (8.). Das Wochenlimit für das *Minimum* zählt Tage
(`Limits.minimumToday`). Nicht kombinierbar mit Wochenziel und Ankunft (Validierung, das Formular
blendet beides aus). Vorab erledigen betrifft nur den nächsten Termin.

### 2.3 Fälligkeit (`Occurrence`)
`dueAt` (geplant), `currentAt` (aktuell, nach Später/Verschieben), `state`,
`postponeCount`, `pausedUntil` (Nachweis läuft), `firstProgressAt` (erster
Checklistenpunkt), `firedAt` (erstes Klingeln, `hasFired`), `awaitsArrival` (wartet nach
Verschieben auf die Ankunft, 6.), `resolvedAt`, `resolution` (`GOAL`, `MINIMUM`,
`NO_PROOF`, `SKIPPED`, `MISSED`, `DROPPED`), `note`. `firedAt` und `awaitsArrival` gibt es
seit V1.3b0.2 (Review-Lauf 3 E5): `PLANNED` allein sagt nicht, ob ein Termin schon
geklungen hat. Pro Reminder gibt es höchstens **eine offene** Fälligkeit;
nach dem Abschluss legt die Engine die nächste an.

### 2.4 Weitere Tabellen
| Tabelle | Inhalt |
|---|---|
| `event` | unveränderliches Protokoll pro Fälligkeit: `CREATED`, `DUE`, `ESCALATED`, `SNOOZED`, `POSTPONED`, `COMPLETED`, `SKIPPED`, `MISSED`, `MISSED_DETECTED`, `PROOF_STARTED`, `PROOF_ABORTED`, `DROPPED`, `ITEM_DONE`, `DEFERRED`, `WINDOW_CLOSED`, `BLOCKER_ASKED`, `AWAITING_ARRIVAL`, `REOPENED` (Detail: die zurückgenommene Auflösung) |
| `proof` | Foto, Text oder Audio pro Fälligkeit, optional pro Checklistenpunkt (`itemId`) |
| `checklist_item`, `item_done` | Punkte eines Reminders; erledigte Punkte pro Fälligkeit mit `withProof` |
| `prep_answer` | Startklar-Antworten, nur anhängen; aktuell ist die neueste pro Frage |
| `blocker` | Text, angelegt, gelöst am, Herkunft (`STARTKLAR` / `ASKED`), Vorbereitungsaufgabe |
| `place` | Orte (bisher nur *Zuhause*), Radius Standard 150 m; WLANs ab 7.29 (Entwurf unten) |
| `template` | Vorlagen (11.3) |
| `suggestion` | Verbesserungsvorschläge (11.5), ohne Fremdschlüssel |

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
- **Hat geklungen** (`firedAt`, seit V1.3b0.2): `Fire` und die Ankunft setzen es beim
  ersten Klingeln. Ein Termin, der geklungen hat, kann nicht mehr *entfallen* (`Drop`
  wird abgelehnt).
- **Takt geändert** (`Changes.save`): Ein offener Termin, der nie geklungen hat und nie
  verschoben wurde, wandert auf die neue Zeit (oder wird mit *Takt geändert*
  ausgelassen). Ein geschlummerter oder verschobener behält seinen Tag – Speichern lässt
  den heutigen Termin nicht verschwinden (Review-Lauf 3 B1).
- **Fortsetzen** eines pausierten Reminders (`setActive`): Der offene Termin startet neu
  ab der nächsten Fälligkeit (`firedAt`, `awaitsArrival`, `pausedUntil` zurückgesetzt).
  Für die pausierte Zeit werden keine Verpasser nachgemeldet.
- **Zurücknehmen** (`ReminderEngine.reopen`, V1.3b0.4, Anf. 7.31): `Action.Reopen(at)`
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
  Alarm-Ansicht überspringt dann den Nachweis (`AlarmView.proofCarried`).
- **Vorab verschieben und auslassen** (V1.3b0.4, Anf. 7.32): `Postpone` und `Skip` gelten
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
  in der Benachrichtigung eine stille Benachrichtigung mit *Rückgängig*, eigene Id,
  `setTimeoutAfter(60 s)`; das Ergebnis kommt als zweite stille Benachrichtigung. Auf
  *Heute* unter *Erledigt* *Zurücknehmen* mit Rückfrage, Ergebnis als Toast. Keine
  Migration (Ereignistypen stehen als Name in der Datenbank).

## 4. Alarm und Eskalation

| Strenge | normal | „Nicht stören“ aktiv | … und `ignoreDnd` |
|---|---|---|---|
| `RELAXED` | Banner + Vibration | still | Banner + Vibration |
| `NORMAL` | Vollbild + Vibration (Wecker ohne Ton) | still | Vollbild + Vibration |
| `STRICT` | Vollbild + Vibration, ab `loudFrom` mit Alarmton | still | wie normal |

- **Runden:** Eine Runde klingelt 3 min (`Escalation.ROUND`); die nächste liegt 10 min
  (`REPEAT`) nach dem Planen, also etwa alle 10 min ab Rundenbeginn, bis zur Reaktion.
  Während „Nicht stören“ laufen die Runden still weiter. Offen: Die Fälligkeit speichert
  ihre nächste Runde nicht; jeder `sync` plant sie ab jetzt neu, dadurch kann sich eine
  Runde nach hinten schieben.
- **Mehrere gleichzeitig** (Anf. 7.3, geplant mit V1.3b): ein Vollbild zur Zeit, der
  nächste mit *+1 wartet*; seine Runden laufen weiter. Heute überschreibt der zweite
  Alarm den ersten im `AlarmService`.
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
  begann (`alarmsPausedFrom`, `excusedByPause`, seit V1.3a3); sonst *verpasst*. Eine
  verlängerte Pause behält ihren Beginn. Ein laufender Alarm stoppt beim Pausieren.
  Endet die Pause, klingelt eine offene Fälligkeit sofort, auch beim Einschalten von Hand
  (`ReminderEngine.resume`, seit V1.3a3).
  Fristen sind hart: Eine Frist, die während der Pause abläuft, ist *verpasst* und bleibt
  *überfällig* wie ohne Pause (7.); der Notaus macht sie nicht neutral (Anf. 7.2, PS7).
- **Später** (+x min): Knöpfe pro Reminder; Knöpfe über die Tagesgrenze werden
  ausgeblendet; ohne Limit, jedes ist ein `SNOOZED`.
- **Verschieben** auf einen Zeitpunkt: zählt gegen `postponeLimit`. Ist es erreicht,
  bleiben Erledigt, Minimum und Auslassen.
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

## 6. Ort: Ankunft zuhause

- `Reminder.arrival = Arrival(placeId, whenAway)`: **Der Takt bestimmt den Tag, die
  Ankunft den Moment.** Die Uhrzeit des Takts ist die Rückfallzeit; `whenAway` ist
  *entfällt* (`DROP`, Standard) oder *trotzdem klingeln* (`RING`). Nicht bei Fristen.
- Ankunft klingelt nur die heutige, noch geplante Fälligkeit vor ihrer Uhrzeit.
- **Geofence** *Dwell* pro benötigtem Ort, Verweildauer 1/3/5/10 min (Standard 3); ohne
  „Immer erlauben“ zählt nur die Uhrzeit. Neu registriert, wenn Orte oder Verweildauer
  sich ändern und nach Neustart.
- **Aktive Prüfung** (ein frischer Fix, höchstens 8 s) zur Rückfallzeit bei `DROP`:
  sicher drin → Alarm, sicher draußen → `DROPPED`, unklar → Alarm. Bei *Wenn ich zuhause
  bin* nach dem Speichern, nach dem Setzen von *Zuhause* und nach Neustart/Update. Die
  Prüfung läuft außerhalb der Transaktion (1., `PositionNeeded`).
- **Nach Später oder Verschieben** (Anf. 7.29 OZ5, seit V1.3b0.2): Hat der Termin schon
  geklungen und ist man zur neuen Zeit nicht sicher da (*weg* oder *unklar*), wird er nicht
  `DROPPED`, sondern wartet (`AwaitArrival`, `awaitsArrival`, Ereignis
  `AWAITING_ARRIVAL`). Die Ankunft lässt ihn klingeln; der Alarm liegt an der
  Tagesgrenze, dort wird er *verpasst*. *Heute* zeigt ihn unter *Wenn du zuhause bist*
  („verschoben – klingelt bei Ankunft, sonst heute verpasst“). `checkPlaces` prüft
  wartende Termine mit. Periodische Prüfungen im Prüfabstand kommen mit 7.29.
- Offen: `AndroidGeofences` merkt sich im Prozess, was registriert ist. Verwerfen die
  Play-Services die Geofences (Standort aus und wieder an), registriert die App sie erst
  nach einem Neustart des Prozesses neu. Nicht reproduziert; mit 7.29 auf
  `GEOFENCE_NOT_AVAILABLE` und den Wechsel des Standort-Schalters reagieren.

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
  aus *Heute* und dem Reminder (Anf. 7.13 C8, V1.3b0.1). Der letzte
  schließt: `GOAL`, wenn jeder Punkt seinen Nachweis hat, sonst `NO_PROOF`. *Alles
  erledigt* schließt mit einem Nachweis für den Rest; *Minimum* schließt mit `MINIMUM`,
  ohne die Punkte abzuhaken.
- **Über mehrere Tage:** Wurde an einem Tag ein Punkt erledigt, kommt der Rest am
  nächsten Tag zur selben Uhrzeit wieder (zählt nicht als Verschieben); sonst verpasst.
  *Rest auf morgen* im Alarm ist nach einem Punkt am selben Tag frei, sonst eine
  Verschiebung.
- **Mehrere Uhrzeiten am Tag (V1.3a3):** Die Liste läuft über die Termine. Ein
  abgehakter Punkt schließt den Termin (`GOAL` bzw. `NO_PROOF` nach dem Nachweis dieses
  Punkts), der nächste Termin zeigt nur die offenen Punkte. Erledigt ist, was seit dem
  letzten vollständigen Durchlauf abgehakt wurde (`Checklists.round` über
  `itemsDoneOfReminder`); danach beginnt die Liste von vorn. Ein Termin ohne Punkt ist
  *verpasst*, kein *Rest auf morgen*. Im Alarm: „Erledigt: ‹nächster Punkt›“, „Danach:
  Rest um …“, klein *Alles auf einmal erledigt*.
- *Nach Erledigung* zählt ab dem **ersten** erledigten Punkt (`firstProgressAt`).
- Benachrichtigung: „✓ nächster Punkt“ nur ohne Nachweis; danach eine stille
  Benachrichtigung mit dem nächsten Punkt.
- Bearbeiten: Gebliebene Punkte behalten Id und Fortschritt; entfernte nehmen ihren
  Fortschritt mit, ihre Nachweise bleiben.

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
- Zählen: *Ziel* und *Minimum*, nicht *ohne Nachweis*; maßgeblich ist `resolvedAt`.
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
- **Heute:** Jetzt fällig, Überfällig (Fristen), Später heute (mit *Erledigen* = vorab),
  Wenn du zuhause bist, Diese Woche (Wochenziele, knappe hervorgehoben), Erledigt.
- **Verlauf:** Erledigungen nach Tagen mit Ergebnis und Nachweisen; „vor dem Reminder
  erledigt“ aus `resolvedAt < dueAt`.
- **Reminder:** Gruppen *aktiv*, *Pausiert*, *Abgeschlossen* (archiviert oder ein
  Einmal-/Ankunfts-Reminder ohne offene Fälligkeit, eingeklappt). Sortierung nächste
  Fälligkeit (Standard), Name, zuletzt angelegt. Archivieren pausiert;
  Wiederherstellen lässt pausiert.
- **Formular:** Was, Charakter, Wann, Wie streng; der Rest klappbar mit Zusammenfassung
  in der Kopfzeile (Wochenziel, Ziel und Minimum, Vorab-Reminder, Blocker, Checkliste,
  Nachweis, Warum und Botschaft, Startklar, Feineinstellungen). Zahlen als Chips plus
  *andere…*.
- **Alarm (Vollbild, immer dunkel):** Titel, Warum, Botschaft, Startklar, Blocker,
  Woche, Ziel/Minimum, Checkliste; Erledigt, Minimum, Blocker gelöst, Rest auf morgen,
  Auf morgen, Später, Verschieben, Auslassen; Nachweis als eigener Schritt.
- **Einstellungen:** Vorschläge, Darstellung, Alarmzeiten, Nachfragen, Vorlagen,
  Übersicht, Zuhause, Berechtigungen. Zahnrad auf allen Tabs.

### 11.2 Benachrichtigungen, Uhr, Widget
- **Alarm-Kanäle:** Vollbild über Foreground-Service; zusätzlich eine normale
  Benachrichtigung mit denselben Aktionen für die Uhr (Kanal *Alarm auf der Uhr*, ohne
  Ton), die laufende ist `setLocalOnly`.
- **Übersicht:** stille Benachrichtigung morgens (heute) und abends (Rückblick,
  Wochenziele, morgen; jede Uhrzeit eines Reminders einzeln, Anf. O1 seit V1.3b0.1),
  Standard 7:30 und 20:30, einzeln *aus*, Sa–So mit eigenen
  Zeiten (VS2, derselbe Schalter wie bei den Alarmzeiten). Inexakter Alarm
  (`setWindow`, 10 min), Inhalt beim Auslösen aus der DB.
- **Notaus (PS7):** dauerhafte stille Benachrichtigung *Alle Alarme pausiert …* mit
  *Wieder einschalten*, verschwindet am Pausenende (`setTimeoutAfter`); dazu ein rotes
  Banner auf *Heute*. Einschalten in den Einstellungen ganz oben, mit Rückfrage.
- **Vorab-Reminder (T6, S6):** *am Vorabend* (Uhrzeit) oder *x min vorher*, nicht bei
  *wenn ich zuhause bin*. Stiller Kanal, inexakt (`setWindow`, 5 min), mit erstem Schritt
  und offenen Blockern; entfällt, wenn schon erledigt. Tipp öffnet den Reminder.
- **Widget** (Glance, folgt dem System-Modus): *+ Reminder* und nächste Fälligkeit.

### 11.3 Vorlagen
Übernehmen Charakter, Strenge, Nachweis, Minimum mit Limit, Später-Knöpfe,
Verschiebe-Limit, *Nicht stören*-Ausnahme und die Art des Takts mit Zeiten (auch
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

### 11.5 Verbesserung vorschlagen
Glühbirne oben rechts auf jedem Bildschirm, im Alarm ein Textknopf ganz unten (der Alarm
läuft weiter). Gespeichert mit Bildschirm, Reminder, App-Version; Status offen /
umgesetzt / verworfen. Liste in den Einstellungen, *Teilen* als Markdown.

## 12. Android-Anbindung

| Thema | Lösung |
|---|---|
| Alarm | `setAlarmClock()` für normal/streng, `setExactAndAllowWhileIdle()` für locker; Übersicht und Vorab-Reminder mit `setWindow()` |
| Dauer-Alarm | Foreground-Service Typ `systemExempted` |
| Vollbild | Full-Screen-Intent + Activity mit `showWhenLocked`/`turnScreenOn` |
| Neuaufbau | `sync` bei Änderung, Alarm, App-Start, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, Zeit-/Zonenänderung |
| Ort | Play-Services Geofencing (Dwell), Fused Location für die aktive Prüfung |
| Berechtigungen | Seite in den Einstellungen, Warnung auf *Heute*: Benachrichtigungen, Vollbild, Akku, Kamera, Mikrofon; Standort „Immer“ und DND-Ausnahme nur, wenn ein Reminder sie braucht |

## 13. Daten, Migration, Diagnose

- **Room** mit exportierten Schemas (`app/schemas/`), aktuell Version 8. Jede Version hat
  eine explizite Migration (`AvlDatabase.MIGRATIONS`), kein destruktiver Fallback;
  Migrationen möglichst nur mit `ADD COLUMN` und neuen Tabellen.
- **Fremdschlüssel mit `ON DELETE CASCADE`:** `occurrence`, `blocker`, `checklist_item`,
  `prep_answer` → `reminder`; `event`, `proof`, `item_done` → `occurrence`; `item_done` →
  `checklist_item`. Wer eine Tabelle neu aufbaut (kopieren, löschen, umbenennen), schaltet
  vorher `PRAGMA foreign_keys=OFF`, prüft danach `PRAGMA foreign_key_check` und schaltet
  wieder ein – sonst löscht das `DROP TABLE` alle abhängigen Zeilen. Migration 5→6 übernimmt
  die neueste Antwort auf Frage 3 als Blocker. Migration 6→7: `reminder.moreTimes` und
  `template.moreTimes` (weitere Uhrzeiten als Minuten, z. B. `780,1200`; die erste bleibt
  in `timeMinutes`), `reminder.hintQuietUntil`. Migration 7→8 (V1.3b0.2):
  `occurrence.firedAt` (aus dem ersten `DUE`-Ereignis nachgetragen) und
  `occurrence.awaitsArrival`.
- **Einstellungen** (DataStore): Wochenend-Zeiten als eigene Schlüssel (fehlen = wie
  Mo–Fr), `alarmsPausedUntil` und `alarmsPausedFrom` in Millisekunden (fehlt = keine
  Pause bzw. Beginn unbekannt).
- **Backup vor jeder Migration** (Schutz gegen Datenverlust beim Update, kein
  Nutzer-Backup, Anf. 8.4) per `VACUUM INTO` nach
  `filesDir/backup/avlreminder-v<alt>-<Zeit>.db`; scheitert es, startet die App trotzdem.
- **Diagnose-Log** (`Diag`): logcat-Tag `AvlDiag` und `files/diag/diag.log` (rotiert bei
  512 KB). Zeiten, IDs, Titel, Zustände, Berechtigungen; keine Nachweis-Inhalte, keine
  Positionen. Titel nur in der Entwicklungsphase; vor einem offiziellen Release
  werden sie entfernt (Entscheidung 2026-11-03).
- **Einstellungen** außerdem: `claudeAccess` (1 = Schnittstelle für Claude an, 15).
- Alle Daten bleiben auf dem Gerät; nur das Cloud-Backup bringt sie, wenn es
  eingeschaltet ist, Ende-zu-Ende verschlüsselt zum Backend (Anf. 8.4,
  [backup-entwurf.md](backup-entwurf.md)).

## 14. Tests und Qualität

| Bereich | Test-Art | Ziel |
|---|---|---|
| `:core` | JUnit, Fake-Clock, Fake-Repository | Kover ≥ 90 % (aktuell 98,2 %) |
| `:core` Szenarien | `scenarios/Simulation`: ganze Tage mit Uhr, Alarmen, Ort und Handlungen; prüft nach jedem Schritt die Invarianten | je gemeldetem Fehler ein Tag |
| `:app` data | Robolectric + Room, `MigrationTest` gegen die Schemas | jede Migration, Backup |
| `:app` echte Daten | `RealDataTest` mit `testdata/real-*.json`: Migration, Alarm-Ansicht, eine Woche mit allen Remindern | bei jeder neuen Version neu ziehen |
| `:app` Verbindung | `ReceiverGlueTest`: Broadcast (Alarm, Knopf, Ankunft, Neustart) → Engine mit echter Room-DB → Benachrichtigung, Dienst, Alarm | jeder Receiver |
| `:app` ui | Compose-UI-Tests mit Robolectric für Kernabläufe; Previews hell und dunkel | keine Zahl |
| Gerät | [geraetetest.md](geraetetest.md), Diagnose-Log | vor jeder Freigabe |

- **Szenario-Tests** (seit 2026-11-04): `Simulation` stellt die Uhr vor und löst jeden Alarm
  zu seiner Zeit aus, `arrive`/`leave` spielen Ort und Geofence nach, `act` die Handlungen,
  `phoneOff` ein ausgeschaltetes Handy. Nach jedem Schritt gilt: je aktivem Reminder genau
  ein offener Termin, jeder offene Termin hat einen Alarm (außer er wartet nur auf eine
  Ankunft), geschlossene haben keinen, laut erst ab `loudFrom`. Ein Fehler aus der echten
  Nutzung wird zuerst als Tag in `DayScenarioTest` nachgestellt. Tests mit „(open point)“
  im Namen halten das heutige Verhalten fest, über das noch entschieden wird.
- **Emulator** nur gezielt (der Rechner ist langsam): exakte Alarme im Doze-Modus, Vollbild
  auf dem Sperrbildschirm, echte Geofences, Neustart. Noch nicht eingerichtet.
- **Echte Daten** (seit 2026-11-04): Kevins Daten pseudonymisiert als JSON in
  `testdata/` ([README](../testdata/README.md)), erzeugt mit
  `tools/testdata/export_testdata.py` über adb, ohne Import/Export in der App.
  `RealDataTest` lädt sie in das Schema ihrer Version, migriert, baut die Alarm-Ansicht
  jedes offenen Termins und spielt eine Woche sowie zwei Tage mit ausgeschaltetem Handy
  durch. `Fakes` und `Simulation` liegen dafür in den Testhilfen von `core`
  (`src/testFixtures`, `java-test-fixtures`), die `:app` in den Tests mitbenutzt.

- Tests entstehen mit dem Code; Regeln haben sprechende Testnamen.
- Ein Befehl für alles, lokal wie in CI:
  `./gradlew :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

## 15. Schnittstelle für Claude (V1.3a2, Anf. 7.26 AG11)

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
  Reminder gerade oder ist er überfällig (offene Fälligkeit nicht mehr `PLANNED`,
  `currentAt` erreicht oder wartet auf die Ankunft), lehnen `save`, `setActive` und `archive` ab: „läuft gerade –
  nur in der App“ (seit V1.3a3).
- **JSON** (`ClaudeJson`): Zeiten lokal ohne Zone (`2026-11-03T19:15`), Enums mit ihrem
  Namen. Takt z. B. `{"type":"daily","times":["12:30","19:15"]}`, `weekdays` mit `days`,
  `once` mit `at`, `afterDone` mit `intervalDays`/`time`/`firstOn`, `onArrival`,
  `deadline` mit `due`/`days`/`from`/`until`/`intervalMinutes`. Jede Antwort hat `ok`
  und `version` (1), Fehler in `error`.
- **Protokoll:** `claude <Methode> id=<ID> ok|error` im Diagnose-Log, keine Inhalte.

---

## Entwurf V1.3 (2026-11-02, bestätigt; V1.3a und V1.3b0 umgesetzt)

> Grundlage: anforderungen.md 7.11 (Timer), 7.12 (Abläufe), 7.14 G2/G3 (Uhr über
> Benachrichtigungen), 7.20 V2 (vorgezogen starten), 7.25 VS1/VS2, 7.27 NT2 und 7.28 PS7.
> **[E]** = Empfehlung, von Kevin zu bestätigen; **[P]** = vorher auf dem Gerät prüfen.
> Nach der Umsetzung wandert alles in die Themen oben.

### E.1 Aufteilung

| Etappe | Inhalt | Warum so |
|---|---|---|
| **V1.3a – Alltag** | Mehrmals täglich (VS1), Wochenend-Zeiten (VS2), Hinweis bei ständigem *Später* (NT2), Notausschalter (PS7) | Klein, sofort im Alltag spürbar, passt zur Beobachtungsphase. Keine neuen Bildschirme, nur Regeln und Einstellungen. |
| **V1.3b0 – Umbau** | Engine als Fassade, Befehle in einer Transaktion, Alarm-Ansicht in `core` (Review-Lauf 2); V1.3b0.2: `firedAt`/`awaitsArrival`, Ortsprüfung vor der Transaktion (Review-Lauf 3) | Umgesetzt. Macht die Sitzung (neuer Zustand) und 7.29 (Ort im Befehl) sicher. |
| **7.29 – Ankunft im Zeitraum** | Zeitraum und WLAN, ohne GPS (Entwurf unten); GPS später mit Anf. 7.30 | Vor V1.3b; Anforderungen von Kevin am 2026-11-05 gelesen. |
| **V1.3b – Sitzung mit Timer** | *Ich starte jetzt*, Timer, Nachfragen, Ziel/Minimum als Dauer, maximale Dauer, Dauer-Vergleich (7.11 D1–D6), vorgezogen starten (7.20 V2), Aktionen und Timer auf der Uhr per Benachrichtigung (G2, G3) | Der große neue Mechanismus. Braucht einen neuen Zustand *läuft* im Lebenszyklus und einen Dienst für den Timer. |
| **V1.3c – Abläufe** | Schritte, Pausen, Wiederholungen, Signale, Fortschritt, Abläufe als Vorlagen (7.12 A1–A7) | Baut vollständig auf der Sitzung aus V1.3b auf; erst sinnvoll, wenn der Timer im Alltag trägt. |

**[E]** Reihenfolge a → b → c, jede Etappe einzeln installieren. Nach V1.3b eine
Woche Training mit Timer, bevor die Abläufe festgelegt werden – die Erfahrung mit den
Nachfragen zeigt, wie viel Ablauf wirklich nötig ist (passt zu NT1, *trimmen*).

### E.2 V1.3a – Alltag
Umgesetzt (2026-11-02) und in die Themen oben übernommen: mehrmals täglich (2.2, 10.2),
Wochenend-Zeiten (4., 11.2), Notaus (4., 11.2), Hinweis bei ständigem *Später* (10.2),
Migration und Einstellungen (13.).

### E.3 V1.3b – Sitzung mit Timer

**Modell**
- Pro Reminder `session: SessionSpec?` (aus = wie bisher): Nachfrage-Intervall
  (Standard 10 min), Strenge der Nachfragen, Ziel- und Minimum-Dauer (optional, D4),
  maximale Dauer (Standard 90 min, D5), Reaktionszeit des letzten Reminders (5 min).
- Neuer Zustand **`RUNNING`** der Fälligkeit mit den Feldern `startedAt` und
  `nextCheckAt`. Neue Ereignisse `STARTED`, `CHECK_ASKED`, `CHECK_ANSWERED`,
  `FINAL_CALL`. Neues Ergebnis **`ABORTED`** (*abgebrochen*: zählt nicht als Erfolg und
  bricht die Serie wie *verpasst* – ehrlich in der Statistik, D3). **[E]**
- Dauer = `resolvedAt − startedAt`, ohne eigene Tabelle. Vergleich mit dem Schnitt
  der letzten 10 Sitzungen (D6) im Verlauf und nach dem Abschluss.

**Ablauf**
```
PLANNED/DUE/ESCALATING ─Ich starte jetzt─► RUNNING ─Fertig──────► Nachweis? ─► DONE (GOAL)
                                            │ ▲   ├─Teilschritt─────────────► DONE (MINIMUM)
                              Noch dran ────┘ │   ├─Abbruch─────────────────► CLOSED (ABORTED)
                       Nachfrage alle x min ──┘   └─max. Dauer → letzter Reminder (5 min)
                                                        └─ keine Antwort ─► MISSED bzw. MINIMUM*
```
\* MINIMUM, wenn schon Checklistenpunkte erledigt sind (D5), in V1.3c auch erreichte
Schritte eines Ablaufs.
- *Ich starte jetzt* beendet die Eskalation sofort. Auch **vorab** möglich (7.20 V2) –
  dann entfällt der Alarm wie beim Vorab-Erledigen.
- Mit Ziel-Dauer schlägt die Nachfrage die passende Antwort vor („45 min erreicht –
  *Fertig*?“).
- Nachfragen sind Alarme mit eigener Strenge: **[E]** Standard *locker* (Banner +
  Vibration), damit das Training nicht ständig vom Vollbild unterbrochen wird; ohne
  Antwort eskaliert die Nachfrage wie ein normaler Alarm.
- **`RUNNING` gegen die anderen Regeln** (Anf. 7.11 D7, Review-Lauf 3 E8). Grundsatz:
  `missedAt` gilt für `RUNNING` nicht; eine Sitzung endet nur über eine Antwort oder die
  maximale Dauer.

  | Ereignis während `RUNNING` | Verhalten |
  |---|---|
  | Tagesgrenze | nicht verpasst; die Sitzung zählt für ihren Starttag |
  | nächster Termin (*mehrmals täglich*) | wartet; nach dem Abschluss folgt der nächste noch nicht begonnene Termin, in der Sitzung vergangene zählen nicht extra |
  | Fensterende / Frist abgelaufen | kein `CloseWindow`, kein `Miss`; die Sitzung läuft weiter |
  | Notaus | Nachfragen still; maximale Dauer gilt, danach Minimum bzw. `ABORTED` ohne letzten Alarm |
  | „Nicht stören“ | Nachfragen still; die Reaktionszeit der letzten beginnt erst ohne DND |
  | Pausieren, Archivieren, Bearbeiten, `save` (Takt) | abgelehnt („läuft gerade“); Text-Felder darf die App ändern |
  | Schnittstelle (`save`, `setActive`, `archive`) | abgelehnt wie beim klingelnden Reminder (`notRunning`) |
  | anderer Reminder klingelt | ein Vollbild zur Zeit (4.); die Sitzung läuft mit ihrer Benachrichtigung weiter |
  | Handy aus / Neustart | `sync` plant die nächste Nachfrage; war die maximale Dauer schon um, gilt sofort der letzte Reminder |

- **Zwei Dienste:** Der Sitzungsdienst und der `AlarmService` laufen nebeneinander, jeder
  mit eigener Benachrichtigungs-ID. Eine Nachfrage nutzt den `AlarmService` mit der
  `AlarmView` im Modus *Nachfrage* (Antworten statt Erledigt/Später).

**Android**
- Foreground-Service (Typ `systemExempted` wie der Alarm; Sitzungen dauern zu lange für
  `shortService`) mit laufender Benachrichtigung: Chronometer, *Fertig*, *Teilschritt*,
  *Abbruch*.
- Nachfragen und letzter Reminder über den `AlarmPort` (exakt); sie überleben einen
  Neustart über `sync`.
- **Uhr (G2, G3):** Die laufende Benachrichtigung erscheint nicht auf der Uhr (G1).
  Deshalb wie in V1.1 eine **zweite, spiegelbare Benachrichtigung**: Nachfragen mit
  *Noch dran / Fertig / Teilschritt / Abbruch*, im Alarm zusätzlich *Ich starte
  jetzt*. **[P]** ob die Uhr einen Chronometer aus einer gespiegelten Benachrichtigung
  anzeigt; falls nicht, zeigt sie die Startzeit („seit 18:42“).
- Bildschirm: Sitzungsansicht mit großem Timer und den vier Antworten; erreichbar aus
  *Heute* (*läuft seit …*) und über die Benachrichtigung.

### E.4 V1.3c – Abläufe (Skizze)
- Tabelle `routine` (Name, Schritte als JSON): Schritt = Art (*Zeit*, *offen*,
  *Pause*), Titel, Dauer, Signal; Gruppen mit Wiederholungen (A3). Der Reminder
  verweist per `routineId`; mehrere Reminder können denselben Ablauf nutzen (A7).
- Die Sitzung führt `stepIndex` und `stepStartedAt`; jeder Schrittwechsel ist ein
  Ereignis (`STEP_DONE`), ein Abbruch speichert den Stand (A5).
- Signale (A4): drei feste Vibrationsmuster (*Schritt vorbei*, *Pause vorbei*,
  *nächster Satz*), per spiegelbarer Benachrichtigung auch auf der Uhr. **[P]** ob die
  Uhr die Muster unterscheidbar wiedergibt.
- Minimum über den Ablauf (A6): „bis Schritt x“.
- Vor dem Start anpassbar (A7): Schritt überspringen, Pause verlängern – nur für diese
  Sitzung.
- Genauer Entwurf nach den Erfahrungen mit V1.3b.

### E.5 Entschieden (2026-11-02)
1. Aufteilung a/b/c und Reihenfolge (E.1).
2. Mehrere Uhrzeiten: verpasst beim nächsten Termin; kein Wochenziel und kein Ort mit
   mehreren Uhrzeiten (E.2).
3. NT2-Schwelle (3 Fälligkeiten × 2 *Später*) und Karte auf *Heute* statt im Alarm.
4. Notausschalter: *Notaus*-Auslassen bricht keine Serie.
5. `ABORTED` bricht die Serie; Nachfragen standardmäßig *locker*; eine Sitzung ignoriert
   die Tagesgrenze und endet nur über die maximale Dauer (Standard 90 min).

Alle fünf bestätigt am 2026-11-02 (anforderungen.md Kap. 9). Die Tabelle *`RUNNING` gegen
die anderen Regeln* in E.3 kam am 2026-11-05 dazu (Review-Lauf 3).

---

## Entwurf 7.29 – Ankunft im Zeitraum (2026-11-05, zum Bestätigen)

> Grundlage: anforderungen.md 7.29 OZ1–OZ9 und F1–F3, Review-Lauf 3 E7. Seit 2026-11-05 nur
> WLAN, GPS ist nach 7.30 ausgelagert. **[E]** = Empfehlung,
> **[P]** = vorher auf dem Gerät prüfen. Umsetzung erst nach Kevins Gesamtdurchsicht der
> Anforderungen; dieser Entwurf soll nur verhindern, dass der Umbau später teurer wird.

### Z.1 Datenmodell (Migration 8→9)
- `Arrival` bekommt `leadMinutes` (Vorlauf, Standard 90; 0 = kein Zeitraum wie bisher),
  `bufferMinutes` (0–30, Standard 5) und `checkMinutes` (15–30, Standard 20). In der DB
  `reminder.arrivalLead`, `arrivalBuffer`, `arrivalCheck` per `ADD COLUMN` mit den
  Standardwerten: Bestehende Ankunfts-Reminder bekommen F3 (OZ9), *Heute* zeigt einmal
  einen Hinweis.
- Neue Tabelle `place_wifi` (`placeId` → `place` mit `ON DELETE CASCADE`, `bssid`, `ssid`
  nur zur Anzeige), eine Zeile pro Zugangspunkt (Anf. 7.29 OZ3). Beim Festlegen übernimmt
  die App die verbundene BSSID und alle BSSIDs derselben SSID aus den letzten
  Suchergebnissen. WLAN-Namen und BSSIDs sind privat: nicht ins Diagnose-Log, im
  Testdaten-Export ersetzt.
- `Occurrence.arrivedAt: Instant?` **[E]**: erkannte Ankunft im Zeitraum. Der Alarm liegt bei
  `arrivedAt + Puffer`; kurz wieder weg ändert daran nichts (OZ4). Ohne eigenes Feld müsste
  man den Puffer aus Ereignissen raten – genau das Problem, das E5 gelöst hat.
- `place` bleibt allgemein (OZ6), Radius pro Ort.

### Z.2 Ablauf
Zeitraum = [späteste Uhrzeit − Vorlauf, späteste Uhrzeit]; die Uhrzeit des Takts ist die
späteste Uhrzeit.

| Wann | da | klar weg | unklar |
|---|---|---|---|
| Ankunft vor dem Zeitraum (Geofence, WLAN) | ignoriert, Diag „zu früh“ | – | – |
| Beginn des Zeitraums (Prüfung) | klingelt zur spätesten Uhrzeit (F2) | weiter prüfen | weiter prüfen |
| im Zeitraum (Prüfung, WLAN, Geofence) | `arrivedAt`, Alarm nach dem Puffer | weiter prüfen | weiter prüfen |
| späteste Uhrzeit | klingelt | `DROP`: entfällt (F1); `RING`: klingelt | klingelt |
| nach Später/Verschieben (`awaitsArrival`) | klingelt | weiter prüfen | weiter prüfen; Tagesgrenze → verpasst |

- *Wenn ich zuhause bin* (ohne Uhrzeit): eine WLAN-Verbindung zählt wie eine Ankunft,
  keine Prüfungen, kein Zeitraum (OZ9).
- „Weiter prüfen“ heißt: der nächste Prüf-Alarm des Orts (Z.3). Mehrere Reminder am
  selben Ort teilen sich die Prüfung; es gilt der kürzeste Prüfabstand.

### Z.3 Ports
- **Prüf-Alarme pro Ort:** `PlaceAlarmPort.set(placeId, at)` / `cancel(placeId)`. Der
  `AlarmPort` kennt nur einen Alarm pro Fälligkeit, Prüfungen gehören aber zum Ort. Exakt
  genug ist `setWindow` mit 2 min. `ReminderEngine.onPlaceCheck(placeId)` sammelt alle
  Fälligkeiten, die an dem Ort warten, prüft einmal und plant den nächsten Prüf-Alarm.
- **Ortsergebnis als Fakt:** `LocationPort.check(place): PlaceCheck(placeId, source, result,
  scanAge, at)` mit `source` = `WIFI_CONNECTED` / `WIFI_SCAN` / `GEOFENCE` / `FALLBACK` und
  `result` = `AT` / `AWAY` / `UNKNOWN`. Er ersetzt `isAt(): Boolean?`; die Engine bekommt
  ihn über denselben Weg wie heute (`EngineContext.positions`, außerhalb der Transaktion,
  1.). Reihenfolge: verbunden → `AT`; Suche ≤ 10 min ohne WLAN des Orts → `AWAY`; sonst
  `UNKNOWN`. Erst zur spätesten Uhrzeit fällt `UNKNOWN` auf die heutige Prüfung (Geofence-
  Stand, grobe Ortsabfrage) zurück (OZ2). 7.30 ergänzt später `GPS` und eine Genauigkeit.
- **WLAN:** `WifiPort.connected(): Bssid?` und `WifiPort.lastScan(): Scan(bssids, at)?`
  (`WifiManager.getScanResults`, nie `startScan`). Verbindungen über
  `ConnectivityManager.registerNetworkCallback(request, PendingIntent)`; der Receiver ruft
  `ReminderEngine.onWifi(bssid)`. **[P]** BSSID und SSID gibt es nur mit
  Standort-Berechtigung (`FLAG_INCLUDE_LOCATION_INFO`), im Hintergrund mit „Immer
  erlauben“. Auf dem S24 mit Android 17 prüfen: Kommt der Callback zuverlässig, wie alt
  sind die Suchergebnisse im Hintergrund typischerweise, und muss der PendingIntent nach
  einem Neustart neu registriert werden (dann über `sync`)?

### Z.4 Android
- **Kein GPS** (Anf. 7.30, später). Wenn es kommt: `getCurrentLocation(PRIORITY_HIGH_ACCURACY)`
  mit Zeitlimit 30 s, nur im Zeitraum und nur bei `UNKNOWN`; notfalls ein kurzer
  Vordergrunddienst vom Typ `location`. Davor die Messung aus 7.30 GP3.
- **Geofence** bleibt (Ankunft ohne WLAN); die Verweildauer gilt nur für ihn (Anf. T2).
  Bei der Gelegenheit den offenen Punkt aus 6. lösen (Neuregistrierung nach
  `GEOFENCE_NOT_AVAILABLE`).
- **Diagnose (OZ8):** jede Prüfung mit Quelle, Ergebnis, Alter der Suche und Grund. Keine
  Koordinaten, keine WLAN-Namen, keine BSSIDs (nur „WLAN von Zuhause“).

### Z.5 Oberfläche
- Formular, Ankunft: *spätestens um* (die Uhrzeit), *ab* als Vorlauf-Chips (30 / 60 / 90 /
  120 min, *andere…*), Puffer und Prüfabstand unter *Feineinstellungen*.
- Orte: `setHomeHere` wird zu `setPlaceHere(placeId)`; Einstellungen *Orte* mit Zuhause und
  einem weiteren Ort (OZ6), jeweils *mit diesem WLAN verbunden* übernehmen. Die Meldung
  „Zuhause zuerst festlegen“ wird zu „Ort zuerst festlegen“.
- *Heute*: „Training – ab 18:00 bei Ankunft, sonst 19:30“.

### Z.6 Tests
- `Simulation` bekommt `wifi(bssid?)` und `scan(bssids, at)`; die Prüf-Alarme laufen wie die
  anderen Alarme.
- Tage in `DayScenarioTest`: 2026-11-04 nachgestellt (Ankunft 07:12 → kein Alarm; auf 20:30
  verschoben, *unklar* → wartet; Ankunft 20:34 → Alarm 20:39), Homeoffice (F2), nicht da
  (F1), zwei Reminder am selben Ort mit verschiedenen Prüfabständen.
- `MigrationTest` 8→9, `RealDataTest` mit den Standardwerten.

### Z.7 Zum Bestätigen
1. `Occurrence.arrivedAt` als eigenes Feld (Z.1).
2. Prüf-Alarme pro Ort mit eigenem Port (Z.3).
3. Ohne GPS (2026-11-05): *klar weg* nur aus einer Suche ≤ 10 min ohne WLAN des Orts,
   sonst *unklar* und Rückfall zur spätesten Uhrzeit (Z.3). Die **[P]**-Punkte zum WLAN
   prüfe ich zuerst mit einem kleinen Gerätetest.
4. Vorlauf 0 bedeutet „wie bisher“, auch als Ausweg, falls 7.29 im Alltag stört.
