# Stand und nächste Schritte

Stand: 2026-11-02. Bei jedem größeren Schritt aktualisieren.

## Auf dem Gerät
- **V1.2c** (`versionName` 1.2c-prototyp, `versionCode` 6), installiert am 2026-11-02,
  in `develop` gemergt. Enthält zusätzlich zu V1.2b Fristen mit Ausführungsfenstern und
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
2. **Backend** (Kevin, parallel): lehrreich, später Backup und Sync mit anderen (7.28).
3. **Play-Store-Release** für eine sehr kleine Zielgruppe, Android 17 bleibt Minimum,
   voller EU-Datenschutz (7.28).

## Offen
1. **Testsession vor V2** (Plan: [testsession.md](testsession.md)): Kevin führt sie durch,
   danach wertet Claude `diag.log`, `logcat` und `adb shell dumpsys alarm` aus und gleicht
   das Ergebnis mit den Vorschlägen in der App ab.
2. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt; Kevin markiert sie dort
   als „umgesetzt“. Offen sind VS1–VS3 (anforderungen.md 7.25).
3. **CI:** PRs nach `main` laufen über GitHub Actions. Offen: `upload-artifact` auf eine
   Version mit Node 24 heben; Branch-Regel für `main` setzt Kevin.
4. **V1.3** (Timer, Abläufe, Watch, mehrmals täglich, Wochenend-Zeiten) laut
   anforderungen.md Kap. 10, nach der Beobachtungsphase.

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
