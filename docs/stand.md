# Stand und nächste Schritte

Stand: 2026-11-03. Bei jedem größeren Schritt aktualisieren.

## In Arbeit
- **V1.3a3** (`versionName` 1.3a3-prototyp, `versionCode` 9) in `develop`, gebaut,
  **noch nicht installiert**: 6 Fixes aus Review-Lauf 1
  ([.review/runs/2026-11-03.md](../.review/runs/2026-11-03.md)) und Kevins Antworten auf
  die Fragen 1–6 und 7a/7c, dazu Checklisten über mehrere Uhrzeiten (ein Punkt pro
  Termin, 7.25 VS1). Keine Migration. Kevin installiert am Abend des 2026-11-03. Gerätetests:
  geraetetest.md *Neu in V1.3a3*. Offen: Die Eskalationsrunde wird bei jedem `sync`
  (App öffnen) neu ab „jetzt“ geplant; Frage 7b (Notaus von Hand aus) an Kevin. Build
  grün: 321 Tests, Kover in `core` 98,87 %.
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
2. **Backend** (Kevin, parallel, `../avlreminder-backend`): B0–B2 fertig, B2b
   (Daten-Backup) als Nächstes. Überblick: [backend.md](backend.md).
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
