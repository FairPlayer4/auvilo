# Stand und nächste Schritte

Stand: 2026-11-08. Bei jedem größeren Schritt aktualisieren.

## In Arbeit
- **V1.3a3** (`versionName` 1.3a3-prototyp, `versionCode` 9) in `develop`, gebaut,
  **noch nicht installiert**: 6 Fixes aus Review-Lauf 1
  ([.review/runs/2026-11-03.md](../.review/runs/2026-11-03.md)) und Kevins Antworten auf
  die Fragen 1–6 und 7a/7c, dazu Checklisten über mehrere Uhrzeiten (ein Punkt pro
  Termin, 7.25 VS1). Keine Migration. Kevin installiert am Abend des 2026-11-03. Gerätetests:
  geraetetest.md *Neu in V1.3a3*. Offen: Die Eskalationsrunde wird bei jedem `sync`
  (App öffnen) neu ab „jetzt“ geplant. Frage 7b ist umgesetzt (Notaus von Hand aus klingelt
  sofort). Build grün: 322 Tests, Kover in `core` 98,88 %.
- **V1.3b0** (`versionName` 1.3b0-prototyp, `versionCode` 10) auf `feature/v1.3b0`, installiert am 2026-11-03:
  Umbau aus Review-Lauf 2
  ([.review/runs/2026-11-03-2.md](../.review/runs/2026-11-03-2.md), E1–E4) – Engine als
  Fassade (`Scheduler`, `AlarmViews`, `Changes`), jeder Befehl in einer Transaktion,
  zentrales Klingeln über `RingPort`, `AlarmView` in `core`. Einzige Verhaltensänderung: Ein
  gespeicherter Ankunft-Reminder klingelt sofort, wenn man schon da ist, auch über die
  Schnittstelle. Keine Migration. Build grün: 328 Tests, Kover in `core` 98,44 %.
  Für B2b vorbereitet: Einstellungen als JSON sichern und
  wiederherstellen (`Settings.backup`/`restore`, ohne *Notaus* und Schnittstelle).
  Noch offen aus dem Review (optional): Paket `alarm` aufteilen, Wochenstand einmal
  definieren, *Heute* an einer Stelle laden.
- **V1.3b0.1** (`versionCode` 11) auf `feature/v1.3b0`, **installiert am 2026-11-04 21:38**:
  Kevins Rückmeldungen vom 2026-11-04 – Checklisten-Punkte jederzeit abhaken (C8), lesbare
  Punkte im Alarm, Übersicht nennt jede Uhrzeit, mitwachsende Textfelder mit
  Tastatur-Vorschlägen (`GrowingField`, `TextKeyboard` in `ui/Inputs.kt`). 333 Tests grün.
  Offen: Training-Alarm am Morgen und „Checkliste nicht richtig gespeichert“ – Kevin
  erklärt das Szenario, dann Logs (`diag.log`) und Daten über die Schnittstelle lesen.
  Dazu bessere Tests (design.md 14): Szenario-Tests in `core` (`scenarios/`, 14 Tage
  nachgespielt, zwei davon halten die offenen Punkte fest) und `ReceiverGlueTest` in `:app`
  (8 Tests: Alarm, Knöpfe, Ankunft, Neustart). Das Diagnose-Log nennt bei einer Ankunft
  jetzt Titel und Fälligkeit der klingelnden Reminder. 355 Tests grün, Kover 98,45 %.
  Echte Testdaten seit 2026-11-04 abends: `testdata/real-2026-11-04.json` (19 Reminder,
  87 Termine, pseudonymisiert) und `RealDataTest` (Migration, Alarm-Ansicht, eine Woche,
  Handy zwei Tage aus). 359 Tests grün, Kover 98,33 %.
- **Training-Fälle geklärt (2026-11-04, aus `diag.log`):** Morgens meldete der Geofence um
  07:12 eine Ankunft, und ohne früheste Uhrzeit klingelte das Training sofort. Kevin
  verschob es auf 20:30. Um 20:30 lieferte die grobe Ortsabfrage auf dem Heimweg
  vermutlich „unklar“, also klingelte es; die Ankunft kam erst um 20:34. Daraus die
  Anforderungen 7.29 (Ankunftszeitraum, WLAN, GPS im Zeitraum, Puffer); F1–F3 wie
  vorgeschlagen entschieden. Kevin liest zuerst alle Anforderungen im Ganzen; bis dahin
  nur Anforderungsarbeit und Tests. Bis zur Umsetzung kann eine Ankunft am Morgen das
  Training weiter sofort auslösen.
