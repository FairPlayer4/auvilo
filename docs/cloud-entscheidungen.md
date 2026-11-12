# Cloud-Backup – Entscheidungen

> Die Entscheidungen zum Cloud-Backup in der App (Backend B2b) mit ihren Gründen: A-1…A-15
> Einbindung (bestätigt 2026-11-07), W-1…W-8 Prüfen und Wiederherstellen, R-1…R-6 Schutz
> vor Datenverlust (bestätigt 2026-11-09). Umgesetzt bis 2026-11-10; den aktuellen Aufbau
> beschreibt [design.md](design.md) Kap. 16, den Gerätetest [geraetetest.md](geraetetest.md)
> Abschnitt D. Die Schrittprotokolle der Umsetzung und die Entwürfe stehen im Git-Verlauf
> (`git log -- docs/backup-entwurf.md docs/wiederherstellen-entwurf.md`).
> Grundlage im Backend (`../avlreminder-backend`): `docs/b2b-entwurf.md` (E-B2b-1…13),
> `docs/formate.md`, `docs/diagnose-entwurf.md`; hier Anf. 7.28 (PS8, PS8a, PS9) und 8.4.

## Ziel und Grenzen
- Kevins echte Daten werden Ende-zu-Ende verschlüsselt gesichert und lassen sich auf einem
  neuen Gerät oder nach Neuinstallation zurückholen.
- **Eigenbetrieb** (Backend, 2026-11-07): Bis jemand außer Kevin die App gegen die Cloud
  nutzt, darf Claude die Schlüssel kennen; vor dem ersten weiteren Nutzer wird alles
  zurückgesetzt und neu eingerichtet. Die Schlüssel sind bis dahin als bekannt zu betrachten.
- Die App funktioniert ohne Backend vollständig; das Backup darf scheitern. Das
  Migrations-Backup und die lokalen Tageskopien (R-1) bleiben.
- **Nicht** dabei: Synchronisation (B3), Fotos und Audio (Verweise zeigen nach dem
  Wiederherstellen ins Leere, die App hält das aus), Play Integrity, Play-Store-Variante.

## A – Einbindung

