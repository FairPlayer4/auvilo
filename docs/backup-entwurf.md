# Entwurf: Cloud-Backup in der App (Backend B2b, Schritt 7)

> Stand: 2026-11-07, von Kevin **bestätigt** am 2026-11-07 (anforderungen.md Kap. 9). Grundlage: Backend `../avlreminder-backend`,
> `docs/b2b-entwurf.md` (E-B2b-1 bis E-B2b-13), `docs/formate.md`,
> `docs/diagnose-entwurf.md`; hier Anf. 7.28 (PS8, PS8a, PS9) und 8.4.
> Das Backend ist fertig und in der Cloud getestet; der Kotlin-Client (`client/`
> dort) bringt alle Abläufe mit. Die App baut nur noch die Android-Seite.

## Ziel
Kevins echte Daten werden Ende-zu-Ende verschlüsselt in der Cloud gesichert und
lassen sich auf einem neuen Gerät (oder nach Neuinstallation) zurückholen. Das
Backend befindet sich im **Eigenbetrieb** (entschieden 2026-11-07 im Backend): Bis
jemand außer Kevin die App gegen die Cloud nutzt, darf Claude die Schlüssel kennen;
vor dem ersten weiteren Nutzer wird alles zurückgesetzt und neu eingerichtet.

Weiterhin gilt (backend.md): Die App funktioniert ohne Backend vollständig, das
Backup darf scheitern, der Export als Datei und das Migrations-Backup bleiben.

**Nicht** in diesem Schritt: Synchronisation (B3), Fotos und Audio, Play Integrity,
Play-Store-Variante.

## Was es schon gibt (Backend, `client/`)
| Modul | Liefert | Die App braucht dazu |
|---|---|---|
| `protokoll` | `Einrichtung` (Schlüssel neu oder aus dem Backup), `Sicherungsablauf` (sichern, wiederherstellen, löschen, Schutz vor älteren Fassungen, ein sicherndes Gerät), `Diagnose`, `Geraetenachrichten` (was signiert wird) | `LokaleAblage` (Keysets), `SicherungsStand` (Zähler), Signieren mit dem Geräteschlüssel |
| `verbindung` | Connect-Clients, Adapter `ServerBackupAblage`, `ServerSicherungsAblage`, `ServerGeraete`, `ServerDiagnoseAblage`, Fehler-Übersetzung (auch „Kill-Switch hat gesperrt“) | Anmelden und Sitzung erneuern (heute im Test-Client, wandert nach `verbindung`) |

## Entscheidungen (Empfehlungen)

