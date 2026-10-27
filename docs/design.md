# Design – avlreminder V1.0 und V1.1

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

**Alarm aus während des Nachweises (N0):** neues Feld `Occurrence.proofStartedAt`.
- `Action.StartProof` setzt es; die App stoppt Ton und Vibration; der nächste Alarm
  liegt bei `proofStartedAt + windowMinutes` (höchstens Tagesgrenze).
- `Action.AbortProof` (Zurück, App verlassen) → nächster Alarm in 1 min.
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
  │  │        │ └ Nachweis läuft (proofStartedAt, Frist) ┘
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