| # | Frage | Entscheidung | Warum |
|---|---|---|---|
| A-1 | Eigene App-Variante? | **Nein**, solange nur Kevin die App nutzt: normaler Build hinter dem Schalter aus PS8 (aus = keine Anmeldung, kein Netz). Variante bzw. interner Test-Track erst mit dem ersten Tester. | Eine Variante hätte eine eigene Datenbank; gesichert würden nicht Kevins echte Daten. |
| A-2 | Einbindung von `client/` | **Composite Build** (`includeBuild("../avlreminder-backend/client")`), die App hängt von `verbindung` ab (bringt `protokoll` mit). Fester Commit in `gradle/backend-commit.txt`; die CI holt ihn mit einem **Deploy-Key** (nur lesen, nur Backend-Repo). Lokal warnt der Build bei anderem Commit oder uncommitteten Änderungen in `client/`/`proto/` („diese APK nicht installieren“), mit `-PbackendStreng=true` (CI) bricht er ab. | E-B2b-10. Ohne festen Commit baute die CI einen anderen Stand als lokal; ein Deploy-Key gilt nur für ein Repo und läuft nicht ab. Langfristig mit der Sicherheitsphase neu entscheiden (Monorepo oder veröffentlichte Versionen). |
| A-3 | Netz | Berechtigung **`INTERNET`**. Anf. 8.4: Daten verlassen das Gerät nur Ende-zu-Ende verschlüsselt im Backup, und nur, wenn es eingeschaltet ist. | Die App hatte vorher gar kein Netz. |
| A-4 | Anmeldung | **Credential Manager**, „Mit Google anmelden“ (`GetSignInWithGoogleOption`), `serverClientId` = Web-Client des Backend-Projekts, `nonce` = `Geraetenachrichten.nonce(Geräteschlüssel)`. Im GCP-Projekt ein Web- und ein Android-Client (unten). | Der Server prüft `aud` gegen `GOOGLE_CLIENT_IDS`, bei Android ist das der Web-Client. Die Nonce bindet das ID-Token an den Geräteschlüssel. |
| A-5 | Geräteschlüssel | ECDSA P-256 im **Android Keystore**, nicht exportierbar, StrongBox wenn vorhanden, `SHA256withECDSA`. Verloren (App-Daten gelöscht) → neues Gerät. | Der private Schlüssel verlässt das Gerät nie, auch Claude sieht ihn nicht. |
| A-6 | Keysets | Tink-Keysets **verschlüsselt mit einem Keystore-AES-GCM-Schlüssel**, Datei in `filesDir/backend/` (`LokaleAblage`). | Schützt auch gegen Kopieren der Datei. Nach Neuinstallation hilft der Wiederherstellungsschlüssel (gewollt). |
| A-7 | Sitzung und Zähler | Token und `GemerkteSicherung` in einem eigenen DataStore `backend`, nicht in den Einstellungen. Abgelaufene Sitzung vor dem Aufruf erneuern. | Die Einstellungen werden selbst gesichert (PS8a); das Token läuft ohnehin ab (PS9). |
| A-8 | Nutzlast | **ZIP** aus `avlreminder.db` (`VACUUM INTO`) und `einstellungen.json` (`Settings.backup()`), `schemaVersion` = `AvlDatabase.VERSION`; vorher Größenprüfung (8 MiB Chiffrat). | Komprimiert und ohne eigenes Format; das Backend sieht nur Bytes (E-B2b-1). |
| A-9 | Wann sichern? | **WorkManager stündlich**, nur mit Netz, nur bei geändertem SHA-256 von Datenbank und Einstellungen; dazu *Jetzt sichern*. | Im Backend entschieden (2026-11-04). Der Hash erspart einen Änderungszähler. |
| A-10 | Zwei Geräte | Automatisch nur, wenn die letzte Sicherung von diesem Gerät stammt; sonst ruht es sofort mit Benachrichtigung, die Rückfrage „Überschreiben?“ kommt mit *Jetzt sichern*. | E-B2b-6. Der Worker kann auf keine Antwort warten (2026-11-08/09). |
| A-11 | Wiederherstellen | Nur auf Wunsch, mit „Sicherung vom … von Gerät …“ und Rückfrage; vorher lokale Kopie; Einstellungen ohne *Notaus* und Schnittstelle; neueres Schema ablehnen (`NeueresSchema`). Ablauf W-5. | Ersetzen statt Zusammenführen: Ohne Sync gibt es nur ein sicherndes Gerät. |
| A-12 | Diagnose senden | **Eigener Logger fürs Backend** (Ereignisname, typisierte Felder, keine freien Texte; E-D-1), nicht `diag.log`. | `diag.log` enthält Titel (design.md 13); Claude darf Sendungen nur lesen, weil sie keine Inhalte haben. |
| A-13 | Fehler | Netz weg, Kill-Switch, Tageslimit: nur Status, nächste Stunde neu. Benachrichtigung erst, wenn die Sicherung 3 Tage nicht aktuell war (hängt am gemerkten Konto); lehnt der Server die Sitzung ab, sofort „Bitte neu anmelden“. | „Kosten vor Verfügbarkeit“: Ausfälle sind normal und dürfen nicht nerven. |
| A-14 | Oberfläche | Einstellungen **„Cloud-Backup (experimentell)“**: Einschalten, Anmelden, Einrichtung (Schlüssel zeigen bzw. eingeben), letzte Sicherung, *Jetzt sichern*, *Sicherung prüfen*, *Wiederherstellen*, *Sicherung löschen*, *Diagnose senden*, *Dieses Gerät abmelden*, *Cloud-Daten löschen* (R-3); ohne Sitzung „Nur dieses Handy zurücksetzen“ (löscht auf dem Server nichts). | Alles an einer Stelle; PS6 (Transparenz). |
| A-15 | Schlüssel sichern | **Papier ist der Standard** (geändert 2026-11-12, vorher öffnete sich der Speicher-Dialog sofort): Neuer Wiederherstellungsschlüssel → anzeigen, Hauptknopf *Aufgeschrieben* mit Rückfrage. *Im Passwortmanager speichern* nennt vorher die Folge (Anf. 7.33 WS4) und öffnet dann den Speicher-Dialog (`CreatePasswordRequest`); gespeichert genügt *Fertig*. Bei Abfrage zuerst `GetPasswordOption`, Eintippen als Ausweg. Eintrag „avlreminder Wiederherstellung <Konto-Anfang>“. **Umsetzen vor der Sicherheitsphase.** | Der Schlüssel öffnet das Schlüssel-Backup und schaltet neue Geräte frei (Backend E-B2b-13). Ohne geräteinterne Verschlüsselung des Google Passwortmanagers hängt damit die ganze E2E-Verschlüsselung am Google-Konto; der Hinweis kam bisher erst nach dem Speichern. Holen aus dem Passwortdienst bleibt, weil Abtippen fehleranfällig ist. Bricht man nach dem Speichern ab, bleibt dort ein Eintrag ohne Backup; löschen kann ihn nur der Passwortmanager. |