| # | Frage | Empfehlung | Warum |
|---|---|---|---|
| A-1 | Eigene App-Variante? | **Nein, vorerst nicht.** Das Backup kommt in den normalen Build hinter den Schalter aus PS8 (aus = keine Anmeldung, kein Netz). Eine eigene Variante bzw. der interne Test-Track kommt erst mit dem ersten Tester. | Eine eigene Variante hätte eine eigene Datenbank; gesichert würden dann nicht Kevins echte Daten. Das widerspricht E-B2b-11 im Backend nur in der Reihenfolge; dort nachtragen. |
| A-2 | Einbindung von `client/` | **Gradle Composite Build** (`includeBuild("../avlreminder-backend/client")`), die App hängt von `verbindung` ab (bringt `protokoll` mit). In der CI holt ein **Deploy-Key** (nur lesen, nur Backend-Repo) das Backend-Repo auf einen **festen Commit**, der in `gradle/backend-commit.txt` steht. | Wie in E-B2b-10 entschieden. Ohne festen Commit baute die CI mit einem anderen Stand als lokal. Ein Deploy-Key gilt nur für ein Repo und läuft nicht ab (anders als ein Token). **Ergänzt 2026-11-07:** Lokal baut Gradle, was im Nachbarordner liegt. Weicht er vom festgehaltenen Commit ab oder hat er uncommittete Änderungen in `client/` oder `proto/`, warnt der Build („diese APK nicht installieren“); mit `-PbackendStreng=true` (CI) bricht er ab. Langfristig: neu entscheiden mit der Sicherheitsphase (Monorepo oder veröffentlichte Versionen). |
| A-3 | Netz | Neue Berechtigung **`INTERNET`** (normal, ohne Rückfrage). Anf. 8.4 wird präzisiert: Daten verlassen das Gerät nur Ende-zu-Ende verschlüsselt im Backup, und nur, wenn es eingeschaltet ist. | Die App hat bisher gar kein Netz. Die Präzisierung steht für Orte und WLANs schon in Kap. 9 (2026-11-05). |
| A-4 | Anmeldung | **Credential Manager** mit „Mit Google anmelden“ (`GetSignInWithGoogleOption`), `serverClientId` = Web-Client des Backend-Projekts, `nonce` = `Geraetenachrichten.nonce(Geräteschlüssel)`. Kevin legt dafür im GCP-Projekt einen **Android-Client** (Paket `com.avlreminder`, SHA-1 des Debug-Schlüssels) und einen **Web-Client** an; die Web-Client-ID kommt in `google_client_ids` (OpenTofu, kostenlos). | Der Server prüft `aud` gegen `GOOGLE_CLIENT_IDS`; bei Android ist `aud` der Web-Client. Die Nonce bindet das ID-Token an den Geräteschlüssel (wie im Test-Client). |
| A-5 | Geräteschlüssel | ECDSA P-256 im **Android Keystore**, nicht exportierbar, StrongBox wenn vorhanden, signiert `SHA256withECDSA`. Geht er verloren (App-Daten gelöscht), meldet sich die App als neues Gerät an. | Wie im Backend vorgesehen (`Geraetenachrichten`). Der private Schlüssel verlässt das Gerät nie, auch Claude sieht ihn nicht. |
| A-6 | Personen- und Tresorschlüssel (Keysets) | Tink-Keysets **verschlüsselt mit einem AES-GCM-Schlüssel aus dem Android Keystore** (Tink `AndroidKeystore`), Datei in `filesDir/backend/`. Umsetzung von `LokaleAblage`. | Die Keysets brauchen ihren Klartext zum Ver- und Entschlüsseln; der Keystore schützt sie auch gegen Kopieren der Datei. Nach Neuinstallation sind sie weg; dann hilft der Wiederherstellungsschlüssel (gewollt). |
| A-7 | Sitzung und Zähler | Sitzungs-Token und `GemerkteSicherung` in einem eigenen DataStore `backend` (nicht in den Einstellungen, damit sie nie ins Backup geraten). Abgelaufene Sitzung: vor dem Aufruf erneuern (signiert). | Sandbox reicht für das Token (PS9); es läuft ohnehin ab. Eigener Speicher, weil die Einstellungen selbst gesichert werden (PS8a). |
| A-8 | Nutzlast | **ZIP** mit zwei Einträgen: `avlreminder.db` (Kopie per `VACUUM INTO`) und `einstellungen.json` (`Settings.backup()`). `schemaVersion` = `AvlDatabase.VERSION`. Zuerst prüft die App die Größe (8 MiB Chiffrat). | ZIP komprimiert (Deflate) und fasst beides zusammen, ohne eigenes Format; `java.util.zip` ist dabei. Das Backend sieht nur Bytes (E-B2b-1). |
| A-9 | Wann sichern? | **WorkManager**, periodisch **stündlich**, nur mit Netz, nur wenn sich die Nutzlast geändert hat (SHA-256 der unkomprimierten Datenbank und Einstellungen, im DataStore `backend` gemerkt). Dazu *Jetzt sichern* von Hand. | Stündlich ist im Backend entschieden (2026-11-04; backend.md nennt noch 15 min, wird korrigiert). Über den Hash entfällt ein eigener Änderungszähler in der Datenbank. |
| A-10 | Zwei Geräte | Ablauf aus `protokoll` unverändert: automatisch nur, wenn die letzte Sicherung von diesem Gerät stammt; sonst eine Benachrichtigung „Ein anderes Gerät sichert – übernehmen?“; bei „nein“ ruht das automatische Sichern. | Schon im Backend so entschieden (E-B2b-6, 2026-11-07). |
| A-11 | Wiederherstellen | Nur auf Wunsch, mit Anzeige „Sicherung vom … von Gerät …“ und Rückfrage. Davor eine **lokale Kopie** der aktuellen Datenbank (`filesDir/backup/vor-wiederherstellung-<Zeit>.db`, wie das Migrations-Backup). Dann Datenbank schließen, Datei ersetzen, Einstellungen ohne *Notaus* und Schnittstelle übernehmen, App-Prozess neu starten; Room migriert, der Start plant alle Alarme neu. Neueres Schema als die App kennt: ablehnen (`NeueresSchema`). | Ersetzen statt Zusammenführen, weil es ohne Sync nur ein sicherndes Gerät gibt. Die lokale Kopie macht einen Fehlgriff rückgängig. |
| A-12 | Diagnose senden | **Eigener kleiner Logger fürs Backend** (Ereignisname, typisierte Felder, keine freien Texte; E-D-1), nicht `diag.log`. Knopf *Diagnose senden*. | `diag.log` enthält Titel (design.md 13, Ausnahme der Entwicklungsphase). Die Backend-Regel E-D-1 erlaubt keine Inhalte; Claude darf Sendungen nur deshalb lesen. |
| A-13 | Fehler | Netz weg, Server gesperrt (Kill-Switch), Tageslimit: nichts tun außer Status anzeigen; WorkManager versucht es zur nächsten Stunde. Keine Benachrichtigung, außer das letzte Backup ist älter als 3 Tage. | „Kosten vor Verfügbarkeit“: Ausfälle sind normal und dürfen nicht nerven. Nach 3 Tagen sollte Kevin es aber merken. |
| A-14 | Oberfläche | Neuer Bereich in den Einstellungen **„Cloud-Backup (experimentell)“**: Einschalten (mit kurzer Erklärung), Anmelden, Einrichtung (neuer Wiederherstellungsschlüssel anzeigen und Bestätigung „aufgeschrieben“ bzw. Eingabe des vorhandenen), Status „letzte Sicherung“, *Jetzt sichern*, *Wiederherstellen*, *Sicherung löschen*, *Diagnose senden*, *Ausschalten*. Dazu (2026-11-08, gebaut) **Cloud-Konto zurücksetzen** mit Rückfrage: auf dem Server Sicherung und Schlüssel-Backup löschen und dieses Gerät sperren, auf dem Handy Schlüsseldatei, Sitzung und Keystore-Schlüssel löschen; Reminder bleiben. Ist der Server nur nicht erreichbar, löscht es auch auf dem Handy nichts; darf das Gerät dort nichts mehr (gesperrt, nicht freigeschaltet), setzt es das Handy trotzdem zurück und sagt es. | Alles an einer Stelle; PS6 (Transparenz). |
| A-15 | Schlüssel sichern | **Gebaut am 2026-11-08** (`cloud/PasswordSafe.kt`): Neuer Wiederherstellungsschlüssel → sofort Speicher-Dialog des Passwortdienstes (Credential Manager, `CreatePasswordRequest`); bei Abfrage zuerst `GetPasswordOption`, Eintippen als Ausweichweg (Anf. 7.33). Hinter einer Schnittstelle wie `GoogleSignIn`, damit die Tests einen Nachbau nutzen. Gespeichert genügt *Fertig*, sonst *Aufgeschrieben* mit Rückfrage; *Im Passwortmanager speichern* und *Aus dem Passwortmanager holen* als Knöpfe. Der Passwortdienst wird von selbst nur einmal gefragt; ein falscher Schlüssel von dort führt zur Eingabe. Eintrag „avlreminder Wiederherstellung <Konto-Anfang>“. | Abtippen eines langen Codes ist fehleranfällig und lästig (erster Gerätetest). Der Passwortdienst ist auf dem Handy ohnehin da. |

