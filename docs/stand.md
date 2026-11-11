# Stand und nächste Schritte

Stand: 2026-11-11. Bei jedem größeren Schritt aktualisieren. Erledigtes steht in git,
in den Entwürfen und den Review-Berichten (`.review/runs/`), nicht hier.

## Auf dem Gerät (S24)
- **V1.3b1** (Sitzung mit Timer und Uhr, 7.11) in `develop`, noch nicht installiert.
  Offen: installieren, Gerätetest K27–K30, danach eine Woche Training mit Timer vor V1.3c.
- **1.3b0.6-backup5** (`versionCode` 16), installiert am 2026-11-11 über 1.3b0.5: Ankunft
  im Zeitraum mit WLAN (7.29), weiterer Ort, Geofence-Neuregistrierung. Nach dem Start:
  Berechtigungen ok, Alarme neu geplant, WLAN-Meldung kommt an (Log ohne BSSID), keine
  Fehler. Offen: Gerätetest K15 und K23–K26 ([geraetetest.md](geraetetest.md)).
- **1.3b0.5-backup5** (`versionCode` 15), installiert am 2026-11-11 über 1.3b0.4
  (Backend-Pin `5845beb`). Gegenüber 1.3b0.4: Fixes aus Review-Lauf 10 (R1, R2, W-7,
  abgelehnte Sitzung, *Cloud-Daten löschen*, *Mit Wiederherstellungsschlüssel
  freischalten*), Phase C und D, Phase F (Eskalationsrunde ab letztem Klingeln, Wochenstand
  einmal definiert, Checklisten-Knopf im Vollbild-Alarm und auf der Uhr). Nach dem Start:
  Berechtigungen ok, Alarme neu geplant, keine Fehler im Diagnose-Log; Gerätetest C1–C4
  am selben Tag bestanden. Seit 2026-11-11 in `develop`.

## Cloud-Backup
Entwürfe: [backup-entwurf.md](backup-entwurf.md) (Schritte 1–5),
[wiederherstellen-entwurf.md](wiederherstellen-entwurf.md) (W-1…W-8, R-1…R-6);
Überblick [backend.md](backend.md). Seit 2026-11-11 in `develop`.

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
1. **1.3b0.6 installieren**, *Zuhause* in *Orte* neu festlegen (mit WLAN verbunden) und
   K23–K26 aus [geraetetest.md](geraetetest.md) im Alltag prüfen.
2. **Testsession vor V2** ([testsession.md](testsession.md)): Kevin führt sie durch, danach
   wertet Claude `diag.log`, `logcat` und `adb shell dumpsys alarm` aus.
3. **Nutzung beobachten und trimmen** (7.27): Reminder werden manchmal zu streng angelegt
   und mehrfach um 10 min geschoben; Cheats bekommen später Rückfragen. Zählen erst mit
   der Testsession (NT4).
4. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt (Kevin markiert sie);
   offen VS3 (7.25).
5. **V1.3:** Entwurf bestätigt (design.md *Entwurf V1.3*); 7.29 umgesetzt (V1.3b0.6),
   Sitzung mit Timer umgesetzt (V1.3b1); als Nächstes V1.3c (Abläufe) nach einer Woche
   Training mit dem Timer. Offen aus V1.3b: Vorlagen mit Sitzung, Chronometer auf der
   Uhr prüfen (design.md E.3 **[P]**).
6. **Später, stört aktuell nicht:** Kevin beschreibt die Szenarien zu Checklisten mit
   mehreren Uhrzeiten (vermuteter Fehler; Code und Tests am 2026-11-11 ohne Befund
   geprüft), Training-Alarm am Morgen und „Checkliste nicht richtig gespeichert“
   (möglicher Grund am 2026-11-11 behoben: Vollbild-Alarm und Uhr zeigten bei Checklisten
   „Erledigt“ statt „✓ Punkt“ und schlossen so die ganze Liste ohne Punkte ab);
   aus Review-Lauf 2 optional Paket `alarm` aufteilen.
7. **Versionsname** je installiertem Stand `1.3b<n>-backup<Schritt>` (ab 1.3b1; vorher
   `1.3b0.<n>`), `versionCode` +1.
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
