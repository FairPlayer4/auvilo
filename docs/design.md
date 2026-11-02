# Design – avlreminder V1.0 bis V1.2

> Stand: 2026-10-24 – erster Entwurf. Grundlage: [anforderungen.md](anforderungen.md),
> Ergebnisse aus [spike.md](spike.md). Mocks: siehe Abschnitt 9.

## 1. Ziel von V1.0

Eine **benutzbare** erste Version für den Alltag, um früh echte Erfahrungen zu sammeln:

- Reminder nach Zeit (einmalig, täglich, an Wochentagen, Intervall ab letzter Erledigung)
- Eskalation nach Strenge und Zeitfenster
- Aktionen im Alarm: Erledigt, Minimum, Später, Verschieben, Auslassen
- Nachweis: Quittieren oder Foto (In-App-Kamera)
- Ziel und Minimum
- Liste der Erledigungen (Verlauf)
- Widget: ein Klick → neuer Reminder

**Nicht in V1.0** (kommt in V1.1/V1.2): Ort, Audio, Motivations-Audio, Wochenziele,
Fristen mit Ausführungsfenstern, Serien/Joker, Statistik, Übersicht morgens/abends.
Das Datenmodell ist aber so angelegt, dass diese Teile ohne Umbau dazukommen.

## 2. Architektur

```
┌──────────────────────────── :app (Android) ────────────────────────────┐
│  ui/        Compose-Screens, ViewModels                                 │
│  widget/    Glance-Widget                                               │
│  alarm/     AlarmScheduler, AlarmReceiver, AlarmService, AlarmActivity  │
│  system/    BootReceiver, Zeitänderung, App-Start-Sync                  │
│  data/      Room-DB, DAOs, Repository-Implementierungen, Dateien (Fotos)│
└───────────────┬─────────────────────────────────────────────────────────┘
                │ nutzt
┌───────────────▼──────────── :core (reines Kotlin, kein Android) ────────┐
│  model/     Reminder, Occurrence, Proof, Event, Enums                   │
│  rules/     Zeitplan (nächste Fälligkeit), Eskalationsstufe,            │
│             Lebenszyklus (Zustandsautomat), Limits                      │
│  ports/     Interfaces: ReminderRepository, AlarmPort, Clock            │
│  usecases/  CreateReminder, CompleteOccurrence, Postpone, Skip,         │
│             SyncSchedule (Alarme aus DB neu aufbauen), DetectMissed     │
└─────────────────────────────────────────────────────────────────────────┘
```

**Regeln:**
- `:core` kennt kein Android. Alle Regeln aus den Anforderungen leben dort und werden
  mit schnellen Unit-Tests abgesichert (Coverage ≥ 90 %, siehe Abschnitt 8).
- Zeit kommt immer über `Clock` (testbar, keine `System.currentTimeMillis()` in der Logik).
- **Die Datenbank ist die einzige Wahrheit.** Alarme und (später) Geofences sind nur
  abgeleitet und werden jederzeit aus der DB neu aufgebaut (Erkenntnis aus dem Spike:
  Neustart und Force-Stop löschen alle Alarme).
- Keine DI-Bibliothek: ein kleiner manueller Container (`AppGraph`) reicht für V1.

## 3. Datenmodell

### 3.1 Reminder (die Definition)

| Feld | Typ | Bedeutung |
|---|---|---|
| id | Long | |
| title | String | "Katzenbrunnen reinigen" |
| why | String? | "Warum ist mir das wichtig?" |
| character | Enum | `OVERCOME` (Überwindung) / `DOCUMENT` (Dokumentation) |
| strictness | Enum | `RELAXED` / `NORMAL` / `STRICT` (Vorlage, siehe 5) |
| schedule | eingebettet | siehe 3.2 |
| goal | String? | Zielwert, z. B. "45 min" |
| minimum | String? | Minimalziel, z. B. "10 min"; null = kein Minimum |
| minimumLimitPerWeek | Int? | null = unbegrenzt |
| proof | Enum | `NONE` / `CONFIRM` / `PHOTO` (später `LOCATION`, `AUDIO`, Kombination) |
| postponeLimit | Int? | max. Verschiebungen pro Fälligkeit; null = unbegrenzt |
| active | Boolean | pausiert / aktiv |
| createdAt | Instant | |

### 3.2 Zeitplan (Takt)

| Art | Felder | Beispiel |
|---|---|---|
| `ONCE` | dateTime | Arzt anrufen am 28.10. 09:00 |
| `DAILY` | time | Täglich 08:00 |
| `WEEKDAYS` | time, days | Mo–Fr 18:30 |
| `AFTER_DONE` | intervalDays, time | 14 Tage nach letzter Erledigung, 19:00 |

Später: `CALENDAR_MONTHLY`, `TIMES_PER_PERIOD` (Wochenziel), `DEADLINE` + Ausführungsfenster,
Ort-Trigger.

### 3.3 Occurrence (eine konkrete Fälligkeit)

| Feld | Typ | Bedeutung |
|---|---|---|
| id | Long | |
| reminderId | Long | |
| dueAt | Instant | ursprünglich geplant |
| currentAt | Instant | aktueller Zeitpunkt (nach Verschieben/Später) |
| state | Enum | siehe Abschnitt 4 |
| postponeCount | Int | |
| resolvedAt | Instant? | |
| resolution | Enum? | `GOAL`, `MINIMUM`, `NO_PROOF`, `SKIPPED`, `MISSED` |
| note | String? | Freitext, Grund beim Auslassen |

Es existiert pro Reminder immer höchstens **eine offene** Occurrence. Wird sie
abgeschlossen, erzeugt `SyncSchedule` die nächste.