**Einrichtung:** Schalter an → Geräteschlüssel → Credential Manager, ID-Token mit Nonce →
`Anmelden` → `Einrichtung` aus `protokoll`: ohne Schlüssel-Backup neue Schlüssel, Upload
erst nach „aufgeschrieben“/gespeichert; mit Schlüssel-Backup Eingabe des Schlüssels
(höchstens drei Versuche) → erste Sicherung von Hand, danach stündlich.

## W – Prüfen und Wiederherstellen

| # | Frage | Entscheidung | Warum |
|---|---|---|---|
| W-1 | Entspricht die Sicherung den Daten? | *Sicherung prüfen*: holen, entschlüsseln, nach `filesDir/restore/pruefung.db`, mit Room öffnen (migriert, prüft das Schema), `integrity_check` und `foreign_key_check`. Je Zeile ein SHA-256 über alle Spalten (nach Namen sortiert, mit Typ, sodass `NULL`, `''` und `0` verschieden sind), zugeordnet über `id`; Einstellungen über ihre Werte. Danach Datei löschen. | Sagt je Tabelle, *was* abweicht (*geändert*, *nur hier*, *nur in der Sicherung*), ohne Inhalte zu zeigen. |
| W-2 | Ergebnis | „Sicherung vom … entspricht den Daten auf diesem Gerät“ oder Abweichungen je Tabelle; seit der Sicherung geändert → Hinweis und *Jetzt sichern und erneut prüfen*. | So prüft Kevin selbst, dass Sichern, Server und Entschlüsseln verlustfrei sind. |
| W-3 | Logger | `sicherung/geprueft` mit `gleich`, `tabellen`, `tabellen_abweichend`, `schema`, `dauer_ms`; Tabellen als feste Aufzählung. | Claude kann das Ergebnis bestätigen, ohne Inhalte zu sehen. |
| W-4 | Ältere Sicherung (Schema kleiner) | Migration nur in der Prüfdatei, nie in der laufenden Datenbank; neueres Schema ablehnen. | Der riskanteste Teil läuft auf einer Kopie. |
| W-5 | Ablauf | Prüfen wie W-1 → Übersicht („42 Reminder, hier 40“) mit Rückfrage → `restore/bereit.db` und Merker `restore/auftrag.json` → Neustart (Start-Activity mit neuem Task, `exit(0)`) → in `AppGraph` vor dem Öffnen (`DatabaseSwap.applyPending`): laufende Datenbank per `VACUUM INTO` nach `backup/vor-wiederherstellung-<Zeit>.db`, `bereit.db` umbenennen, `-wal`/`-shm` löschen, Merker löschen → Einstellungen übernimmt der Start (`finishRestore`), er plant alle Alarme neu. Scheitert der Tausch: `restore/fehlgeschlagen.json` mit Grund, Daten bleiben, automatisches Sichern wartet auf Bestätigung. | Umbenennen ist atomar; über den Merker setzt ein Absturz an jeder Stelle sauber fort oder bricht ab. Ein Prozessende löscht keine Alarme, nur *Beenden erzwingen*. |
| W-6 | Rückgängig | 7 Tage *Wiederherstellen rückgängig machen*: neueste Kopie vor dem Wiederherstellen mit ihren Einstellungen, gleicher Ablauf, ohne Netz. | Ein Fehlgriff ist ohne Cloud rückgängig. |
| W-7 | Danach | Nach dem Wiederherstellen von einem anderen Gerät oder lokal (Tageskopie, *Rückgängig*) sichert die App automatisch erst nach *Jetzt sichern*, das vor dem Ersetzen fragt (`Sicherungsablauf` merkt Fassung 0). | Sonst überschreibt ein Testgerät die Sicherung des S24 oder das stündliche Sichern lädt ältere Daten über die neuere Fassung. |
| W-8 | *Sicherung löschen* | Nur die Daten-Sicherung (alle Fassungen), nicht das Schlüssel-Backup; der Takt stoppt. Rückfrage mit Datum und „Auch die älteren Fassungen der letzten 7 Tage werden gelöscht“. | DSGVO Art. 17. Mit dem Schlüssel geht späteres Sichern ohne neue Einrichtung. |

