# avlreminder – Hinweise für Claude

Android-App für sehr wirksame Reminder, zuerst nur für Kevin selbst (V1: ein Nutzer,
lokal, Sideload auf sein privates S24 Ultra, Android 17 / API 37). Das Backend kommt
als Ende-zu-Ende-verschlüsseltes Cloud-Backup dazu, nur nach Einschalten.
Strenge und invasive Mechanismen sind gewollt. Kommunikation auf **Deutsch, mit ß**.

## Wo was steht
- [docs/stand.md](docs/stand.md) – aktueller Stand, offene Punkte, nächste Schritte (zuerst lesen)
- [docs/anforderungen.md](docs/anforderungen.md) – Kevins Anforderungen, Entscheidungen (Kap. 9, bis 2026-10-31 in `anforderungen-archiv.md`), Planung (Kap. 10)
- [docs/design.md](docs/design.md) – technisches Design nach Themen; am Ende höchstens der Entwurf der nächsten Etappe
- [docs/geraetetest.md](docs/geraetetest.md) – Kerntests auf dem echten Gerät (Archiv pro Version: `geraetetest-archiv.md`)
- [docs/backend.md](docs/backend.md) – Backend (`../avlreminder-backend`) aus Sicht der App
- [docs/cloud-entscheidungen.md](docs/cloud-entscheidungen.md) – Entscheidungen zum Cloud-Backup (A-, W-, R-) und Einrichtung
- [docs/entwicklungsumgebung.md](docs/entwicklungsumgebung.md) – Toolchain und Pfade auf Kevins Rechner
- Module: `core` (reine Kotlin-Logik, Kover-Abdeckung), `app` (Android, Compose, Room)

## Zusammenarbeit
- Kevin entwickelt die Anforderungen iterativ mit Claude: Feedback und Rückfragen geben,
  Empfehlung statt Optionsliste.
- Die vollständige Antwort immer als Text schreiben; die Zusammenfassung in der
  Abschluss-Box ersetzt sie nie.
- Neue Anforderungen und Entscheidungen in `docs/anforderungen.md` festhalten
  (Abschnitt 7.x und Tabelle in Kap. 9 mit Datum).
- Eine Etappe ist erst fertig, wenn ihr Entwurf in die Themen von `design.md` eingearbeitet
  und der Entwurfsabschnitt gelöscht ist; `stand.md` und Kap. 10.1 nachziehen.

## Bauen und testen (Git Bash)
```bash
./gradlew -q :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```
- Vor einem PR nach `main` zusätzlich `-PbackendStreng=true` (wie die CI): bricht ab,
  wenn der Backend-Stand nicht zu `gradle/backend-commit.txt` passt.
- `JAVA_HOME` und `ANDROID_HOME` sind auf dem Rechner gesetzt, nichts exportieren
  (Pfade in entwicklungsumgebung.md).
- Gradle, adb und `git push` brauchen in Claude Code Netz/Geräte außerhalb der Sandbox.
- Der Build braucht das Backend-Repo daneben (`../avlreminder-backend`, Composite Build,
  [cloud-entscheidungen.md](docs/cloud-entscheidungen.md) A-2). Steht dort ein anderer Commit als in
  `gradle/backend-commit.txt` oder gibt es uncommittete Änderungen in `client/`/`proto/`,
  warnt der Build: **eine solche APK nicht installieren**. Dasselbe gilt für die Warnung
  „Cloud-Backup ausgeblendet“ (Server-Werte fehlen, cloud-entscheidungen.md, Einrichtung). Nach Änderungen im Backend den
  Commit dort pushen und in `gradle/backend-commit.txt` eintragen.
- Android Lint (`:app:lintDebug`) bricht bei jedem neuen Fund ab, auch bei Warnungen. Funde
  beheben statt in die Baseline (`app/lint-baseline.xml`) zu schieben; nur wenn ein Fund
  bewusst so bleibt, mit Begründung `@SuppressLint` setzen.
