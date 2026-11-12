# Stand und nächste Schritte

Stand: 2026-11-12. Bei jedem größeren Schritt aktualisieren. Erledigtes steht in git, in
anforderungen.md Kap. 10.1 und in den Review-Berichten (`.review/runs/`), nicht hier.

## Auf dem Gerät (S24)
- **V1.3b2** (`versionCode` 18), installiert am 2026-11-12: Antworten auf Review-Lauf 4
  (Sitzung zählt in der Woche ihres Starts, *Abbruch* 60 s zurücknehmbar, *Teilschritt*
  nach aufgebrauchtem Limit ausgeblendet, F2 über die geplante erste Prüfung, wiederholte
  WLAN-Meldung keine Ankunft) und Tests. Start ohne Fehler, Alarme neu geplant.
- Davor V1.3b1 (2026-11-11): Sitzung mit Timer und Uhr (7.11), davor 1.3b0.6 (Ankunft im Zeitraum mit WLAN, 7.29) und 1.3b0.5 (Cloud-Backup
  Schritte 1–5). Nach dem Start jeweils: Berechtigungen ok, Alarme neu geplant, keine
  Fehler im Diagnose-Log.

## Cloud-Backup
Schritte 1–5 installiert (2026-11-07 bis 11-09), seit 2026-11-11 in `develop`.
Entscheidungen: [cloud-entscheidungen.md](cloud-entscheidungen.md), Aufbau: design.md 16,
Backend: [backend.md](backend.md). **Eigenbetrieb** seit 2026-11-07: Die App sichert
Kevins echte Daten; vor dem ersten weiteren Nutzer kommt die Sicherheitsphase (Backend
`CLAUDE.md`). Ältere Fassungen holen (R-4): Backend fertig, in der App fehlt die Auswahl
einer Fassung.

## Offen
1. **Gerätetests** aus [geraetetest.md](geraetetest.md): K15, K23–K26 (vorher *Zuhause* in
   *Orte* mit WLAN neu festlegen), K27–K30 (Sitzung, dabei: zeigt die Uhr den
   Chronometer?), K31, K32. In den nächsten Tagen im `diag.log` prüfen, dass keine Ankunft mehr direkt nach
   „app start“ kommt und die erste Prüfung eines Zeitraums pünktlich ist (`late=`).
2. **Eine Woche Training mit der Sitzung**, dann Entwurf **V1.3c**: Abläufe (7.12), ein
   Vollbild zur Zeit mit *+1 wartet* (7.3), Knöpfe je Art mit *Rest später* (7.4);
   Vorlagen mit Sitzung.
3. **Nutzung beobachten und trimmen** (7.27): Reminder werden manchmal zu streng angelegt
   und mehrfach um 10 min geschoben; Cheats bekommen später Rückfragen. Zählen erst vor V2
   mit einer neuen Testsession (NT4).
4. **Vorschläge in der App:** 1, 4, 5, 6, 7 und 9 sind umgesetzt (Kevin markiert sie);
   offen VS3 (7.25).
5. **Später, stört aktuell nicht:** Kevin beschreibt die Szenarien zu Checklisten mit
   mehreren Uhrzeiten (vermuteter Fehler; Code und Tests am 2026-11-11 ohne Befund, die
   Engine-Fälle laufen seit 2026-11-12 auch gegen Room, ohne Befund), Training-Alarm am
   Morgen; aus Review-Lauf 2 optional Paket `alarm` aufteilen.
6. **Versionsname** je installiertem Stand `1.3b<n>-backup<Schritt>`, `versionCode` +1.
7. **Play-Store-Release** ohne Eile, frühestens mit V2 (7.28 PS5–PS9). Branch-Regel für
   `main` setzt Kevin.

## Beobachtungen aus der echten Nutzung
- „Test-Reminder“ klingelte am 2026-10-31 in Runden um 11:30 und 12:00; prüfen, ob das zur
  eingestellten Eskalation passt.
- Morgen-Übersicht steht auf 8:00; vermutlich von Kevin so eingestellt.