### 3.4 Proof und Event

- **Proof:** occurrenceId, type, filePath (App-interner Speicher), takenAt.
- **Event:** occurrenceId, at, type (`DUE`, `ESCALATED`, `SNOOZED`, `POSTPONED`,
  `COMPLETED`, `MISSED_DETECTED`, …), detail. Unveränderliches Protokoll – Grundlage
  für Verlauf, spätere Statistik und Fehlersuche.

## 4. Lebenszyklus (Zustandsautomat)

```
            ┌──────────── Später (+x min) ────────────┐
            ▼                                         │
PLANNED ──(Zeit erreicht)──► DUE ──(keine Reaktion)──► ESCALATING
   ▲                          │                         │
   │ Verschieben (Limit)      ├── Erledigt ──► Nachweis ok ──► DONE (GOAL | MINIMUM)
   └──────────────────────────┤                Nachweis fehlt ─► DONE (NO_PROOF)
                              ├── Auslassen (Grund) ──► CLOSED (SKIPPED, neutral)
                              └── Ende des Tages ohne Reaktion ─► CLOSED (MISSED)
```

- Übergänge sind **reine Funktionen** in `:core` (`transition(state, action, now) → state + events`)
  und vollständig per Unit-Test abgedeckt.
- **Verpasst-Erkennung:** Bei jedem Sync (Alarm, App-Start, Neustart) werden
  Occurrences mit `currentAt` in der Vergangenheit geprüft. War kein Alarm ausgelöst
  worden (Akku-Management, Force-Stop), wird das als Event `MISSED_DETECTED` vermerkt
  und der Reminder sofort nachgeholt – nichts verschwindet still.
- **Minimum-Limit:** Ist das Limit der Woche erreicht, ist *Minimum* ausgegraut.
- **Verschiebe-Limit:** Ist es erreicht, bleibt nur *Erledigt*, *Minimum* oder *Auslassen*.

## 5. Eskalation

Stufe = f(Strenge, Uhrzeit). Zeitfenster global in den Einstellungen.

| Strenge | 7–18 Uhr | 18–21 Uhr | außerhalb 7–21 |
|---|---|---|---|
| `RELAXED` | Banner + Vibration, alle 10 min wiederholen | dto. | still |
| `NORMAL` | **Vollbild** + Vibrationsmuster in Schleife (Wecker ohne Ton) | dto. | still |
| `STRICT` | **Vollbild** + Vibrationsmuster in Schleife | **Vollbild + Vibration + Alarmton** | still |

- Wiederholung bis Reaktion; Timeout pro Runde 3 min, danach nächste Runde nach 10 min.
  Ohne Reaktion bis Fensterende (21 Uhr) → `MISSED`.
- Startwerte der Vorlagen (aus Anforderungen 7.9):
  `RELAXED` Quittieren, Verschieben unbegrenzt · `NORMAL` Nachweis wählbar, 3× Verschieben ·
  `STRICT` Nachweis Pflicht, 1× Verschieben, Minimum max. 1×/Woche.
  Alle Werte pro Reminder überschreibbar.

## 6. Android-Anbindung (aus dem Spike übernommen)

| Thema | Lösung |
|---|---|
| Alarm | `setAlarmClock()` für `NORMAL`/`STRICT` (0 s Verspätung im Spike); `setExactAndAllowWhileIdle()` für `RELAXED`, damit nicht jeder Reminder als "nächster Wecker" erscheint |
| Dauer-Alarm | Foreground-Service Typ `systemExempted` |
| Vollbild | Full-Screen-Intent + Activity mit `showWhenLocked`/`turnScreenOn` |
| Neuaufbau | `SyncSchedule` bei: Änderung, Alarm, App-Start, `BOOT_COMPLETED` (kommt auch nach Force-Stop beim nächsten Start), `TIME_SET`/`TIMEZONE_CHANGED` |
| Fotos | CameraX, Speicherung in `filesDir/proofs/`, nicht in der Galerie |
| Widget | Jetpack Glance, öffnet direkt "Neuer Reminder" |
| Berechtigungen | Einrichtungsseite beim ersten Start (wie Spike): Benachrichtigungen, Vollbild, Akku nicht optimieren, Kamera |

## 7. Darstellung: Hell, Dunkel, System

- Einstellung **Darstellung**: *System* (Standard) · *Hell* · *Dunkel*. Gespeichert mit
  DataStore (Preferences), wirkt sofort ohne Neustart.
- Ein `AvlTheme` mit zwei Material-3-`ColorScheme`s plus eigenen Farben für Strenge und
  Ergebnis (`AvlColors` über `CompositionLocal`). Screens verwenden **nur Tokens**, keine
  festen Farbwerte – so kostet der Dunkelmodus später nichts extra.
- **Dynamic Color (Material You) aus:** Die Strenge-Farben sollen immer dasselbe bedeuten.
- **Alarm-Vollbild ist in beiden Modi dunkel:** blendet nachts nicht, hoher Kontrast,
  sofort als Alarm erkennbar.
- **Widget** folgt dem System-Modus (Glance `ColorProviders` mit Tag-/Nachtfarben).
- Edge-to-Edge; Symbole in Status- und Navigationsleiste passen sich dem Modus an.
- Kontrast: Text ≥ 4,5 : 1 in beiden Modi. Strenge und Ergebnisse unterscheiden sich
  auch in der Helligkeit, nicht nur im Farbton.

