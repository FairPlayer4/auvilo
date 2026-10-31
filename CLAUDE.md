# avlreminder – Hinweise für Claude

Android-App für sehr wirksame Reminder, zuerst nur für Kevin selbst (V1: ein Nutzer,
lokal, kein Backend, Sideload auf sein privates S24 Ultra, Android 17 / API 37).
Strenge und invasive Mechanismen sind gewollt. Kommunikation auf **Deutsch, mit ß**.

## Wo was steht
- [docs/stand.md](docs/stand.md) – aktueller Stand, offene Punkte, nächste Schritte (zuerst lesen)
- [docs/anforderungen.md](docs/anforderungen.md) – Kevins Anforderungen, Entscheidungen (Kap. 9), Planung (Kap. 10)
- [docs/design.md](docs/design.md) – technisches Design je Version
- [docs/geraetetest.md](docs/geraetetest.md) – Tests auf dem echten Gerät
- [docs/entwicklungsumgebung.md](docs/entwicklungsumgebung.md) – Toolchain, Stolpersteine auf dem Firmen-PC
- Module: `core` (reine Kotlin-Logik, Kover-Abdeckung), `app` (Android, Compose, Room)

## Zusammenarbeit
- Kevin entwickelt die Anforderungen iterativ mit Claude: Feedback und Rückfragen geben,
  Empfehlung statt Optionsliste.
- Die vollständige Antwort immer als Text schreiben, bevor die Abschluss-Box kommt.
- Neue Anforderungen und Entscheidungen in `docs/anforderungen.md` festhalten
  (Abschnitt 7.x und Tabelle in Kap. 9 mit Datum).

## Bauen und testen (Git Bash)
```bash
export JAVA_HOME="$LOCALAPPDATA/Programs/jdk-21.0.12.1+1"
export JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=%USERPROFILE%.Bartik\.jt'
./gradlew -q :core:check :core:koverLog :app:testDebugUnitTest :app:assembleDebug
```
- Ohne `jdk.net.unixdomain.tmpdir` scheitern Gradle und die Android-Tools mit
  „Unable to establish loopback connection“ (siehe entwicklungsumgebung.md).
- Gradle, adb und `git push` brauchen in Claude Code Netz/Geräte außerhalb der Sandbox.
- Vor jeder Installation: alle Tests grün. UI-Tests laufen mit Robolectric/Compose
  (`app/src/test`), Migrationen haben einen eigenen `MigrationTest`.

## Gerät (Kevins privates Handy)
- **Installieren erst nach Kevins ausdrücklichem Go.** Vorher bauen, zusammenfassen,
  was sich ändert, und die Risiken nennen.
- adb: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` (Seriennummer siehe `adb devices`).
- Ohne Rückfrage erlaubt: lesende Geräteinfos, App installieren/deinstallieren, Logs
  lesen, App-Berechtigungen setzen. Alles andere vorher fragen, auch `logcat -c`.
- Immer `adb install -r`, **nie vorher deinstallieren** (sonst sind Kevins Daten weg).
- Bei Gerätetests genau sagen, wann Kevin sperren, entsperren oder etwas tun soll.
  Simulationen danach zurücksetzen.
- Diagnose: `adb exec-out run-as com.avlreminder cat files/diag/diag.log` und
  `adb logcat -d -s AvlDiag`. Die Datenbank (`databases/avlreminder.db` plus `-wal`, `-shm`)
  nur in ein temporäres Verzeichnis kopieren, lesen und wieder löschen.
- Inhalte von Kevins Remindern und Nachweisen sind privat: nicht ins Repo, nicht in
  Logs schreiben, nicht an externe Dienste geben.

## Git
- Remote `origin`: GitHub `FairPlayer4/avlreminder` (privat, seit 2026-10-31; vorher
  Bitbucket `dg-ds/avlreminder`, nur noch Archiv). Arbeit auf `develop` oder
  Feature-Branches (`feature/v1.x`); dort nach eigenem Ermessen committen und pushen.
- `main` nur per Pull Request, nie direkt pushen. Nach einer abgeschlossenen Etappe
  einen PR anbieten (`gh pr create`, Basis `main`; ohne `gh` die Compare-URL
  `https://github.com/FairPlayer4/avlreminder/compare/main...<branch>` nennen).
- Commit-Nachrichten auf Deutsch, Präfix mit Version (z. B. `V1.2b: …`).