## Ablauf Einrichtung (erstes Einschalten)
1. Schalter an → Geräteschlüssel im Keystore anlegen.
2. Credential Manager → ID-Token mit Nonce → `Anmelden` (signiert) → Sitzung, Konto, Gerät.
3. `Einrichtung` aus `protokoll`: Gibt es kein Schlüssel-Backup, entstehen neue Schlüssel,
   die App zeigt den Wiederherstellungsschlüssel und lädt erst nach „aufgeschrieben“ hoch.
   Gibt es eins, fragt sie nach dem Wiederherstellungsschlüssel (höchstens drei Versuche).
4. Erste Sicherung von Hand, danach der stündliche Takt.

Kevins Konto im Backend ist heute mit Test-Schlüsseln belegt (Test-Client, Gerät `r-pc`).
Vor der Einrichtung in der App räumt Claude das mit dem Test-Client ab (Sicherung und
Schlüssel-Backup löschen, Testgeräte sperren); dann richtet die App **neue** Schlüssel ein.

## Schritte (jeweils bauen, Tests grün, installieren nach Kevins Go)
1. **Bauen ohne Verhalten** – umgesetzt am 2026-11-07 (Branch `feature/backup`):
   Anmelden und Erneuern liegen in `verbindung` (`Anmeldung`, mit Tests), Composite Build
   mit Prüfung des Backend-Commits, CI mit zweitem Checkout per Deploy-Key, `INTERNET`.
   Nichts sichtbar. App und `core` bauen für Java 21 wie `client/`. Gefunden dabei: Beide
   Backend-Module erzeugten alle Protobuf-Klassen (D8: „defined multiple times“); im
   Backend behoben, `ModulgrenzeTest` hält es fest.
