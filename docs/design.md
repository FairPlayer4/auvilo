# Design – avlreminder V1.0

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

- Wann endet eine Fälligkeit ohne Reaktion bei Remindern außerhalb des Eskalationsfensters
  (z. B. 02:00)? Vorschlag: am Ende des nächsten Eskalationsfensters.
- Mehrtagestest abwarten: Samsungs "Schlafende Apps" und Tief-Doze.
