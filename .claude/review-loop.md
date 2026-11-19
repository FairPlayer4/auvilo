# Review-Loop-Konfiguration

## Budget
- Review-Einheiten pro Lauf: 3
- Architektur-Review: jeder 4. Lauf
- Mutations-Stichproben pro Lauf: 2, zusätzlich zur festen Suite `tools/mutate.py`

## Git
- Basisbranch: `develop`

## Checks
- Alles in einem Befehl (Git Bash, mit Netz):
  `./gradlew -q :core:check :core:koverLog :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- Mutations-Suite: `python -I -X utf8 tools/mutate.py`; die Zahl „entdeckt/gesamt“ geht in die
  Kennzahlen von `state.md`.

## Anforderungsquellen
- `docs/` (zuerst `stand.md`, dann `anforderungen.md`, `design.md`), `CLAUDE.md`

## Ausschließen
`app/schemas/**`, `testdata/**`, `**/build/**`

## Auto-Fix
- erlaubt: nur Kleinigkeiten
- nie anfassen: Migrationen (`app/…/data/Migrations.kt`, `app/schemas/**`)

## Empfehlungen und Trends (Kevin, 2026-11-19, V1.3c14)
- Eine Empfehlung (E-Nummer), die zwei Läufe offen ist, kommt in eine Etappe oder wird
  ausdrücklich abgelehnt; sie steht nicht unbegrenzt offen. Der Bericht nennt je offener
  Empfehlung, seit welchem Lauf sie offen ist.
- Ein Trend (dritte Beobachtung) bekommt einen Vorschlag für einen Test, der von selbst rot
  wird (Stolperdraht, Vertragstest, Mutant), nicht nur einen Fix.
- `state.md` führt je Lauf eine Zeile Kennzahlen: Mutations-Suite entdeckt/gesamt, Stellen in
  `TimeRuleTest`, offene Empfehlungen.

## Projektkontext für den Reviewer
Android-App (Kotlin, Compose, Room) für sehr wirksame Reminder, ein Nutzer, lokal; strenge
Mechanismen sind gewollt. `:core` ist reines Kotlin mit allen Regeln, `:app` setzt sie um.
Zeit kommt über die Uhr der Engine. Private Inhalte (Reminder, Nachweise, Positionen) dürfen
nicht ins Log. Bekannte Grenzen (design.md 12) nicht als Fund melden.