2. **Anmelden und Einrichten:** Keystore, Credential Manager, `LokaleAblage`, Bereich in den
   Einstellungen bis „eingerichtet“. Voraussetzung: Kevins OAuth-Clients (siehe unten).
   Gerätetest: anmelden, Wiederherstellungsschlüssel, Fingerabdruck stimmt mit Server.
   **Gebaut am 2026-11-07** (Paket `cloud`, noch kein Gerätetest):
   - `KeystoreDeviceKey` (A-5, StrongBox wenn vorhanden), `KeyFile` mit `KeystoreSealer`
     (A-6: Tink `AndroidKeystore`, AES-256-GCM; das Konto ist Teil der zugehörigen Daten),
     `CloudStore` (A-7: DataStore `backend` mit Schalter, Sitzung, freigeschaltet),
     `CredentialManagerSignIn` (A-4), `CloudBackup` (Zustände für die Oberfläche, immer nur
     ein Schritt zur Zeit; Fehler werden zu Text, nie zum Absturz).
   - Ausgeschaltet entsteht weder ein Schlüssel noch eine Verbindung (Test mit Mutationsprobe).
     Ausschalten behält Schlüssel und Sitzung; wieder eingeschaltet ist das Gerät ohne Netz
     sofort „eingerichtet“.
   - Wiederherstellungsschlüssel: Bestätigen braucht zwei Tipps („Aufgeschrieben“, „Ja“);
     solange er angezeigt oder eingegeben wird, sind Bildschirmfotos gesperrt
     (`FLAG_SECURE`). Er steht nie im Log.
   - Im Backend dafür: `SitzungsAblage` und die Rückfragen der `Einrichtung` sind `suspend`
     (die Oberfläche wartet auf die Eingabe, ohne einen Thread zu blockieren).
   - Die Web-Client-ID steht in `google_client_ids` (Backend `infra/variablen.tf`), ist aber
     **noch nicht angewendet**.
