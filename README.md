# AuViLo-Reminder

Android-App für Erinnerungen, die **nicht still verschwinden**: Eine Erinnerung gilt erst
als erfüllt, wenn die Aufgabe nachweislich erledigt oder bewusst verschoben wurde. Dazu
kombiniert die App Eskalation, Nachweise, Orts- und Zeit-Auslöser und Verstärkung, mit
Schwerpunkt auf **positiver Verstärkung** (Serien, Wochenziele, Statistik).

Der Name steht für **Au**dio, **Vi**suell, **Lo**kation (Arbeitsname und Paket weiterhin
`avlreminder`). Das optionale Cloud-Backup kommt aus dem Schwester-Repo
[FairPlayer4/auvilo-backend](https://github.com/FairPlayer4/auvilo-backend).

> **Stand:** privates Projekt, im täglichen Einsatz auf dem eigenen Gerät (Galaxy S24
> Ultra und Galaxy Watch). Installiert wird per Sideload, nicht über den Play Store. Was
> gerade installiert ist: [stand.md](docs/stand.md).

## Wofür

Herkömmliche Erinnerungen sind schwach: wegklicken und vergessen ist der Normalfall. Das
trifft vor allem **anstrengende, wiederkehrende Dinge** wie Training oder Haushalt und
Aufgaben, die nur in bestimmten Zeitfenstern gehen. Die App richtet sich an Menschen, die
sich bewusst für strenge Mechanismen entscheiden; was stört, stellt der Nutzer selbst ein.

Leitprinzipien (vollständig in [anforderungen.md](docs/anforderungen.md), Kap. 3):

- **Kein Reminder verschwindet still.** Jeder endet bewusst: erledigt (Ziel oder Minimum),
  verschoben, ausgelassen, entfallen – oder er wird als verpasst markiert.
- **Lieber klein als gar nicht.** An schweren Tagen zählt das Minimalziel.
- **Nachweis statt Klick:** Foto, Ort, Audio oder Text, wenn der Reminder es verlangt.
- **Erinnern, wenn es möglich ist:** Zeitfenster und Orte statt fester Uhrzeiten.
- **Der Rhythmus zählt, nicht der einzelne Tag:** Wochenziele wie „3–4× pro Woche“.
- **Nicht ans Handy gebunden:** laufende Reminder lassen sich auch an der Uhr bedienen.

## Funktionen

- **Auslöser:** Uhrzeiten (auch mehrmals täglich, Wochenend-Zeiten), Fristen mit
  Zeitfenstern, Ankunft zuhause oder an einem Ort im Zeitraum (Geofence und WLAN),
  Vorab-Reminder und Übersicht morgens und abends.
- **Eskalation:** je nach Strenge Banner oder Vollbild mit Vibration, streng auch mit
  Alarmton; *Nicht stören* und Tagesgrenze, Später-Knöpfe mit Verschiebe-Limit pro Termin, Woche oder Monat,
  Notausschalter.
- **Erledigung nachweisen:** quittieren, Foto (auch mit den Lautstärketasten), Audio, Text;
  Ziel und Minimum; Erledigung zurücknehmen; vorab erledigen, verschieben oder auslassen.
- **Sitzung mit Timer** („Ich starte jetzt“), Checklisten, Startklar-Fragen und Blocker,
  Nachfragen, wenn es hakt.
- **Verstärkung:** Wochenziele, Serien mit Joker, Statistik, Motivations-Audio.
- **Watch-App** (Wear OS) für die Sitzung: Timer, Antworten, Kachel, laufende Aktivität.
- **Schnellerinnerung per Sprache** über ein eigenes Widget, erkannt möglichst auf dem
  Gerät.
- **Daten sichern:** Sicherungsdatei mit Passwort, Tageskopien auf dem Gerät und optional
  das **Cloud-Backup**, Ende-zu-Ende-verschlüsselt (nur auf Einladung).
- **Diagnose-Log** auf dem Gerät und eine Schnittstelle für Claude über adb (nur im
  Debug-Build).

Welche Version was gebracht hat: [anforderungen.md](docs/anforderungen.md), Kap. 10.1.

## Daten und Datenschutz

- **Lokal-first:** Alle Daten, auch Fotos und Audio, liegen auf dem Gerät. Ohne Backend
  funktioniert die App vollständig.
- Sie verlassen das Gerät nur **Ende-zu-Ende-verschlüsselt** im Cloud-Backup und nur, wenn
  es eingeschaltet ist. Der Server in der EU sieht keine Inhalte. Fotos und Audio gehen
  nicht in die Cloud.
- Der Wiederherstellungsschlüssel für das Cloud-Backup gehört in einen Passwortmanager
  oder auf Papier. Ohne ihn und ohne Gerät sind die Daten weg; das ist gewollt.

Mehr dazu: [cloud-entscheidungen.md](docs/cloud-entscheidungen.md),
[backend.md](docs/backend.md) und die DSGVO-Dokumente im
[Backend-Repo](https://github.com/FairPlayer4/auvilo-backend/tree/develop/docs/datenschutz).

## Aufbau

```
core/   reines Kotlin ohne Android: Modell, Regeln (Takt, Eskalation, Fristen, Ziele,
        Sitzungen, Orte) und die ReminderEngine als einziger Weg, etwas zu ändern
app/    Android: Compose-Oberfläche, Alarme und Dienste, Orte, Room-Datenbank, Widgets,
        Sprache, Cloud-Backup, Anbindung der Uhr
wear/   Watch-App (Wear OS): Fernbedienung und Anzeige, entscheidet nichts selbst
tools/  Mutations-Suite (mutate.py), Export pseudonymisierter Testdaten
docs/   Anforderungen, Design, Stand, Gerätetests, Play Store
```

- Alle Regeln liegen in `:core` und sind mit Unit-Tests abgesichert (Kover ≥ 90 %, der
  Build schlägt sonst fehl). Jeder Befehl der Engine läuft in einer Transaktion; Alarme
  planen und klingeln erst danach.
- Technik: Kotlin, Jetpack Compose, Room, DataStore, Glance, CameraX,
  Play-Services-Location; targetSdk 37, minSdk 34. Details: [design.md](docs/design.md),
  Kap. 1.
- Das Cloud-Backup nutzt die Module `protokoll` und `verbindung` aus dem Backend als
  Quellcode (Composite Build). Welcher Backend-Commit gilt, steht in
  [`gradle/backend-commit.txt`](gradle/backend-commit.txt).

## Dokumentation

| Dokument | Inhalt |
|---|---|
| [stand.md](docs/stand.md) | was auf dem Gerät installiert ist, offene Punkte – **zuerst lesen** |
| [anforderungen.md](docs/anforderungen.md) | Vision, Mechanismen (Kap. 7), Entscheidungen (Kap. 9), Versionen (Kap. 10.1), offene Fragen |
| [design.md](docs/design.md) | aktueller Aufbau nach Themen: Architektur, Datenmodell, Alarm, Orte, Sitzung, Cloud-Backup, Uhr |
| [geraetetest.md](docs/geraetetest.md) | was nach jedem Update auf dem echten Gerät geprüft wird |
| [cloud-entscheidungen.md](docs/cloud-entscheidungen.md) | Entscheidungen zum Cloud-Backup in der App |
| [backend.md](docs/backend.md) | das Backend aus Sicht der App |
| [playstore.md](docs/playstore.md), [playstore-erklaerungen.md](docs/playstore-erklaerungen.md) | was für den Play Store fehlt, Entwürfe für die Play Console |
| [rueckmeldung-entwurf.md](docs/rueckmeldung-entwurf.md) | Entwurf: Diagnose und Vorschläge aus der App senden |
| [spike.md](docs/spike.md) | Machbarkeits-Spike auf dem S24 Ultra |
| [entwicklungsumgebung.md](docs/entwicklungsumgebung.md) | Werkzeuge und Pfade |
| [anforderungen-archiv.md](docs/anforderungen-archiv.md), [geraetetest-archiv.md](docs/geraetetest-archiv.md) | ältere Entscheidungen und Testlisten |

## Bauen

Voraussetzungen: JDK 21, Android SDK und Git Bash (Details in
[entwicklungsumgebung.md](docs/entwicklungsumgebung.md)). Das Backend-Repo muss
**daneben** im Ordner `avlreminder-backend` liegen:

```bash
git clone https://github.com/FairPlayer4/auvilo.git avlreminder
git clone https://github.com/FairPlayer4/auvilo-backend.git avlreminder-backend
git -C avlreminder-backend checkout "$(cat avlreminder/gradle/backend-commit.txt)"
cd avlreminder
./gradlew -q :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug
```

- Steht das Backend auf einem anderen Commit als in `gradle/backend-commit.txt`, warnt
  der Build; mit `-PbackendStreng=true` bricht er ab (so baut die CI).
- Ohne die Server-Werte `avlCloudServerUrl` und `avlCloudWebClientId` (in
  `gradle.properties` im Gradle-Benutzerverzeichnis oder in `local.properties`) blendet
  die App das Cloud-Backup aus; alles andere funktioniert.
- Android Lint bricht bei jedem neuen Fund ab, auch bei Warnungen.
- Die Mutations-Suite prüft, ob die Tests Fehler finden:
  `python -I -X utf8 tools/mutate.py` (etwa 20 Minuten).
- Gearbeitet wird auf `develop`; nach `main` nur per Pull Request. Die CI
  ([build.yml](.github/workflows/build.yml)) läuft auf beiden und bei Pull Requests
  und ist die maßgebliche Prüfung; gemergt wird nur mit grüner CI.

## Lizenz

[Apache License 2.0](LICENSE), Copyright 2026 Kevin Bartik.