- **V1.3b0.2** (`versionName` 1.3b0.2-prototyp, `versionCode` 12) auf `feature/v1.3b0`,
  **installiert am 2026-11-05 20:10** (Migration 7→8 geprüft: Backup v7, Schema 8, 80
  Termine mit `firedAt`, `foreign_key_check` leer): Funde und Empfehlungen aus Review-Lauf 3
  ([.review/runs/2026-11-05.md](../.review/runs/2026-11-05.md)).
  - E5: Ein Termin merkt sich, ob er geklungen hat (`firedAt`) und ob er nach dem
    Verschieben auf die Ankunft wartet (`awaitsArrival`). **Migration 7→8** (`ADD COLUMN`,
    `firedAt` aus dem ersten `DUE`-Ereignis), Backup v7 vor der Migration.
  - B1: Speichern eines geschlummerten oder verschobenen Reminders verschiebt den
    heutigen Termin nicht mehr auf morgen.
  - B2: Verschobenes Training, zur neuen Zeit nicht zuhause (oder *unklar*) → wartet auf
    die Ankunft, sonst zur Tagesgrenze *verpasst* statt *entfällt* (7.29 OZ5). Das löst
    auch den Fehlalarm auf dem Heimweg vom 2026-11-04 um 20:30.
  - E6: Ortsprüfung außerhalb der Transaktion, Alarme erst nach dem Speichern.
  - Kevins Antworten auf die Fragen 1–7 und die Empfehlungen E7–E10 stehen in
    anforderungen.md (Kap. 9, 2026-11-05) und design.md (Entwurf 7.29, E.3).
  - 368 Tests grün, Kover in `core` 98,2 %.
- **7.29 vereinfacht (2026-11-05):** Kevin hat die Anforderungen im Ganzen gelesen. 7.29
  bestimmt den Ort zuerst nur über WLAN (Verbindung und Suchergebnisse, BSSIDs pro Ort);
  GPS ist nach 7.30 ausgelagert und kommt später. Nächster Schritt: Entwurf 7.29 Z.7
  bestätigen, kleiner Gerätetest zum WLAN im Hintergrund, dann Umsetzung.
- **V1.3b0.3** (`versionCode` 13, **installiert am 2026-11-05 22:43**, Sonde läuft, Heimnetz
  als N1 mit 4 Zugangspunkten gelernt, BSSID über den Callback): WLAN-Sonde im Debug-Build (`location/WifiProbe.kt`),
  nur Diagnose, kein neues Verhalten; Fragen W1–W5 in geraetetest.md. Neue Berechtigungen
  `ACCESS_WIFI_STATE` und `ACCESS_NETWORK_STATE` (normal, ohne Rückfrage). 375 Tests grün.
  Nach ein paar Tagen wertet Claude `diag.log` aus, danach 7.29 umsetzen und die Sonde
  durch die echte Prüfung ersetzen.