3. **Sichern von Hand und Status**, Backend-Logger, *Diagnose senden*. Plan (2026-11-08),
   umgesetzt am 2026-11-08 (`BackupPayload.kt`, `BackupStateFile.kt`, `CloudLog.kt`):
   - *Jetzt sichern* (nur eingerichtet und freigeschaltet): Nutzlast nach A-8 als ZIP mit
     `avlreminder.db` (`VACUUM INTO` in den Cache, danach gelöscht) und `einstellungen.json`;
     Einträge mit fester Zeit, damit gleiche Daten gleiche Bytes ergeben (Hash in Schritt 4).
     Dann `Sicherungsablauf.sichern(…, automatisch = false)` aus `protokoll`.
   - `SicherungsStand` als kleine Datei `filesDir/backend/sicherung.json` mit Konto-ID (eine
     fremde Konto-ID gilt als „kein Stand“). Liegt bei den Schlüsseln, damit *Zurücksetzen*
     ihn mitlöscht.
   - Status unter dem Fingerabdruck: „Zuletzt gesichert: <Datum, Uhrzeit>“ bzw. „Noch nicht
     gesichert“, im DataStore `backend` gemerkt; ein Fehler steht wie bisher als Text darunter.
   - Stammt die Sicherung auf dem Server von einem anderen Gerät: Rückfrage „Überschreiben?“
     mit Datum der fremden Sicherung (A-10). Dafür werden die Rückfragen von
     `Sicherungsablauf` im Backend `suspend` (wie bei `Einrichtung`).
   - Backend-Logger `CloudLog` (A-12): feste Ereignisnamen und Felder nur als Zahl,
     Wahrheitswert oder Aufzählung (anderes lehnt er ab), Fehler als Schritt und Art aus
     Aufzählungen, bei Serverfehlern mit Connect-Code; von einer Ausnahme nur Klasse und
     höchstens 20 Stapelzeilen, nie die Meldung; höchstens 500
     Ereignisse in `filesDir/cloud-diag/`. *Diagnose senden* (nur freigeschaltet) schickt Kopf
     und Ereignisse nach formate.md, erst nach einer Rückfrage; `diag.log` bleibt davon
     getrennt. Der Server nimmt sie nur an, wenn die Konto-ID in `diagnose_konten` steht
     (Backend `infra/diagnose.tf`, E-D-3).
4. **Automatisch** (WorkManager, Hash, zweites Gerät, Hinweis nach 3 Tagen). Umgesetzt am
   2026-11-08 (`CloudWorker.kt`, `CloudBackup.backUpAutomatically`):
   - Stündlich (`PeriodicWorkRequest`, eindeutig `cloud-backup`, `KEEP`), nur mit Netz. Der
     Takt startet, sobald das Gerät eingerichtet und freigeschaltet ist und einmal von Hand
     gesichert hat; Ausschalten und *Zurücksetzen* beenden ihn. Der Worker endet immer mit
     Erfolg: Ein Fehler wird zur nächsten Stunde wiederholt, nicht mit dem Backoff von
     WorkManager (A-13).
   - Die Nutzlast wird jede Stunde gebaut; ist ihr SHA-256 gleich dem der letzten Sicherung
     (DataStore `backend`), geht nichts ans Netz.
   - **Ergänzung zu A-10:** Die Benachrichtigung fragt nicht selbst. Trifft das automatische
     Sichern auf die Sicherung eines anderen Geräts, bleibt diese, und das automatische
     Sichern ruht sofort (`GemerkteSicherung.abgelehnt`, wie nach einem „Nein“). Eine
     Benachrichtigung „Cloud-Backup pausiert“ führt in die Einstellungen; dort steht
     „Automatisches Sichern pausiert“, und *Jetzt sichern* fragt wie in Schritt 3. Warum:
     Knöpfe in der Benachrichtigung bräuchten den Entscheid ohne Datum und Gerät vor Augen,
     und bis zur Antwort fragte jede Stunde den Server erneut (Tageslimit).
   - Hinweis nach 3 Tagen (A-13): Gezählt wird ab dem letzten Zeitpunkt, an dem die
     Sicherung die Daten dieses Geräts enthielt (hochgeladen oder unverändert). Er kommt
     einmal und verschwindet mit der nächsten Sicherung. Ohne Netz läuft der Worker nicht;
     dann kommt der Hinweis erst, wenn wieder Netz da ist.
   - Logger: `gesichert` und `anderes_geraet` haben das Feld `automatisch`, neu ist
     `veraltet` (Feld `tage`). Unveränderte Stunden werden nicht geloggt.
   - Gerätetest: nach einer Änderung (z. B. Reminder abhaken) höchstens eine Stunde warten,
     „Zuletzt gesichert“ ist weitergerückt; ohne Änderung bleibt es stehen (im Server-Log
     zu sehen). `adb shell dumpsys jobscheduler | grep -A3 com.avlreminder` zeigt den Job.
