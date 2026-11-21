# Stand und nächste Schritte

Stand: 2026-11-21. Bei jedem größeren Schritt aktualisieren. Erledigtes steht in git, in
anforderungen.md Kap. 10.1 und in den Review-Berichten (`.review/runs/`), nicht hier.

## Auf dem Gerät (S24)
- **V1.4a** (`versionCode` 35), installiert am 2026-11-21 über V1.3c11 (Handy) und neu auf
  der Galaxy Watch4 (SM-R870, Wear OS 6, API 36), beides über Debugging per WLAN. Enthält
  V1.3c12–c14. Start ohne Fehler, Alarme neu geplant, Berechtigungen weiter erteilt; die Uhr
  bekommt den Stand vom Handy (`watch nearby=true`). Watch-App für die Sitzung
  (Anf. 7.14, design.md 17), neues Modul `:wear`. Keine Migration. Am Handy ändert sich
  nur etwas, wenn die Watch-App auf der Uhr ist: Dann kommt die Sitzung nicht mehr
  zusätzlich als gespiegelte Benachrichtigung auf die Uhr. Erst die Handy-App installieren,
  dann die Uhr (adb über WLAN, design.md 17 „Uhr als Gerät“). Gerätetests K44–K48.
- **V1.3c14** (`versionCode` 34) gebaut, noch nicht installiert: Umbau-Etappe gegen die
  Trends aus den Review-Läufen, ohne neues Verhalten (anforderungen.md Kap. 9, 2026-11-19):
  Zeitregeln im Core, Stolperdrähte, Knöpfe gegen Doppeltipp, Mutations-Suite
  `tools/mutate.py`. Keine Migration. Enthält V1.3c13. Dazu (2026-11-19, Backend-Review
  Lauf 11): *Konto löschen* fragt nach einer verlorenen Antwort per Erneuern nach
  (`ConnectCloudServerTest`, Mutant M16); `gradle/backend-commit.txt` steht auf dem
  Backend-Stand dafür.
  Am Gerät zusätzlich prüfen: *Verschieben* im Alarm zeigt dieselben Ziele wie vorher,
  Notaus „bis morgen früh“.
- **V1.3c13** (`versionCode` 33) gebaut, noch nicht installiert: Fixes und Antworten aus
  Review-Lauf 7 (anforderungen.md Kap. 9, 2026-11-19). Keine Migration. Vor dem Installieren
  `gradle/backend-commit.txt` auf den Backend-Stand nachziehen (der Build warnt sonst).
  Am Gerät prüfen: Sicherungsdatei exportieren (erst Datei, dann Passwort; *Abbrechen*
  lässt keine Datei liegen), Probealarm im Notaus, Foto-Nachweis mit weggescrollter Kamera.
- **V1.3c12** (`versionCode` 32) gebaut, noch nicht installiert: Cloud-Backup nur auf
  Einladung, Text und *Einladung per E-Mail anfragen* mit Kennung (7.28 PS16, playstore.md
  S2). Keine Migration. Auf dem S24 ändert sich nur der Text unter dem Schalter.
- **V1.3c11** (`versionCode` 31), installiert am 2026-11-16 über V1.3c10: Schnellerinnerung
  per Sprache, einfachste Fassung (Anf. 7.35, design.md 11.2). Keine Migration. Start ohne
  Fehler, Alarme neu geplant, Mikrofon schon erlaubt. Auf dem S24 gibt es zwei
  Erkennungsdienste: Android System Intelligence (auf dem Gerät) und Google (Standard).
  Im Alltag testen (Gerätetest K43): Trifft die Erkennung das Muster, welche Erkennung
  läuft, reicht die Pause, bevor sie ein Stück beendet?
- **V1.3c10** (`versionCode` 30), installiert am 2026-11-16 über V1.3c7: Foto-Nachweis mit
  den Lautstärketasten auslösen (7.5 N2a). Keine Migration. Start ohne Fehler, Alarme neu
  geplant, alle Berechtigungen weiter erteilt. Enthält V1.3c9 und V1.3c8:
- **V1.3c9** (`versionCode` 29), mit V1.3c8: Einstieg für
  Tester mit Probealarm, wiederholbar unter *Einstellungen → Hilfe* (7.28 PS15, playstore.md
  N3); Sicherungsdatei, Export und Import mit Passwort, ohne Fotos und Audio (7.28 PS14, N4);
  `minSdk` 34 statt 37 für Tester mit Android 14 bis 16 (Anf. 8.1).
  Keine Migration.
- **V1.3c7** (`versionCode` 27), installiert am 2026-11-15: Hinweis vor Androids
  Standort-Dialog (7.28 PS13). Keine Migration. Start ohne Fehler, Alarme neu geplant,
  Standort „Immer erlauben“ weiter erteilt (`background=true`).