- Vor jeder Installation: alle Tests grün. UI-Tests laufen mit Robolectric/Compose
  (`app/src/test`), Migrationen haben einen eigenen `MigrationTest`.

## Gerät (Kevins privates Handy)
- **Installieren erst nach Kevins ausdrücklichem Go.** Vorher bauen, zusammenfassen,
  was sich ändert, und die Risiken nennen.
- adb liegt im `PATH` (`%USERPROFILE%\Android\Sdk\platform-tools`), Seriennummer siehe `adb devices`.
- Ohne Rückfrage erlaubt: lesende Geräteinfos, App installieren/deinstallieren, Logs
  lesen, App-Berechtigungen setzen. Alles andere vorher fragen, auch `logcat -c`.
- Immer `adb install -r`, **nie vorher deinstallieren** (sonst sind Kevins Daten weg).
- Starten mit `adb shell am start -n com.avlreminder/.ui.MainActivity`, **nie mit `adb shell monkey`** (schaltet die Auto-Rotation ein, 2026-11-08).
- Bei Gerätetests genau sagen, wann Kevin sperren, entsperren oder etwas tun soll.
  Simulationen danach zurücksetzen.
- Diagnose: `adb exec-out run-as com.avlreminder cat files/diag/diag.log` und
  `adb logcat -d -s AvlDiag`. Die Datenbank (`databases/avlreminder.db` plus `-wal`, `-shm`)
  nur in ein temporäres Verzeichnis kopieren, lesen und wieder löschen.
- Inhalte von Kevins Remindern und Nachweisen sind privat: nicht ins Repo, nicht in
  Logs schreiben, nicht an externe Dienste geben. Lesen über die Schnittstelle ist erlaubt.
  Ausnahme in der Entwicklungsphase: Titel dürfen ins Diagnose-Log (design.md 13).
  Ausnahme Testdaten: `testdata/real-*.json` aus `tools/testdata/export_testdata.py`
  (alle Texte ersetzt, keine Medien, Orte verschoben, Leck-Prüfung im Skript) bleiben
  lokal (`.gitignore`, Anf. 7.34 OV2: die Zeiten sind echt); nur dieses Skript benutzen.
- **Schnittstelle für Claude** (Debug-Build, design.md 15): erst nutzbar, wenn Kevin sie
  in den Einstellungen einschaltet. Lesen ohne Rückfrage; Reminder anlegen, ändern,
  pausieren, archivieren nur nach Absprache mit Kevin.
  ```bash
  adb shell "content call --uri content://com.avlreminder.claude --method today"
  adb shell "content call --uri content://com.avlreminder.claude --method save --arg '{\"reminder\":{\"id\":3,\"goal\":\"…\"}}'"
  ```

## Git
- Remote `origin`: GitHub `FairPlayer4/avlreminder` (privat). `develop` ist der
  Hauptarbeitsbranch, dazu Feature-Branches (`feature/v1.x`); dort nach eigenem Ermessen committen und pushen.
- `main` nur per Pull Request, nie direkt pushen. Nach einer abgeschlossenen Etappe
  einen PR anbieten (`gh pr create`, Basis `main`; ohne `gh` die Compare-URL
  `https://github.com/FairPlayer4/avlreminder/compare/main...<branch>` nennen).
- GitHub Actions baut und testet nur nach dem Merge auf `main` (`.github/workflows/build.yml`,
  gleicher Befehl wie lokal; Debug-APK als Artefakt am Lauf), nicht bei PRs und nicht auf
  `develop` (GitHub-Minuten, Kap. 9, 2026-11-13). Vor einem PR nach `main` deshalb lokal
  mit `-PbackendStreng=true` bauen, alles grün, und nach dem Merge den Lauf ansehen.
  Andere Branches nur bei Bedarf von Hand (`gh workflow run build.yml --ref <branch>`).
  Wird das Repo öffentlich, wieder bei PRs bauen.
- Commit-Nachrichten auf Deutsch, Präfix mit Version (z. B. `V1.2b: …`).