| Token | Hell | Dunkel |
|---|---|---|
| Hintergrund | `#F5F6F8` | `#101216` |
| Fläche | `#FFFFFF` | `#1B1E24` |
| Text | `#16191F` | `#ECEEF2` |
| Text gedämpft | `#5B6270` | `#A9B0BC` |
| Linie | `#E2E5EA` | `#2E323B` |
| Akzent (Text, Rahmen) | `#1F4FD1` | `#8AA8FF` |
| Akzent (Fläche mit weißer Schrift) | `#1F4FD1` | `#2F5FE0` |
| Streng / verpasst | `#B4400A` | `#FF9A62` |

## 8. Tests und Qualität

| Bereich | Test-Art | Ziel |
|---|---|---|
| `:core` (Regeln, Use-Cases) | JUnit 5, Fake-Clock, Fake-Repositories | **Coverage ≥ 90 %** (Kover, Build schlägt sonst fehl) |
| `:app` data | Room In-Memory-Tests | wichtige Abfragen, Migrationen |
| `:app` alarm/system | wenige Robolectric-Tests + Geräte-Checkliste (wie Spike) | keine Coverage-Zahl |
| `:app` ui | Compose-UI-Tests für Kernabläufe (anlegen → Alarm → erledigen, Darstellung umschalten); Compose-Previews jedes Screens in Hell und Dunkel | keine Coverage-Zahl |

- Tests werden zusammen mit dem Code geschrieben, nicht nachträglich.
- Regeln aus den Anforderungen bekommen sprechende Testnamen
  (z. B. `minimum is disabled when weekly limit is reached`).

**Tech:** AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09, Room + KSP,
Glance, CameraX, Kover. minSdk = targetSdk = 37. Paket `com.avlreminder`.

## 9. Screens (siehe Mocks)

Klickbare Mocks: https://claude.ai/artifact/PXA9YgWi8XoHupYnN7Z3cS

1. **Heute** – offene und anstehende Reminder des Tages, Erledigtes ausgegraut; FAB "+"
2. **Reminder anlegen** – Titel, Zeitplan, Strenge (Vorlage), Ziel/Minimum, Nachweis, Warum
3. **Alarm (Vollbild)** – Titel, Warum, Aktionen: Erledigt · Minimum · Später · Verschieben · Auslassen
4. **Nachweis** – Kamera-Vorschau bzw. Quittieren; Notiz optional
5. **Verschieben** – Schnellwahl (in 1 h, heute Abend, morgen) + Datum/Uhrzeit, Restanzahl
6. **Verlauf** – Erledigungen nach Tagen, mit Ergebnis (Ziel/Minimum/ohne Nachweis/ausgelassen/verpasst)
7. **Widget** – "+ Reminder" und nächster fälliger Reminder
8. **Einstellungen** – Darstellung (System/Hell/Dunkel), Zeitfenster, Berechtigungen

## 10. Offene Punkte

- ~~Ende einer Fälligkeit außerhalb des Eskalationsfensters~~ → ab V1.1 Tagesgrenze (11.3).
- ~~Mehrtagestest~~ → abgeschlossen 2026-10-27, keine Probleme mit Samsungs
  Akkumanagement (siehe [spike.md](spike.md)).

## 11. V1.1 – Design-Anpassungen

> Stand: 2026-10-27 – Entwurf. Grundlage: Anforderungen bis "sechzehnter Entwurf"
> und Spike-Abschluss.

Die Architektur (`:core` rein, DB als einzige Wahrheit, alles Abgeleitete jederzeit
neu aufbaubar) trägt auch V1.1. Anpassen müssen sich vor allem **Datenmodell**,
**Eskalation** und **Nachweis**; dazu kommen zwei neue Konzepte: **Ort-Trigger** und
**Checklisten**.

### 11.1 Schnitt in zwei Pakete

V1.1 ist groß geworden. Damit früh wieder etwas aufs Handy kommt:

| Paket | Inhalt | Warum zuerst / danach |
|---|---|---|
| **V1.1a – Alarm und Nachweis** | "Nicht stören" + Tagesgrenze, Verschiebe-Knöpfe, Alarm aus während des Nachweises, Text- und Audio-Nachweis (kombinierbar), vorab erledigen, Vollbild-Alarme auf der Uhr, Startklar-Fragen, Motivations-Audio | Betrifft jeden Tag; erweitert Vorhandenes, keine neuen Grundkonzepte |
| **V1.1b – Ort und Checklisten** | Ort-Trigger mit Verweildauer und "sonst um 19 Uhr / entfällt", Checklisten | Neue Konzepte (Trigger innerhalb des Tages, teilweise Erledigung über mehrere Tage) |

### 11.2 Datenbank: Migration statt Neuanlage

Auf dem Handy liegen echte Daten. Deshalb ab jetzt:
- Room-Version steigt pro Paket (V1.1a → 2, V1.1b → 3), jeweils mit **expliziter
  Migration**; kein `fallbackToDestructiveMigration`.
- **Migrations-Tests** gegen die exportierten Schemas (`app/schemas/`).
- Vor der ersten Migration legt die App eine **Kopie der DB im App-Speicher** ab
  (`filesDir/backup/`). Die Daten verlassen das Handy nicht.

### 11.3 Eskalation: "Nicht stören" statt Zeitfenster

`EscalationWindows` wird zu `AlertSettings` (global, Einstellungen):

| Einstellung | Standard |
|---|---|
| `loudFrom` | 18:00 (laut bei *streng*, wenn erlaubt) |
| `dayEnd` (Tagesgrenze) | 23:00 |