- Davor V1.3c6 (`versionCode` 26), installiert am 2026-11-15: Akku-Optimierung nur empfohlen,
  ohne `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (7.28 PS12). Keine Migration. Start ohne
  Fehler, Alarme neu geplant; die Akku-Ausnahme blieb nach dem Update bestehen
  (`deviceidle whitelist`, `batteryExempt=true`).
- Davor V1.3c5 (2026-11-15): nur der Anzeigename „AuViLo-Reminder“ (7.28 PS11),
  `applicationId` unverändert.
- Davor **V1.3c4** (`versionCode` 24), installiert am 2026-11-15: V1.3c2 bis V1.3c4 (Konto
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
`CLAUDE.md`). Ältere Fassungen holen (R-4): Backend und `protokoll` fertig
(`fassungEinspielen`), in der App fehlen die Auswahl einer Fassung und der Aufruf.

## Offen
0. **Gerätetests zu V1.3c0–V1.3c11** (alle installiert, siehe oben): ein verpasster Termin
   und ein Notaus-Skip nehmen die Benachrichtigung mit; ein abgelehnter Knopf in der
   Benachrichtigung lässt sie stehen; die Morgenübersicht kommt, auch wenn die App kurz
   nach ihrer Zeit geöffnet wird; einen Punkt aus der Benachrichtigung eines klingelnden
   Vollbild-Alarms abhaken und im Vollbild den Nachweis starten: der nächste Punkt bzw.
   „Nachweis läuft“ bleibt stehen (Lauf 6, E26); K33–K37, K38 (nur noch das Antippen), K39 (nur, falls die Erlaubnis einmal fehlt), K40, K41, K42, C19, C20 (Emulator); ohne Vollbild-Erlaubnis ein Alarm (playstore-erklaerungen.md P2). Nur auf dem Emulator mit
   Testkonto: C15–C18.
1. **Gerätetests** aus [geraetetest.md](geraetetest.md): K15, K23–K26 (vorher *Zuhause* in
   *Orte* mit WLAN neu festlegen), K27–K30 (Sitzung, dabei: zeigt die Uhr den
   Chronometer?), K31, K32. In den nächsten Tagen im `diag.log` prüfen, dass keine Ankunft mehr direkt nach
   „app start“ kommt und die erste Prüfung eines Zeitraums pünktlich ist (`late=`).
2. **V1.4a Watch-App** umgesetzt (design.md 17), nicht installiert; Gerätetests K44–K48.
   Als Nächstes V1.4b Abläufe (7.12, bisher
   V1.3c; die Sitzung hat sich im Training bewährt), dann Checklisten überarbeiten
   (7.13 C9, Kevin macht sich dazu nach den Abläufen Gedanken). Offen aus V1.3c: Vorlagen
   mit Sitzung.
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
   `main` setzt Kevin. Lückenliste vom 2026-11-15: [playstore.md](playstore.md). Name
   „AuViLo-Reminder“ entschieden (7.28 PS11), Anzeigename in V1.3c5; `applicationId`, Paket
   und Logo vor dem Antrag.
8. **Vor der Sicherheitsphase** (Backend-Prüfung 2026-11-12; Liste „Vorher fertig“ in
   Backend `docs/b2b-entwurf.md`): Format v2 kommt über `client/`. *Konto löschen* und
   die Displaysperre (SE5) sind in V1.3c2 umgesetzt, A-15 neu in V1.3c3.
9. **Repo veröffentlichen** (in Wochen bis Monaten, 7.34): keine Secrets gefunden.
   App und Backend sollen in den nächsten Wochen öffentlich werden. Erledigt: Zugang nur
   nach Freigabe, Kostengrenzen und konfigurierbarer Server (OV1), echte Testdaten nur
   lokal (OV2), Lizenz Apache-2.0 (OV3), Doku bereinigt (OV8). Offen: für die App eine bereinigte Historie in einem neuen Repo (OV5: Arbeits-Adresse,
   Testdaten), kurz vorher OV6 und OV7.
10. **Rückmeldung senden** (Anf. 7.36, 2026-11-18): Diagnose und Vorschläge auf Einladung,
    aktive Wahl, Vorschau, ehrlicher Hinweis (nicht Ende-zu-Ende, teilweise von KI
    ausgewertet), später verschlüsselt. Nur festgehalten; Stufe 1 (Vorschläge per E-Mail
    mit Vorschau) geht ohne Backend ([rueckmeldung-entwurf.md](rueckmeldung-entwurf.md)).

## Beobachtungen aus der echten Nutzung
- Ein Reminder klingelte am 2026-10-31 in Runden um 11:30 und 12:00; prüfen, ob das zur
  eingestellten Eskalation passt.
- Morgen-Übersicht steht auf 8:00; vermutlich von Kevin so eingestellt.
