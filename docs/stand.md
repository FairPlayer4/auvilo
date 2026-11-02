# Stand und nächste Schritte

Stand: 2026-11-02. Bei jedem größeren Schritt aktualisieren.

## Auf dem Gerät
- **V1.2b** (`versionName` 1.2b-prototyp, `versionCode` 5), Branch `develop`,
  installiert am 2026-10-31. Enthält Wochenziele, Serien, Statistik, Übersicht morgens
  und abends, Diagnose-Log und den UI-Feinschliff aus Kevins ersten Vorschlägen
  (klappbare Bereiche beim Bearbeiten, eigene Werte mit *andere…*, freie Uhrzeiten
  in den Einstellungen, Zahnrad auf allen Tabs).
- Build grün: 224 Tests, Kover-Abdeckung in `core` 99,37 %.
- Diagnose-Log zuletzt geprüft: Berechtigungen ok, `dndBlocked=none`.

## Offen
1. **Testsession vor V2** (Plan: [testsession.md](testsession.md); am 2026-11-02 von
   „vor V1.3“ verschoben, die App läuft im Alltag gut): Kevin führt sie durch, danach wertet Claude `diag.log`, `logcat` und `adb shell dumpsys alarm`
   aus und gleicht das Ergebnis mit den Vorschlägen in der App ab.
2. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt; Kevin markiert sie dort
   als „umgesetzt“. Offen sind VS1–VS3 (anforderungen.md 7.25).
3. **CI:** PRs nach `main` laufen über GitHub Actions. Offen: `upload-artifact` auf eine
   Version mit Node 24 heben; Branch-Regel für `main` setzt Kevin.
4. **Doku straffen**, am besten vor dem V1.3-Design:
   - design.md nach Themen statt nach Versionen gliedern und nur den aktuellen Stand
     beschreiben (überholte Entwürfe streichen, Historie steht in Git).
   - anforderungen.md nur leicht: Übersicht „was ist in welcher Version umgesetzt“,
     Kap. 10 aktualisieren, Doppeltes zusammenführen. Inhaltlich nichts ohne Kevin streichen.
   - geraetetest.md in Kerntests (nach jedem Update) und versionsspezifisches Archiv teilen.
5. **V1.2c** (Fristen, Vorab-Reminder, Blocker als Aufgaben, Vorlagen): vollständiger
   Entwurf in design.md 12.4, wartet auf Kevins Prüfung. Danach **V1.3**
   (Timer, Abläufe, Watch, mehrmals täglich, Wochenend-Zeiten) laut anforderungen.md Kap. 10.

## Beobachtungen aus der echten Nutzung
- „Test-Reminder“ klingelte am 2026-10-31 in Runden um 11:30 und 12:00. Spätestens in der
  Testsession prüfen, ob das zur eingestellten Eskalation passt.
- Morgen-Übersicht steht auf 8:00; vermutlich von Kevin so eingestellt.