Kein Umschalten zwischen mehreren Datenbanken im Betrieb (Kevin, 2026-11-09): daneben
aufbauen und vergleichen, getauscht wird nur beim Start.

## R – Schutz vor Datenverlust

| # | Maßnahme | Stand |
|---|---|---|
| R-1 | **Lokale Tageskopien:** `VACUUM INTO` nach `filesDir/backup/taeglich-<Datum>.db`, 7 tägliche und 4 wöchentliche (`SnapshotWorker`, täglich und beim Start); Einstellungen *Lokale Kopien*. Nur die Datenbank; beim Wiederherstellen daraus bleiben die Einstellungen. | umgesetzt |
| R-2 | **Bremse:** Fehlen gegenüber der letzten Sicherung mehr als die Hälfte oder mehr als 10 Reminder (oder Zeilen einer anderen Haupttabelle), lädt das automatische Sichern nicht hoch, ruht wie A-10 und meldet es; *Jetzt sichern* fragt. Zählungen im DataStore `backend`. | umgesetzt |
| R-3 | ***Zurücksetzen* entschärft:** *Dieses Gerät abmelden* sperrt nur das Gerät, Server-Daten bleiben (der Weg für Testgeräte). *Cloud-Daten löschen* nur mit eingetipptem „löschen“: sperrt erst alle anderen Geräte, löscht Sicherung samt Fassungen und Schlüssel-Backup, sperrt zuletzt dieses (`CloudLoeschen`). | umgesetzt |
| R-4 | **Ältere Fassung holen:** Backend fertig (`FassungsArchiv`, `FassungenAuflisten`, `SicherungHolen` mit Version, 2026-11-10). In der App fehlt die Auswahl einer Fassung; kommt mit dem Wiederherstellen einer älteren Fassung. Eine bewusst gewählte ältere Fassung darf den gemerkten Zähler nicht senken (E-B2b-3). | Backend fertig, App offen |
| R-5 | **Manuelle Sicherung per `run-as`** (Notfall, vor riskanten Tests): `databases/avlreminder.db` mit `-wal`/`-shm`, `files/datastore/settings.preferences_pb` und `files/backup/` per `adb exec-out run-as com.avlreminder cat …` in einen Ordner außerhalb beider Repos; zweimal holen und SHA-256 vergleichen; auf dem PC nur `integrity_check` und Zeilenzahlen, keine Inhalte. Nie *Beenden erzwingen* (löscht alle Alarme). | Ablauf |
| R-6 | Schon vorher da: Kopie vor jeder Migration, 7 Tage Fassungen auf dem Server, Schlüssel im Passwortmanager, neuere Schemata abgelehnt, Schutz vor dem Überschreiben einer neueren Sicherung (E-B2b-5). | – |

**Privat:** Nach einem Wiederherstellen von Kevins Daten auf einem anderen Gerät (Emulator)
macht Claude dort keine Bildschirmfotos und keine UI-Abzüge; geprüft wird über Zahlen
(Diagnose-Log, `dumpsys`).

## Einmalige Einrichtung (erledigt)
- GCP-Projekt `avlreminder-exp-1ef2`, *Google Auth Platform → Clients*: Web-Client und
  Android-Client (Paket `com.avlreminder`, SHA-1 des Debug-Schlüssels aus
  `keytool -list -v -keystore ~/.android/debug.keystore`). Ein neuer PC mit anderem
  `debug.keystore` braucht einen weiteren Android-Client.
- Deploy-Key der CI (in einem eigenen Terminal, der private Schlüssel gehört nicht in den
  Gesprächsverlauf):
  ```bash
  ssh-keygen -t ed25519 -N "" -C "avlreminder-ci" -f avlreminder-ci
  gh repo deploy-key add avlreminder-ci.pub -R FairPlayer4/avlreminder-backend -t "avlreminder-ci (App-CI, nur lesen)"
  gh secret set BACKEND_DEPLOY_KEY -R FairPlayer4/avlreminder < avlreminder-ci
  rm avlreminder-ci avlreminder-ci.pub
  ```