Neuer Port `DeviceStatePort.isDoNotDisturb()` (Android:
`getCurrentInterruptionFilter() != ALL`). Neues Feld `Reminder.ignoreDnd` ("auch bei
Nicht stören").

| Strenge | normal | "Nicht stören" aktiv | … und `ignoreDnd` |
|---|---|---|---|
| `RELAXED` | Banner + Vibration | still | Banner + Vibration |
| `NORMAL` | Vollbild + Vibration | still | Vollbild + Vibration |
| `STRICT` | Vollbild + Vibration, ab 18 Uhr laut | still | wie normal |

- **Runden:** immer im 10-Minuten-Takt (auch während "Nicht stören", dann still); das
  Warten bis 7 Uhr entfällt. Die erste Runde nach dem Ende alarmiert normal.
- **Verpasst:** an der Tagesgrenze des Tages, an dem die Fälligkeit liegt; liegt sie
  nach der Tagesgrenze, an der Tagesgrenze des Folgetags.
- `ignoreDnd` braucht die Freigabe der App als Ausnahme in "Nicht stören". Die
  Berechtigungsseite prüft sie (Kanal darf DND umgehen) und warnt, wenn ein Reminder
  `ignoreDnd` nutzt, die Freigabe aber fehlt.

### 11.4 Nachweis

**Modell:** `ProofType` (eine Art) wird zu `ProofSpec`:

| Feld | Bedeutung |
|---|---|
| `kind` | `NONE` / `CONFIRM` / `EVIDENCE` |
| `photo` | Foto verlangt |
| `textMinChars` | Text verlangt, Mindestzeichen ohne Leerzeichen; null = kein Text |
| `audioMinSeconds` | Audio verlangt, Mindestdauer; null = kein Audio |
| `windowMinutes` | Frist für den Nachweis, Standard 10 |

- Bestehende Daten: `PHOTO` → `EVIDENCE` mit `photo = true`.
- Tabelle `proof` bekommt `text`; `type` ∈ `PHOTO`, `TEXT`, `AUDIO`. Audio in
  `filesDir/audio/` (wie Fotos: nur App-Speicher).
- `Action.Complete` trägt die erbrachten Nachweise. `:core` prüft Text (Mindestzeichen,
  nicht nur ein wiederholtes Zeichen, nicht identisch mit dem letzten Text) und
  Audio-Dauer. Fehlt etwas, bleibt wie bisher *ohne Nachweis abschließen* → `NO_PROOF`.

**Alarm aus während des Nachweises (N0):** neues Feld `Occurrence.pausedUntil`.
- `Action.StartProof` setzt es auf jetzt + `windowMinutes` (höchstens Tagesgrenze); die
  App stoppt Ton und Vibration; der nächste Alarm liegt bei `pausedUntil`.
- `Action.AbortProof` (Zurück, App verlassen) → `pausedUntil` = jetzt + 1 min.
- Gespeichert wird das Ende statt des Starts: Der nächste Alarm ergibt sich direkt aus
  dem Feld, auch nach einem Neustart oder einer späteren Synchronisation.
- Events `PROOF_STARTED`, `PROOF_ABORTED` – auch Grundlage für die Statistik.
- Bewusst ein Feld statt eines eigenen Zustands: Der Zustandsautomat bleibt klein.

### 11.5 Verschiebe-Knöpfe

`Reminder.snoozeMinutes: List<Int>` (1–2 Werte, Standard `[10]`). Benachrichtigung,
Uhr und Vollbild zeigen dieselben Knöpfe. Ein Knopf, der über die Tagesgrenze führen
würde, wird ausgeblendet. Kurzes Verschieben bleibt ohne Limit; jedes zählt als
Event `SNOOZED`.

### 11.6 Vorab erledigen

Der Zustandsautomat erlaubt `Complete` auf `PLANNED` schon heute; die Zeitplan-Regeln
liefern danach den richtigen nächsten Termin (getestet für *täglich*, *Wochentage*,
*nach Erledigung*). Neu sind nur:
- Knopf *Erledigen* in *Später heute* und in der Detailansicht (Nachweis wie im Alarm).
- Anzeige "vor dem Reminder erledigt" im Verlauf: abgeleitet aus `resolvedAt < dueAt`,
  kein neues Feld.

### 11.7 Vollbild-Alarme auf der Uhr

Die Vordergrund-Benachrichtigung des Alarms ist "laufend" und wird von Android nicht
auf die Uhr gespiegelt. Lösung: zusätzlich eine **normale Benachrichtigung** mit
denselben Aktionen, die mit dem Alarm verschwindet; die laufende bekommt
`setLocalOnly(true)`. Ob die Uhr dabei pro Runde vibriert und ob am Handy ein doppelter
Eintrag stört, entscheidet der Gerätetest – notfalls bündelt eine Gruppe beide.

### 11.8 Startklar-Fragen und Motivations-Audio

- Neue Tabelle `prep_answer` (reminderId, question 1–4, text, `unknown`
  ("weiß ich noch nicht"), at) – **nur anhängen**: Die aktuelle Antwort ist die
  neueste pro Frage, der Verlauf ergibt sich von selbst.
- Pflicht bei *Überwindung* beim Anlegen; bestehende Reminder werden beim nächsten
  Bearbeiten gefragt (Alarme werden nie blockiert).
- Der Alarm zeigt Ziel (Frage 1), ersten Schritt (Frage 4) und offene Punkte
  (Fragen 2/3 bzw. *weiß ich noch nicht*).
- `Reminder.motivationAudio` (Datei in `filesDir/audio/`), im Alarm nur auf Klick
  abspielbar. Aufnahme-Komponente teilen sich Motivations-Audio und Audio-Nachweis
  (`MediaRecorder`, AAC). Mikrofon kommt auf die Berechtigungsseite.

### 11.9 Ort-Trigger (V1.1b)

- Neue Tabelle `place` (id, name, lat, lon, radius; Standard 150 m). V1.1b: nur
  "Zuhause", gesetzt über *aktuellen Standort*.
- **Der Zeitplan bestimmt den Tag, der Trigger den Moment:** `Reminder.trigger =
  Arrival(placeId, fallback: LocalTime?, dropIfAway: Boolean)`. Beispiel Training:
  Mo–Fr, bei Ankunft; sonst 19:00, falls zuhause; sonst entfällt.
- Neue Ports `GeofencePort` (wie `AlarmPort`: aus der DB abgeleitet, bei jedem Sync
  neu registriert) und `LocationPort.isAt(place)` für die aktive Prüfung.
- **Ankunft bestätigen:** Geofence-Übergang *Dwell* mit Verweildauer aus den
  Einstellungen (Standard 3 min). Vorbeifahren löst so nicht aus.
- Zur Fallback-Zeit prüft die App den Standort aktiv: da → Alarm, weg → `Action.Drop`
  → `CLOSED` mit neuer Resolution **`DROPPED`** ("entfallen", neutral).
- Nach Neustart/Update: Standort aktiv prüfen statt auf die automatische Meldung zu
  warten (Spike-Erkenntnis).
- "Wenn ich zuhause bin" (einmalig): Trigger ohne Fallback; ist man beim Anlegen
  schon da, ist es sofort fällig.
- Berechtigung *Standort im Hintergrund* kommt auf die Berechtigungsseite.

### 11.10 Checklisten (V1.1b)

- Neue Tabellen `checklist_item` (reminderId, position, title) und `item_progress`
  (occurrenceId, itemId, doneAt, plannedFor).
- Eine Occurrence mit Checkliste kann **über mehrere Tage offen** bleiben:
  `Action.DeferRest(to)` setzt sie mit den Restpunkten auf `PLANNED`; zählt nicht gegen
  das Verschiebe-Limit, wenn heute mindestens ein Punkt erledigt wurde (C5c).
- Alle Punkte erledigt → `GOAL`; *Minimum* schließt den Block mit `MINIMUM`.
- *Nach Erledigung*: Das Intervall zählt ab dem **ersten** erledigten Punkt (neues Feld
  `Occurrence.firstProgressAt`).
- Nachweis pro Punkt möglich: `proof.itemId` (optional).
- Benachrichtigung: Platz für 3 Aktionen → nächster offener Punkt ✓, Verschiebe-Knopf,
  App öffnen; die volle Liste im Vollbild bzw. in der App.
- Verteilung beim Start planen (C4, `plannedFor`): wenn das Paket zu groß wird, nach
  V1.2.

### 11.11 Lebenszyklus V1.1

```
PLANNED ──► DUE ──► ESCALATING
  │  ▲        │ ▲       │
  │  │        │ └ Nachweis läuft (pausedUntil, Frist) ┘
  │  └ Später / Verschieben / Rest auf morgen (Checkliste)
  ├── vorab erledigt ──────────────► DONE (GOAL | MINIMUM | NO_PROOF)
  ├── Ort: nicht da zur Fallback-Zeit ► CLOSED (DROPPED, neutral)
  └── … wie V1.0: SKIPPED, MISSED (jetzt an der Tagesgrenze)
```

### 11.12 Tests

Wie Abschnitt 8, zusätzlich:
- `:core`: Eskalation mit "Nicht stören" und Tagesgrenze, Nachweis-Prüfung, Frist des
  Nachweises, vorab erledigen pro Zeitplan-Art, Ort-Trigger inkl. `DROPPED`,
  Checklisten-Regeln (C5–C5c). Coverage bleibt ≥ 90 %.
- `:app`: Migrations-Tests 1→2→3.
- Gerätetest ([geraetetest.md](geraetetest.md)) erweitert um: "Nicht stören" an/aus,
  Ausnahme-Freigabe, Uhr bei *normal*/*streng*, Nachweis-Frist, Ankunft mit
  Verweildauer, Vorbeifahren.

### 11.13 Umsetzung V1.1a – Abweichungen und Präzisierungen

- **`pausedUntil` statt `proofStartedAt`** (siehe 11.4).
- **Migration ohne Tabellen-Neubau:** Die Spalte `reminder.proof` bleibt und trägt jetzt
  `ProofKind`; die übrigen Felder kommen per `ALTER TABLE ADD COLUMN`. So greift keine
  Fremdschlüssel-Kaskade auf Vorkommen, Events und Nachweise.
- **Backup** per `VACUUM INTO` nach `filesDir/backup/avlreminder-v1-<Zeit>.db`, nur wenn
  die vorhandene DB älter ist. Scheitert die Kopie, startet die App trotzdem (Log-Eintrag).
- **App verlassen während des Nachweises** (auch Bildschirm aus) zählt als Abbruch:
  Alarm in 1 min. Bei der Rückkehr startet die Frist neu.
- **Ausnahme bei "Nicht stören"** wird über die Kanäle geprüft (`canBypassDnd` für Alarm
  und Banner). Der Eintrag auf der Berechtigungsseite erscheint nur, wenn ein aktiver
  Reminder `ignoreDnd` nutzt, und öffnet die Kanal-Einstellungen.
- **Uhr-Kopie** auf einem eigenen Kanal *Alarm auf der Uhr* (ohne Ton, mit Vibration).
- **Schnell-Reminder "Meeting"** hat jetzt Charakter *Dokumentieren* und keinen Nachweis,
  damit er ohne Startklar-Fragen gespeichert werden kann.
- **Startklar-Pflicht** gilt beim Speichern (auch beim Bearbeiten älterer Reminder);
  gespeichert werden nur geänderte Antworten.
- **Vorab erledigen** gibt es in V1.1a nur über *Später heute*; eine eigene
  Detailansicht kommt später.
- **Ersetzte Motivations-Audios** werden beim Speichern gelöscht.
- **Tagesgrenze und "laut ab"** sind in den Einstellungen wählbar (laut 16–20 Uhr,
  Grenze 21–23 Uhr).

### 11.14 Umsetzung V1.1b – Abweichungen und Präzisierungen

- **Modell:** Statt `Reminder.trigger` gibt es `Reminder.arrival = Arrival(placeId,
  whenAway)`. Die Uhrzeit des Zeitplans ist die Fallback-Zeit; `whenAway` ist *entfällt*
  (`DROP`, Standard) oder *trotzdem klingeln* (`RING`). "Wenn ich zuhause bin" ist eine
  eigene Zeitplan-Art `Schedule.OnArrival` (einmalig, ohne Uhrzeit).
- **Ankunft** klingelt nur das heutige, noch geplante Vorkommen vor seiner Uhrzeit.
  Gesnoozte oder verschobene Vorkommen behalten ihre Zeit.
- **Geofence:** ein *Dwell*-Geofence pro benötigtem Ort, Verweildauer 1/3/5/10 min
  (Standard 3). Kein Initial-Trigger. Neu registriert wird nur, wenn sich Orte oder
  Verweildauer ändern (Merker im Prozess; nach einem Neustart ist er leer). Fehlt
  "Immer erlauben", wird nichts registriert und die Uhrzeit allein zählt.
- **Aktive Standortprüfung** (ein frischer Fix, höchstens 8 s):
  - Zur Fallback-Zeit bei `DROP`: sicher drin → Alarm; sicher draußen (Abstand minus
    Genauigkeit > Radius) → `DROPPED`. Unbekannt oder ungenau → Alarm, damit nichts
    verloren geht.
  - Für `OnArrival` nach dem Speichern, nach dem Setzen von "Zuhause" und nach
    Neustart/Update. Für zeitbasierte Reminder ist keine Prüfung nötig, die
    Fallback-Zeit deckt es ab.
- **Checklisten:** Tabelle `item_done` (occurrenceId, itemId, doneAt, withProof) statt
  `item_progress`; `plannedFor` entfällt mit C4 (→ V1.2).
  - Punkte werden einzeln abgehakt (`Action.CheckItem`), auch vorab. Der letzte schließt
    den Block: `GOAL`, wenn jeder Punkt seinen Nachweis hat, sonst `NO_PROOF`.
  - *Alles erledigt* schließt mit einem Nachweis für den Rest. *Minimum* schließt mit
    `MINIMUM`, ohne die Punkte abzuhaken.
  - **Tagesgrenze:** Wurde an dem Tag ein Punkt erledigt, kommt der Rest am nächsten Tag
    zur gleichen Uhrzeit wieder (zählt nicht als Verschieben, C3). Sonst verpasst.
  - *Rest auf morgen* im Alarm: kostenlos nach einem Punkt am selben Tag, sonst zählt es
    gegen das Verschiebe-Limit.
  - Nachweis pro Punkt über die Nachweis-Seite (`proof.itemId`).
- **Benachrichtigung:** "✓ nächster Punkt" nur bei Remindern ohne Nachweis. Nach dem
  Abhaken kommt eine stille Benachrichtigung mit dem nächsten Punkt.
- **Punkte bearbeiten:** Gebliebene Punkte behalten ihre Id und damit ihren Fortschritt.
  Entfernte Punkte nehmen ihren Fortschritt mit; ihre Nachweise bleiben.
- **Migration 2→3** nur mit `ADD COLUMN` und neuen Tabellen. Das Backup
  `avlreminder-v2-<Zeit>.db` entsteht wie in 11.13.

## 12. V1.2 – Schnitt und Entwurf

### 12.1 Schnitt in drei Etappen
V1.2 ist für einen Schritt zu groß und zum Teil noch offen. Deshalb (2026-10-29):

| Etappe | Inhalt | Stand |
|---|---|---|
| **V1.2a – Alltag** | Verbesserung vorschlagen (Anf. 7.24), Reminder-Liste aufräumen (Anf. 7.9, L1–L5) | umgesetzt, installiert |
| **V1.2b – Ziele über die Zeit** | Wochenziele, Serien/Joker, Statistik, Übersicht morgens/abends | Entwurf 12.3 bestätigt, umgesetzt (12.5) |
| **V1.2c – Fristen und Blocker** | Fristen mit Ausführungsfenstern, Vorab-Reminder (T6), Blocker als Aufgaben (S5–S8), eigene Vorlagen | Entwurf 12.4 vollständig (2026-11-02), Prüfung durch Kevin offen |

Der Vorab-Reminder (T6) war bisher keiner Etappe zugeordnet; S6 braucht ihn, deshalb V1.2c.

### 12.2 V1.2a – Design
- **Archiv:** `reminder.archivedAt` (null = nicht archiviert). Archivieren pausiert den
  Reminder und setzt `archivedAt`; Wiederherstellen löscht nur `archivedAt`, der Reminder
  bleibt pausiert. Einschalten und das Verschieben eines Einmal-Reminders auf eine neue
  Zeit heben das Archiv auf. Das Formular kennt `archivedAt` nicht; die Engine übernimmt
  den gespeicherten Wert.
- **Abgeschlossen** ist abgeleitet, nicht gespeichert: ein Einmal-Reminder oder *Wenn ich
  zuhause bin* ohne offene Fälligkeit.
- **Liste** (`ReminderList` in `:core`): Gruppen *aktiv*, *Pausiert*, *Abgeschlossen*
  (archiviert oder abgeschlossen, eingeklappt). Sortierung *nächste Fälligkeit*
  (Standard; ohne Fälligkeit ans Ende, dann nach Name), *Name*, *zuletzt angelegt*. Die
  Wahl liegt in den Einstellungen (DataStore).
- **Vorschläge:** Tabelle `suggestion` (Text, wichtig, Zeitpunkt, Bildschirm, Reminder-Id
  und -Titel, App-Version, Status *offen / umgesetzt / verworfen* mit Zeitpunkt). Keine
  Fremdschlüssel: Ein Vorschlag bleibt, wenn der Reminder gelöscht wird; der Titel ist
  deshalb mitgespeichert. Vorschläge sind keine Reminder-Logik und liegen nur in `:app`.
- **Einstieg:** Glühbirnen-Symbol oben rechts auf jedem Bildschirm (auf *Heute* neben den
  Einstellungen, beim Bearbeiten vor *Speichern*). Im Alarm ein unauffälliger Textknopf
  ganz unten; der Alarm läuft dabei weiter.
- **Liste der Vorschläge:** aus den Einstellungen erreichbar; wichtige zuerst, dann die
  neuesten; *Alle offenen kopieren* und *Teilen* als Markdown-Liste mit Kontext.
- **Migration 3→4** nur mit `ADD COLUMN` und neuer Tabelle, Backup wie in 11.13.

### 12.3 V1.2b – Entwurf zur Prüfung
Vorschläge zu den offenen Fragen; Kevin bestätigt oder korrigiert vor der Umsetzung.
1. **Wochenziel als Eigenschaft** eines wiederkehrenden Reminders (z. B. Training:
   Mo–Fr angeboten, Ziel 3–4×, Minimum 1×), keine eigene Reminder-Art. Woche Mo–So.
2. **"3–4×"**: Ab 3 ist das Ziel erreicht; weitere Angebote kommen nur noch still, ohne
   Eskalation. Ab 4 wird für den Rest der Woche nichts mehr angeboten.
3. **Minimum zählt mit** für das Wochenziel; die Statistik weist es getrennt aus.
4. **Knapp:** Reichen die restlichen Angebotstage nur noch genau, eskaliert der Reminder
   eine Stufe strenger.
5. **Rettungs-Erinnerung:** Steht die Woche bei 0, kommt Samstag 10 Uhr eine strenge
   Erinnerung, Sonntag 10 Uhr noch einmal.
6. **Serien:** tägliche Reminder zählen Tage, Reminder mit Wochenziel Wochen mit
   erreichtem Minimum, Intervall-Reminder Erledigungen, die höchstens einen Tag nach dem
   Plan kamen (am unsichersten). Joker: 1 pro Woche bei täglichen, 1 pro Monat bei
   wöchentlichen; Auslassen und *entfällt* zählen neutral.
7. **Statistik** pro Reminder: erledigt, Minimum, ausgelassen, verpasst; aktuelle und beste
   Serie; Woche, Monat, Jahr, gesamt. Zuerst als Liste, Diagramme später.
8. **Übersicht** als stille Benachrichtigung um 7:30 und 20:30 (einstellbar); ein Tipp
   öffnet die Übersicht in der App. Abends mit Stand der Wochenziele.

### 12.4 V1.2c – Entwurf zur Prüfung
Vollständig seit 2026-11-02 (9–11 vom 2026-10-29, 12–16 neu). Kevin bestätigt oder
korrigiert vor der Umsetzung.

**Fristen (T1d, 7.2)**
9. **Neuer Takt `Deadline`:** Frist (Datum, optional Uhrzeit, sonst Tagesende) plus
   **Ausführungsfenster**: Wochentage und *ein* Zeitfenster von–bis für alle gewählten Tage
   (Standard Mo–Fr 9–17), dazu ein **Erinnerungsintervall** (Standard 60 min). Es gibt
   eine einzige Fälligkeit: Sie klingelt zu Beginn jedes Fensters und dann im Intervall,
   bis sie erledigt ist. Außerhalb der Fenster ist Ruhe.
10. **Auf morgen** geht, solange danach noch mindestens ein Fenster vor der Frist liegt.
    **Letzter Fenstertag:** eine Stufe strenger (wie *knapp* bei Wochenzielen). **Nach der
    Frist** gilt die Fälligkeit als verpasst, bleibt aber in *Heute* als *überfällig*
    sichtbar, bis sie erledigt oder verworfen ist (Leitprinzip 1).

**Vorlagen (7.9)**
11. *Als Vorlage speichern* an einem Reminder. Eine Vorlage übernimmt Charakter, Strenge,
    Nachweis, Minimum mit Limit, Verschiebe-Knöpfe, *Nicht stören*-Ausnahme und die Art
    des Takts mit Zeiten (z. B. Fenster Mo–Fr 9–17). **Nicht** übernommen: Titel, Warum,
    Startklar-Antworten, Motivations-Audio, konkretes Datum. Widget und *+* zeigen zuerst
    die Vorlagenauswahl (zuletzt benutzte oben, dazu *Leer*). Vorlagen lassen sich in den
    Einstellungen umbenennen und löschen.

**Vorab-Reminder (T6, S6)**
12. Pro Reminder optional: *am Vorabend* (Uhrzeit, Standard 19:00) oder *x Minuten
    vorher*. Eine **stille Benachrichtigung** mit Titel, erstem Schritt und **offenen
    Blockern** („Morgen Training – Bereich ist noch nicht aufgeräumt“). Tipp öffnet den
    Reminder. Entfällt, wenn die Fälligkeit vorab erledigt wurde (7.20).

**Blocker (S5, S7, S8)**
13. **Blocker als eigene Liste** pro Reminder statt nur als Antwort auf Frage 3: neue
    Tabelle `blocker` (Text, angelegt, gelöst am, Herkunft *Startklar / Nachfrage*,
    Vorbereitungsaufgabe). Die Antwort „was fehlt“ (Frage 3) wird beim Speichern zu einem
    Blocker; bestehende Antworten werden bei der Migration übernommen. Der Verlauf der
    Startklar-Antworten bleibt wie in 11.8.
14. **Vorbereitungsaufgabe (S5):** Am Blocker *Als Aufgabe planen* legt einen
    Einmal-Reminder an (Charakter *Dokumentation*, Strenge *locker*, Termin vorgeschlagen:
    Vorabend der nächsten Fälligkeit 19:00), verknüpft über `reminder.prepFor`.
    Erledigen = Blocker gelöst. Die Aufgabe steht in *Heute* zu ihrem Termin und beim
    Hauptreminder unter *Startklar*, nicht als eigener Eintrag in der Reminder-Liste.
15. **Blocker gelöst als Minimum (S7):** pro Reminder einschaltbar. Wurde seit der
    letzten Fälligkeit ein Blocker gelöst, bietet der Alarm unter *Minimum* zusätzlich
    *Blocker gelöst* an. Zählt wie ein Minimum (Limit, Wochenziel, Serie) und steht so
    in der Statistik.
16. **Nachfragen, wenn es hakt (S8):** Nur bei *Überwindung*. Zählen über aufeinander
    folgende Fälligkeiten: verschoben (*Verschieben auf …*), ausgelassen, verpasst; drei
    kurze Verschiebungen (+x min) in einer Fälligkeit zählen als eins. Ab der Schwelle
    (Standard 2, in den Einstellungen 1–5) fragt der nächste Alarm zuerst *Was hält dich
    gerade ab?* – überspringbar, der Alarm läuft weiter. Die Antwort wird ein Blocker mit
    Herkunft *Nachfrage*, auf Wunsch gleich mit Vorbereitungsaufgabe. Eine Erledigung
    setzt den Zähler zurück.

**Technik:** Migration 5→6 mit neuer Tabelle `blocker`, `template` und `ADD COLUMN` für
Frist, Fenster, Intervall, Vorab-Reminder, `prepFor`, S7-Schalter; Backup v5 wie bisher.
Fristen-, Blocker- und Nachfrage-Logik in `core` (Kover), Vorab-Reminder als eigener
stiller Kanal wie die Übersicht.

### 12.5 V1.2b – Umsetzung und Präzisierungen
Kevin hat 12.3 am 2026-10-31 bestätigt. Präzisiert bei der Umsetzung:
- **Modell:** `Reminder.weeklyGoal = WeeklyGoal(target, max = target, minimum = 1)`, nur
  bei *täglich* oder *an Wochentagen*; `minimum ≤ target ≤ max ≤ Angebotstage`. Spalten
  `weekTarget`, `weekMax`, `weekMinimum`; Migration 4→5 nur mit `ADD COLUMN`, Backup v4.
- **Zählen:** *Ziel* und *Minimum* zählen, *ohne Nachweis* nicht. Maßgeblich ist der
  Erledigungszeitpunkt (`resolvedAt`), Woche Mo–So in der Zone des Geräts.
- **Ziel erreicht:** weitere Angebote kommen als stille Benachrichtigung ohne Runden; der
  Alarm steht auf der Tagesgrenze. Bleibt das Angebot offen, wird es am Tagesende
  *ausgelassen – Wochenziel erreicht* statt *verpasst*.
- **Höchstens erreicht:** das nächste Angebot kommt erst in der nächsten Woche.
- **Knapp:** `restliche Angebotstage (inkl. heute) ≤ Ziel − erledigt` und Ziel noch nicht
  erreicht → eine Stufe strenger (locker → normal → streng; streng bleibt streng).
- **Rettung:** solange das Minimum fehlt, Sa und So 10:00 *streng*, aber nur bei
  Wochentags-Plänen, die diesen Tag nicht ohnehin anbieten. Die Rettung ignoriert
  *entfällt* (Ankunft zuhause).
- **Serien:** *Tage* bei täglich/Wochentagen ohne Wochenziel (Joker pro Kalenderwoche),
  *Wochen* bei Wochenziel (eine Woche zählt mit erreichtem Minimum; eine Woche darunter
  mit einem Verpasser bricht, sonst neutral; die laufende Woche zählt erst, wenn erreicht;
  Joker pro Monat), *Mal* bei *nach Erledigung* (mehr als einen Tag zu spät = Verpasser;
  Joker pro Monat). Ausgelassen, entfallen und ohne Nachweis halten die Serie.
- **Statistik:** pro Reminder Ziel, Minimum, ohne Nachweis, vorab, ausgelassen, entfallen,
  verpasst für Woche, Monat, Jahr, gesamt; erreichbar beim Bearbeiten über *Statistik und
  Serie*. *Heute* zeigt einen Abschnitt *Diese Woche* (knappe Wochen hervorgehoben).
- **Übersicht:** eigener stiller Kanal; Standard 7:30 und 20:30, in den Einstellungen
  verschiebbar oder *aus*. Inexakter Alarm (`setWindow`, 10 min), nach jedem Auslösen,
  Neustart und App-Start neu gesetzt; Inhalt wird beim Auslösen aus der Datenbank gebaut.
- **Diagnose-Log** für Gerätetests (`Diag`): logcat-Tag `AvlDiag` und
  `files/diag/diag.log` (rotiert bei 512 KB nach `diag.1.log`). Enthält Zeiten, IDs,
  Titel, Zustände, Berechtigungen; keine Nachweis-Inhalte, keine Positionen. Bleibt im
  App-Speicher und wird mit der App gelöscht.