- **V1.3b0.4** (`versionCode` 14) auf `feature/v1.3b0`, **installiert am 2026-11-07 13:05** (Start ok, Berechtigungen ok, alle Alarme neu geplant):
  Erledigung zurücknehmen (Anf. 7.31, design.md 3): stille Benachrichtigung *Rückgängig*
  für 1 min nach *Erledigt*/*Auslassen*, danach *Zurücknehmen* auf *Heute*. Wieder offen,
  klingelt in 10 min; Nachweis zählt wieder; Checkliste verliert nur den letzten Haken.
  Keine Migration, neues Ereignis `REOPENED`. Gerätetests N20–N23.
  Dazu vorab verschieben und auslassen (Anf. 7.32): *Vorab* auf *Heute* und *Verschieben …*
  im Vorab-Reminder öffnen den Alarm-Bildschirm vorab, „heute später“ +30 min/+1/+2 Std ab
  der geplanten Zeit, zählt aufs Verschieben-Konto; Ankunft-Reminder behält die neue Zeit.
  Gerätetests N24–N27.
- Danach: Kevins Wochenplan (Gerätetests V1.3a, Reminder *Gerätetest*), dann V1.3b.

## Auf dem Gerät
- **V1.3a2** (`versionName` 1.3a2-prototyp, `versionCode` 8), installiert am 2026-11-02,
  seit 2026-11-03 in `develop`: V1.3a (mehrmals täglich VS1, Wochenend-Zeiten VS2,
  Hinweis bei ständigem *Später* NT2, Notausschalter PS7; Migration 6→7 mit Backup v6
  geprüft) und die Schnittstelle für Claude über adb (7.26 AG11, design.md 15), von
  Kevin eingeschaltet. Gerätetests N7–N9 bestanden; offen: geraetetest.md *Neu in V1.3a*.
- Davor **V1.2c** (`versionName` 1.2c-prototyp, `versionCode` 6), in `develop` gemergt. Enthält zusätzlich zu V1.2b Fristen mit Ausführungsfenstern und
  *Überfällig*, Vorlagen, Vorab-Reminder, Blocker als Liste mit Vorbereitungsaufgaben,
  *Blocker gelöst* als Minimum und Nachfragen, wenn es hakt. Migration 5→6 mit Backup v5
  geprüft, alle Alarme neu geplant.
- Build grün: 260 Tests, Kover-Abdeckung in `core` 99,41 %.
- Fehler aus dem Alltag werden als Fix auf `develop` behoben.

## Aktueller Schwerpunkt (seit 2026-11-02)
1. **Nutzung beobachten und trimmen** (anforderungen.md 7.27): Kevin sammelt im Alltag,
   welche Funktionen er wirklich nutzt. Auffällig bisher: Reminder werden manchmal zu
   streng angelegt und dann mehrfach um 10 min geschoben. Cheats bleiben vorerst, sollen
   aber sinnvolle Rückfragen („Willst du wirklich …?“) bekommen.
   Hinweis bei wiederholtem *Später* (NT2) ist in V1.3a umgesetzt. Gezählt wird erst vor
   V2 mit der Testsession (NT4).
2. **Backend** (`../avlreminder-backend`): B0–B2b fertig und in der Cloud getestet.
   Als Nächstes das **Cloud-Backup in der App**, Entwurf bestätigt am 2026-11-07
   ([backup-entwurf.md](backup-entwurf.md), fünf Schritte). Vorab legt Kevin die
   OAuth-Clients (Web und Android) und den Deploy-Key an. Überblick: [backend.md](backend.md).
   App und `core` bauen seit 2026-11-07 für Java 21 (Voraussetzung für `client/`).
   **Schritt 1 umgesetzt** am 2026-11-07 auf `feature/backup` (nichts sichtbar, noch nicht
   installiert): Composite Build mit festem Backend-Commit, `INTERNET`, CI mit zweitem
   Checkout; CI mit Deploy-Key grün seit 2026-11-07.
   **Schritt 2 installiert** am 2026-11-08 17:32 (`versionCode` 14, Backend-Pin `7b994a4`;
   Start ok, 162 Alarme vorher und nachher; Gerätetest durch Kevin offen): Bereich
   *Cloud-Backup (experimentell)* in den Einstellungen bis „eingerichtet“ (Paket `cloud`:
   Geräteschlüssel und Schlüsseldatei im Keystore, DataStore `backend`, Credential Manager).
   Vorab am 2026-11-08 erledigt: Web-Client-ID im Backend angewendet, Kevins
   Backend-Konto von den Test-Schlüsseln befreit (Sicherung, Schlüssel-Backup, Geräte).
3. **Play-Store-Release** ohne Eile, erst wenn die App „fertig“ ist (mindestens bis V2);
   dafür Transparenz, Notausschalter (in V1.3a), Backend nur nach Einschalten (7.28 PS5–PS9).

## Offen
1. **Testsession vor V2** (Plan: [testsession.md](testsession.md)): Kevin führt sie durch,
   danach wertet Claude `diag.log`, `logcat` und `adb shell dumpsys alarm` aus und gleicht
   das Ergebnis mit den Vorschlägen in der App ab.
2. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt; Kevin markiert sie dort
   als „umgesetzt“. VS1 und VS2 kommen mit V1.3a, offen bleibt VS3 (anforderungen.md 7.25).
3. **CI:** PRs nach `main` laufen über GitHub Actions (`upload-artifact@v6`, Node 24,
   seit 2026-11-02). Branch-Regel für `main` setzt Kevin.
4. **V1.3**: Entwurf bestätigt (design.md *Entwurf V1.3*, E.5). V1.3a ist gebaut (siehe
   oben); danach V1.3b (Sitzung mit Timer, Uhr) und V1.3c (Abläufe).

## Doku
- [design.md](design.md) beschreibt seit 2026-11-02 nur den aktuellen Stand, nach
  Themen. Neue Entwürfe kommen als Abschnitt *Entwurf* ans Ende.
- [geraetetest.md](geraetetest.md) enthält die Kerntests nach jedem Update; die Listen
  pro Version stehen in [geraetetest-archiv.md](geraetetest-archiv.md).

## Beobachtungen aus der echten Nutzung
- „Test-Reminder“ klingelte am 2026-10-31 in Runden um 11:30 und 12:00. Spätestens in der
  Testsession prüfen, ob das zur eingestellten Eskalation passt.
- Morgen-Übersicht steht auf 8:00; vermutlich von Kevin so eingestellt.
- Diagnose-Log zuletzt geprüft (V1.2c-Installation): Berechtigungen ok.
