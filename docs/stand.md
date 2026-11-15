# Stand und nächste Schritte

Stand: 2026-11-15. Bei jedem größeren Schritt aktualisieren. Erledigtes steht in git, in
anforderungen.md Kap. 10.1 und in den Review-Berichten (`.review/runs/`), nicht hier.

## Auf dem Gerät (S24)
- **V1.3c4** (`versionCode` 24), installiert am 2026-11-15: V1.3c2 bis V1.3c4 (Konto
  löschen, Passwortmanager als Hauptweg, `SCHEDULE_EXACT_ALARM`). Keine Migration (Schema 11
  wie vorher). Start ohne Fehler, Alarme neu geplant; *Wecker und Erinnerungen* per
  `appops` erteilt, `exact=true`. Android hatte die Alarme auch vorher schon exakt geplant,
  weil die App von der Akku-Optimierung ausgenommen ist (`exactAllowReason=allow-listed`).
- Davor V1.3c1 (2026-11-14, mit V1.3c0; Migration auf Schema 11, Sicherung
  `avlreminder-v10-20261114-172224.db`), V1.3b3 (2026-11-13), V1.3b2 (2026-11-12),
  V1.3b1 (2026-11-11): Sitzung mit Timer und Uhr (7.11), davor 1.3b0.6 (Ankunft im Zeitraum
  mit WLAN, 7.29) und 1.3b0.5 (Cloud-Backup Schritte 1–5). Nach dem Start jeweils:
  Berechtigungen ok, Alarme neu geplant, keine Fehler im Diagnose-Log.

## Cloud-Backup
Schritte 1–5 installiert (2026-11-07 bis 11-09), seit 2026-11-11 in `develop`.
Entscheidungen: [cloud-entscheidungen.md](cloud-entscheidungen.md), Aufbau: design.md 16,
Backend: [backend.md](backend.md). **Eigenbetrieb** seit 2026-11-07: Die App sichert
Kevins echte Daten; vor dem ersten weiteren Nutzer kommt die Sicherheitsphase (Backend
`CLAUDE.md`). Ältere Fassungen holen (R-4): Backend fertig, in der App fehlt die Auswahl
einer Fassung.

## Offen
0. **Gerätetests zu V1.3c0–V1.3c4** (alles installiert, siehe oben): ein verpasster Termin
   und ein Notaus-Skip nehmen die Benachrichtigung mit; ein abgelehnter Knopf in der
   Benachrichtigung lässt sie stehen; die Morgenübersicht kommt, auch wenn die App kurz
   nach ihrer Zeit geöffnet wird; einen Punkt aus der Benachrichtigung eines klingelnden
   Vollbild-Alarms abhaken und im Vollbild den Nachweis starten: der nächste Punkt bzw.
   „Nachweis läuft“ bleibt stehen (Lauf 6, E26); K33–K37, C19. Nur auf dem Emulator mit
   Testkonto: C15–C18.
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
   `main` setzt Kevin. Lückenliste vom 2026-11-15: [playstore.md](playstore.md); zuerst
   Name und `applicationId` entscheiden.
8. **Vor der Sicherheitsphase** (Backend-Prüfung 2026-11-12; Liste „Vorher fertig“ in
   Backend `docs/b2b-entwurf.md`): Format v2 kommt über `client/`. *Konto löschen* und
   die Displaysperre (SE5) sind in V1.3c2 umgesetzt, A-15 neu in V1.3c3.
9. **Repo veröffentlichen** (in Wochen bis Monaten, 7.34): keine Secrets gefunden.
   App und Backend sollen in den nächsten Wochen öffentlich werden. Erledigt: Zugang nur
   nach Freigabe, Kostengrenzen und konfigurierbarer Server (OV1), echte Testdaten nur
   lokal (OV2), Lizenz Apache-2.0 (OV3), Doku bereinigt (OV8). Offen: für die App eine bereinigte Historie in einem neuen Repo (OV5: Arbeits-Adresse,
   Testdaten), kurz vorher OV6 und OV7.

## Beobachtungen aus der echten Nutzung
- Ein Reminder klingelte am 2026-10-31 in Runden um 11:30 und 12:00; prüfen, ob das zur
  eingestellten Eskalation passt.
- Morgen-Übersicht steht auf 8:00; vermutlich von Kevin so eingestellt.