5. **Prüfen, Wiederherstellen und Löschen.** Entwurf (2026-11-08, noch nicht bestätigt) in
   [wiederherstellen-entwurf.md](wiederherstellen-entwurf.md): zuerst *Sicherung prüfen*
   ohne Ersetzen, Wiederherstellen über eine Prüfdatei mit Tausch beim Start und
   *Rückgängig*. Gerätetest auf dem Emulator, nicht durch Neuinstallation auf dem S24
   (dort nie deinstallieren).
6. Danach: *Pfeffer offline sichern* im Backend (Kevin), Review-Lauf.

Aufwand grob: Schritt 1 ein halber Tag, 2 ein Tag, 3–5 je ein halber bis ganzer Tag.

## Was Kevin vorab tut
- Im GCP-Projekt `avlreminder-exp-1ef2` unter *Google Auth Platform → Clients* einen
  **Web-Client** und einen **Android-Client** anlegen (Paket `com.avlreminder`, SHA-1 aus
  `keytool -list -v -keystore ~/.android/debug.keystore`, Passwort `android`). Kostenlos.
  Die Client-IDs sind unbedenklich und dürfen in den Chat; ein Client-Secret braucht es nicht.
- **Deploy-Key für die CI** (in einem eigenen Terminal, nicht in Claude Code: der private
  Schlüssel ist ein Zugang und gehört nicht in den Gesprächsverlauf). Im Ordner, in dem
  `gh` angemeldet ist, z. B. dem Home-Verzeichnis:
  ```bash
  ssh-keygen -t ed25519 -N "" -C "avlreminder-ci" -f avlreminder-ci
  gh repo deploy-key add avlreminder-ci.pub -R FairPlayer4/avlreminder-backend -t "avlreminder-ci (App-CI, nur lesen)"
  gh secret set BACKEND_DEPLOY_KEY -R FairPlayer4/avlreminder < avlreminder-ci
  rm avlreminder-ci avlreminder-ci.pub
  ```
  `deploy-key add` ohne `--allow-write` legt einen Schlüssel an, der nur lesen darf.

## Tests
- Wie gewohnt: Robolectric für Worker, Einstellungen und Oberfläche; die Abläufe selbst
  sind im Backend getestet. Keystore und Credential Manager stehen hinter Schnittstellen
  und werden im Test ersetzt; echt geprüft wird auf dem Gerät.
- Ablehnungsfälle unter anderem: Schalter aus → kein Netz, kein Worker; Nutzlast zu groß
  → nichts gesendet; Wiederherstellen mit neuerem Schema → abgelehnt, lokale Daten
  unverändert; Einstellungen aus dem Backup schalten weder *Notaus* noch Schnittstelle;
  Sitzung abgelaufen → erneuern statt Fehler; gesperrter Dienst → nur Status.
- Zip-Format mit einem festen Beispiel (ohne echte Inhalte) als Testvektor, damit spätere
  Versionen alte Sicherungen lesen.

## Bekannte Grenzen
- Fotos und Audio fehlen im Backup; Verweise darauf zeigen nach dem Wiederherstellen ins
  Leere (E-B2b-1). Die App muss das aushalten.
- Debug-Schlüssel signiert die App; ein neuer PC mit anderem `debug.keystore` braucht
  einen weiteren Android-Client.
- Im Eigenbetrieb sind die Schlüssel als bekannt zu betrachten (Backend `CLAUDE.md`).
- Bricht man die Einrichtung nach dem Speichern im Passwortdienst ab, bleibt dort ein
  Eintrag ohne Backup; die nächste Einrichtung legt einen weiteren an (7.33). Löschen
  kann die App ihn nicht, das macht man im Passwortmanager.
