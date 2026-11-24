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
- [docs/rueckmeldung-entwurf.md](docs/rueckmeldung-entwurf.md) – Diagnose und Vorschläge senden, Verschlüsselung (Anf. 7.36, noch keine Etappe)
- [docs/playstore.md](docs/playstore.md) – was für den Play Store fehlt (Lückenliste); Entwürfe der Console-Erklärungen in [playstore-erklaerungen.md](docs/playstore-erklaerungen.md)
- [docs/entwicklungsumgebung.md](docs/entwicklungsumgebung.md) – Toolchain und Pfade auf Kevins Rechner
- Module: `core` (reine Kotlin-Logik, Kover-Abdeckung), `app` (Android, Compose, Room),
  `wear` (Watch-App für die Sitzung, Wear OS, optional; design.md 17)

## Zusammenarbeit
- Kevin entwickelt die Anforderungen iterativ mit Claude: Feedback und Rückfragen geben,
  Empfehlung statt Optionsliste.
- Die vollständige Antwort immer als Text schreiben; die Zusammenfassung in der
  Abschluss-Box ersetzt sie nie.
- Neue Anforderungen und Entscheidungen in `docs/anforderungen.md` festhalten
  (Abschnitt 7.x und Tabelle in Kap. 9 mit Datum).
- Eine Etappe ist erst fertig, wenn ihr Entwurf in die Themen von `design.md` eingearbeitet
  und der Entwurfsabschnitt gelöscht ist; `stand.md` und Kap. 10.1 nachziehen.

## Abnahme jeder Etappe (V1.3c14, gegen wiederkehrende Review-Funde)
- Regeln (Zeiten, Limits, was erlaubt ist) stehen in `:core` mit Test. `TimeRuleTest` und
  `ClockRuleTest` nur mit Begründung erweitern; wird eine Stelle frei, die Zahl senken.
- Jeder neue Weg über eine Android-Komponente (Activity-Ergebnis, Berechtigung, Receiver,
  Dienst) hat einen Test durch die echte Komponente; wartet er auf einen Systemdialog, auch
  einen mit Neuaufbau (`scenario.recreate()`).
- Knöpfe mit Engine-Aufruf nutzen `rememberBusy`, mit einem Test, der zweimal tippt.
- Neue Wege bekommen einen Mutanten in `tools/mutate.py`.
- Zitiert die Doku App-Texte wörtlich, prüft sie `DocQuoteTest`. Zahlen nennt die Doku als
  Konstante mit Beispiel, statt den Wert herzuleiten.

## Bauen und testen (Git Bash)
```bash
./gradlew -q :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :wear:testDebugUnitTest :wear:lintDebug :wear:assembleDebug
```
- Die CI baut mit `-PbackendStreng=true`: bricht ab, wenn der Backend-Stand nicht zu
  `gradle/backend-commit.txt` passt. Lokal braucht man das nur, um einen roten Lauf
  nachzustellen. Vor einem PR nach `main` die Mutations-Suite
  `python -I -X utf8 tools/mutate.py` (etwa 20 min, im Hintergrund, Netz wie
  Gradle): Gegenprobe grün, alle Mutanten entdeckt; das Ergebnis in den PR-Text.
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
- Vor der Installation die Version auf dem Handy prüfen (`adb shell dumpsys package com.avlreminder`)
  und mit `stand.md` abgleichen; dort kann eine installierte Version fehlen (2026-11-15).
- **Nach jeder Installation bzw. Gerätesitzung Kevin erinnern, USB-Debugging auszuschalten**
  (*Einstellungen → Entwickleroptionen*; Kevin, 2026-11-15). Solange es an ist, kann ein
  freigegebener Rechner per USB voll auf das Handy zugreifen. Für die nächste Installation
  schaltet Kevin es wieder ein.
- **Debugging über WLAN** geht für Handy und Uhr (Kevin, 2026-11-21), nur im WLAN zu Hause:
  Kevin nennt IP:Port und Code aus *Gerät mit Kopplungscode koppeln*, dann `adb pair`; den
  Port zum Verbinden zeigt `adb mdns services` (`_adb-tls-connect`), dann `adb connect`.
  Danach Kevin erinnern, *Debugging über WLAN* auszuschalten (wie USB-Debugging oben).
- **Uhr** (Watch-App, design.md 17): nur über WLAN, dieselben Regeln wie beim Handy: erst
  nach Kevins Go, `adb install -r`, nie deinstallieren; erst die Handy-App, dann die Uhr,
  beide aus demselben Stand. Danach Kevin erinnern, auf der Uhr *ADB-Debugging* und
  *Debugging über WLAN* auszuschalten. Mehrere Geräte: `adb -s <serial>`; die Uhr startet
  mit `am start -n com.avlreminder/com.avlreminder.wear.WatchActivity`.
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
- Remote `origin`: GitHub `FairPlayer4/auvilo` (öffentlich, seit 2026-11-23, Historie
  bereinigt nach 7.34 OV5; das alte private Repo `FairPlayer4/avlreminder` ist das Archiv
  mit den PRs und Commits davor, Zuordnung alt→neu in `E:\src\umzug-auvilo\commit-map.txt`).
  Der lokale Ordner heißt weiter `avlreminder`. `develop` ist der
  Arbeitsbranch; dort nach eigenem Ermessen committen und pushen.
- **Feature-Branches nur nach Kevins Bestätigung anlegen** (sonst kommt er durcheinander,
  2026-11-15); ohne Bestätigung direkt auf `develop` arbeiten.
- `main` nur per Pull Request, nie direkt pushen. Nach einer abgeschlossenen Etappe
  einen PR anbieten (`gh pr create`, Basis `main`; ohne `gh` die Compare-URL
  `https://github.com/FairPlayer4/auvilo/compare/main...<branch>` nennen).
- GitHub Actions baut und testet auf `main`, `develop` und bei PRs (`.github/workflows/build.yml`,
  gleicher Befehl wie lokal; Debug-APK als Artefakt am Lauf; wieder seit dem öffentlichen
  Repo, 2026-11-23, PRs aus Forks erst nach Freigabe). **Die CI ist die maßgebliche
  Prüfung** (Kevin, 2026-11-24): nach einem Push den Lauf abwarten, nach `main` nur mit
  grüner PR-CI mergen, nach dem Merge den Lauf ansehen. Lokal gebaut wird trotzdem vor
  jedem Push, weil daraus die APK fürs Handy entsteht.
  Wird das Repo öffentlich, wieder bei PRs bauen.
- Commit-Nachrichten auf Deutsch, Präfix mit Version (z. B. `V1.2b: …`).
