# Stand und nächste Schritte

Stand: 2026-11-10. Bei jedem größeren Schritt aktualisieren. Erledigtes steht in git,
in den Entwürfen und den Review-Berichten (`.review/runs/`), nicht hier.

## Auf dem Gerät (S24)
- **1.3b0.4** (`versionCode` 14) mit dem Cloud-Backup bis Schritt 5 (Backend-Pin
  `6d04e02`, Zeitzonen-Fix `5b8ee18`, Diagnose bei abgebrochenem Wiederherstellen
  `6972903`), installiert am 2026-11-09; Gerätetest S24 am selben Tag bestanden.
  Enthält V1.3a (VS1, VS2, NT2, Notaus PS7, Schnittstelle für Claude 7.26 AG11) und
  V1.3b0.x (Engine als Fassade, Checklisten C8, WLAN-Sonde, *Zurücknehmen*, Vorab-Alarm).
- **Gebaut, noch nicht installiert:** `1.3b0.5-backup5` (`versionCode` 15) auf
  `feature/backup`, Backend-Pin in `gradle/backend-commit.txt`: Fixes aus Review-Lauf 10
  (R1 Einstellungen vor dem Tausch prüfen, R2 Room löscht keine beschädigte DB, W-7,
  abgelehnte Sitzung meldet sich sofort, *Cloud-Daten löschen* sperrt erst alle anderen
  Geräte, *Mit Wiederherstellungsschlüssel freischalten*), Phase C (`CloudBackup.kt`
  geteilt, abgelehnte Sitzung wird einmal erneuert), Phase D (Token vor jedem Aufruf).

## Cloud-Backup
Entwürfe: [backup-entwurf.md](backup-entwurf.md) (Schritte 1–5),
[wiederherstellen-entwurf.md](wiederherstellen-entwurf.md) (W-1…W-8, R-1…R-6);
Überblick [backend.md](backend.md). Branch `feature/backup`.

| Schritt | Inhalt | Stand |
|---|---|---|
| 1 | Composite Build mit festem Backend-Commit, `INTERNET`, CI mit Deploy-Key | 2026-11-07 |
| 2 | Anmelden und Einrichten (Wiederherstellungsschlüssel, Passwortdienst) | installiert 2026-11-08 |
| 3 | Von Hand sichern, Backend-Logger, *Diagnose senden* | installiert 2026-11-08 |
| 4 | Stündlich sichern, Bremse (R-2), 3-Tage-Hinweis (A-13) | installiert 2026-11-08 |
| 5 | Prüfen und Wiederherstellen, Tageskopie, Fassungen (R-4) | installiert 2026-11-09 |

**Eigenbetrieb** seit 2026-11-07: Die App sichert Kevins echte Daten in die Cloud; vor dem
ersten weiteren Nutzer kommt die Sicherheitsphase (Backend `CLAUDE.md`, b2b-entwurf.md).

## Offen
1. **1.3b0.5 installieren** und die Cloud-Fälle aus [geraetetest.md](geraetetest.md) prüfen.
2. **Testsession vor V2** ([testsession.md](testsession.md)): Kevin führt sie durch, danach
   wertet Claude `diag.log`, `logcat` und `adb shell dumpsys alarm` aus.
3. **Nutzung beobachten und trimmen** (7.27): Reminder werden manchmal zu streng angelegt
   und mehrfach um 10 min geschoben; Cheats bekommen später Rückfragen. Zählen erst mit
   der Testsession (NT4).
4. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt (Kevin markiert sie);
   offen VS3 (7.25).
5. **V1.3:** Entwurf bestätigt (design.md *Entwurf V1.3*); als Nächstes Entwurf 7.29 Z.7
   (WLAN) bestätigen, dann V1.3b (Sitzung mit Timer, Uhr) und V1.3c (Abläufe).
6. **Später, stört aktuell nicht:** Eskalationsrunde wird bei jedem `sync` neu ab „jetzt“
   geplant; Checklisten mit mehreren Uhrzeiten (Kevin vermutet einen Fehler); Training-Alarm
   am Morgen und „Checkliste nicht richtig gespeichert“ (Kevin beschreibt das Szenario);
   WLAN-Sonde auslesen; aus Review-Lauf 2 optional Paket `alarm` aufteilen, Wochenstand
   einmal definieren, *Heute* an einer Stelle laden.
7. **Versionsname** je installiertem Stand `1.3b0.<n>-backup<Schritt>`, `versionCode` +1.
8. **Play-Store-Release** ohne Eile, frühestens mit V2 (7.28 PS5–PS9). Branch-Regel für
   `main` setzt Kevin.

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
