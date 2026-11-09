# Entwurf: Cloud-Backup Schritt 5 – Prüfen, Wiederherstellen, Löschen

> Stand: 2026-11-09, **bestätigt** (Kevin, 2026-11-09, Antworten unten), umgesetzt in
> App-Schritt 5 (Abweichungen unter *Umsetzung*). Gehört zu
> [backup-entwurf.md](backup-entwurf.md) (Schritt 5, A-11, A-14). Kevins Vorgaben vom
> 2026-11-08: kein Risiko für die echten Daten, vorher eine manuelle Sicherung, auf dem
> S24 nicht neu installieren, und auf dem Gerät prüfen können, dass die Sicherung den
> Daten des S24 entspricht.

## Kurzfassung
1. **Zuerst „Sicherung prüfen“** (5a): Die App lädt die Sicherung, entschlüsselt sie in
   eine **eigene Datei** und vergleicht sie mit der laufenden Datenbank, Tabelle für
   Tabelle. Dabei wird nichts ersetzt. Das ist der risikofreie Test auf dem S24 und
   zugleich der Beweis, dass Sichern und Entschlüsseln wirklich funktionieren.
2. **Wiederherstellen** (5b) nutzt dieselbe Datei als Zwischenstufe: erst prüfen, dann
   vorzeigen und bestätigen lassen, dann beim nächsten Start der App tauschen. Die alte
   Datenbank bleibt als Kopie liegen, und *Rückgängig* bringt sie zurück.
3. **Gerätetest des Wiederherstellens** auf einem Emulator; auf dem S24 nur *Prüfen* und
   ein Probelauf ohne Tausch.
4. **Löschen** und *Zurücksetzen* werden entschärft, weil sie auf dem Server **alle**
   Fassungen löschen (siehe R-3).

## Feedback zur Idee „mehrere Datenbanken zum Wechseln“
Der Kern der Idee ist richtig: **nie direkt überschreiben, sondern daneben aufbauen und
vergleichen.** Das übernimmt der Entwurf. Ein echtes Umschalten zwischen Datenbanken
während des Betriebs empfehle ich aber nicht:

- **Alarme gehören zur aktiven Datenbank.** Die App hat rund 165 Alarme beim
  AlarmManager, dazu Geofences, die Übersicht-Benachrichtigung und Widgets. Bei jedem
  Umschalten müssten alle Alarme der einen Datenbank gestrichen und die der anderen
  geplant werden. Wer versehentlich auf die falsche umschaltet, bekommt keine Erinnerungen
  mehr, und zwar ohne Warnung. Das wäre genau der Datenverlust, den wir vermeiden wollen,
  nur zeitlich verschoben.
- **Die App hält eine Datenbank für die ganze Laufzeit** (`AppGraph.db`). Alarm-Empfänger,
  Worker, Widgets, Claude-Zugriff und Oberfläche lesen sie direkt. Das umzubauen kostet
  mehrere Tage und viele neue Fehlerquellen.
- **Konflikte (B3) brauchen Zusammenführen pro Datensatz**, nicht das Wählen einer ganzen
  Datei. Eine zweite Datenbank ist dafür als *Eingabe* nützlich („der andere Stand“), und
  das leistet die nur lesende Prüfdatei aus 5a.
- **Geteilte Reminder** gehören in dieselbe Datenbank, mit einer Spalte für den Besitzer
  bzw. den geteilten Bereich. Alarme, Übersicht und Suche müssen eigene und geteilte
  zusammen zeigen; über getrennte Dateien müsste jede Abfrage beide verbinden.

**Empfehlung:** eine *zweite Datenbank nur zum Lesen* (Prüfdatei) statt Umschalten. Sie
bringt den Testnutzen der Idee ohne das Alarm-Risiko. Getauscht wird nur beim Start der
App, bevor irgendetwas die Datenbank öffnet.

## Entscheidungen (Empfehlungen)

| # | Frage | Empfehlung | Warum |
|---|---|---|---|
| W-1 | Wie prüfen, ob die Sicherung den Daten entspricht? | *Sicherung prüfen* in den Einstellungen: Sicherung holen, entschlüsseln, entpacken nach `filesDir/restore/pruefung.db`, mit SQLite (nicht Room) nur lesend öffnen und `PRAGMA integrity_check` ausführen. Dann jede Tabelle der Sicherung mit der laufenden Datenbank vergleichen: Anzahl der Zeilen und ein SHA-256 über alle Zeilen, sortiert nach Primärschlüssel. Die Einstellungen werden als JSON mit sortierten Schlüsseln verglichen. Danach wird die Datei gelöscht. | Ein Vergleich der Bytes allein (Hash wie in Schritt 4) zeigte nur „gleich“ oder „anders“. Der Vergleich je Tabelle sagt, *was* abweicht, ohne Inhalte anzuzeigen. |
| W-2 | Was zeigt das Ergebnis? | „Sicherung vom <Zeit> entspricht den Daten auf diesem Gerät“ oder je Tabelle „Reminder: 2 abweichend, 1 nur hier“ usw. Hat sich seit der letzten Sicherung etwas geändert (Hash ungleich), steht davor „Seit der Sicherung geändert – Abweichungen sind zu erwarten“, mit dem Knopf *Jetzt sichern und erneut prüfen*. | So prüft Kevin auf dem S24 selbst. Gleich heißt: Sichern, Verschlüsseln, Server und Entschlüsseln haben alles verlustfrei übertragen. |
| W-3 | Logger | Ereignis `sicherung/geprueft` mit `gleich` (Wahrheitswert), `tabellen` und `tabellen_abweichend` (Zahlen), `schema` (Zahl), `dauer_ms`. Keine Tabellen- oder Spaltennamen als Text, sondern eine feste Aufzählung der Tabellen. | Dann kann Claude das Ergebnis im Diagnose-Log bestätigen, ohne Inhalte zu sehen. |
| W-4 | Ältere Sicherung als die App (Schema kleiner) | Die Prüfdatei wird mit Room **in der Datei selbst** migriert, nie die laufende Datenbank. Neueres Schema: ablehnen (`NeueresSchema`), wie in A-11. | Die Migration ist der riskanteste Teil; sie läuft so auf einer Kopie, und ein Fehler lässt die echten Daten unberührt. |
| W-5 | Ablauf Wiederherstellen | (1) wie W-1 holen und prüfen, mit Room öffnen (prüft das Schema) und `PRAGMA foreign_key_check`; (2) Übersicht zeigen: „Sicherung vom … von Gerät …, 42 Reminder (hier: 40)“, Rückfrage mit zwei Tipps wie beim Schlüssel; (3) Datei als `restore/bereit.db` ablegen und Merker `restore/auftrag.json` schreiben; (4) App-Prozess neu starten; (5) in `AvlDatabase.create`, **bevor Room öffnet**: laufende Datenbank per `VACUUM INTO` nach `backup/vor-wiederherstellung-<Zeit>.db` kopieren, `bereit.db` per Umbenennen an ihre Stelle setzen, `-wal` und `-shm` löschen, Einstellungen übernehmen (ohne *Notaus* und Schnittstelle), Merker löschen; (6) der normale Start plant alle Alarme neu. | Umbenennen ist auf demselben Dateisystem atomar (`filesDir` und `databases` liegen beide im App-Ordner). Über den Merker setzt ein Absturz an jeder Stelle beim nächsten Start sauber fort oder bricht ab (Tests für jeden Schritt). |
| W-6 | Rückgängig | Nach einem Wiederherstellen steht 7 Tage lang *Wiederherstellen rückgängig machen* in den Einstellungen. Es setzt `vor-wiederherstellung-*.db` mit demselben Ablauf (Merker, Neustart) wieder ein, ohne Netz. | Ein Fehlgriff ist dann ohne Cloud rückgängig zu machen. |
| W-7 | Was passiert danach mit dem Takt? | Nach dem Wiederherstellen ist die Sicherung auf dem Server „von einem anderen Gerät“ (wenn sie von dort stammt); das automatische Sichern startet erst nach einem Sichern von Hand, das wie in Schritt 3 fragt. Auf dem Gerät, von dem die Sicherung stammt, ändert sich nichts. | Verhindert, dass ein frisch wiederhergestelltes Testgerät (Emulator) die Sicherung des S24 überschreibt. |
| W-8 | *Sicherung löschen* | Löscht nur die Daten-Sicherung (alle Fassungen, so baut es der Server), nicht das Schlüssel-Backup; der Takt stoppt. Rückfrage mit Datum der Sicherung und dem Satz „Auch die älteren Fassungen der letzten 7 Tage werden gelöscht“. | DSGVO Art. 17. Der Schlüssel bleibt, damit ein späteres Sichern ohne neue Einrichtung geht. |

## Risiko von Datenverlust verringern

### R-1 Lokale Schnappschüsse (Realbetrieb, empfohlen, klein)
Täglich ein `VACUUM INTO` nach `filesDir/backup/taeglich-<Datum>.db`, behalten: 7 tägliche
und 4 wöchentliche. Unabhängig von Cloud, Anmeldung und Netz. Wiederherstellen daraus
nutzt denselben Ablauf wie W-5 (ohne Download). Schützt vor dem häufigsten Fall: Ein
Fehler in der App oder ein Fehlgriff löscht Daten, und man merkt es erst nach Tagen. Größe
klären (morgen auf dem Gerät): Bei wenigen MB sind 11 Kopien unbedenklich.

### R-2 Bremse beim automatischen Sichern (Realbetrieb, empfohlen, klein)
Die Cloud schützt bisher nicht vor dem Fehler „die App verliert Daten und sichert den
Verlust stündlich“. Der Server hält zwar 7 Tage ältere Fassungen (Backend E-B2b-4), aber
die App kann sie noch nicht holen. Daher: Sinkt die Zahl der Reminder (oder einer anderen
Haupttabelle) gegenüber der letzten Sicherung um mehr als die Hälfte bzw. um mehr als 10,
lädt das automatische Sichern **nicht** hoch, sondern ruht wie bei A-10 und meldet
„Cloud-Backup pausiert: viele Daten fehlen“. *Jetzt sichern* fragt dann ausdrücklich nach.
Die Zahlen merkt sich die App beim Sichern im DataStore `backend` (nur Zählungen).

### R-3 *Zurücksetzen* entschärfen (empfohlen, vor dem Emulator-Test)
*Cloud-Konto zurücksetzen* löscht heute auf dem Server Sicherung **mit allen älteren
Fassungen** und das Schlüssel-Backup (kein Soft Delete, Backend `infra/datensicherung.tf`).
Tippt man das auf dem Emulator, ist die Cloud-Sicherung des S24 endgültig weg. Vorschlag:
- *Dieses Gerät abmelden*: nur das Gerät sperren und lokal Schlüssel und Sitzung löschen,
  Server-Daten bleiben. Das ist der normale Weg für Testgeräte.
- *Cloud-Daten löschen*: wie heute, aber nur mit Eingabe des Wortes „löschen“ und dem
  Hinweis, dass es nicht rückgängig zu machen ist.

### R-4 Ältere Fassung holen (später, Backend)
Ein Aufruf „Fassungen auflisten / Fassung holen“ im Backend, damit die App eine Sicherung
von vor einigen Tagen prüfen (W-1) und wiederherstellen kann. Kostet fast nichts, ist aber
Backend-Arbeit (Proto, Server, Client). Bis dahin kann Kevin eine ältere Fassung über die
GCP-Konsole holen; sie ist verschlüsselt und nur mit dem Wiederherstellungsschlüssel
lesbar. Empfehlung: nach Schritt 5, vor der Sicherheitsphase.

### R-5 Manuelle Sicherung vor dem Test (morgen, einmalig)
Die Debug-App erlaubt `run-as`; damit holt Claude die Datenbank auf den PC, ohne die App
zu beenden (ein *Beenden erzwingen* würde alle Alarme löschen):
1. `databases/avlreminder.db`, `-wal`, `-shm`, `files/datastore/settings.preferences_pb`
   und `files/backup/` per `adb exec-out run-as com.avlreminder cat …` nach
   `E:\avl-sicherung\2026-11-09\` (außerhalb beider Repos, nie committen).
2. Zweimal holen und die SHA-256 vergleichen; gleich heißt: Während des Kopierens hat die
   App nichts geschrieben.
3. Auf dem PC eine Kopie öffnen und nur `PRAGMA integrity_check` und die Zeilenzahlen je
   Tabelle ausgeben. Claude liest **keine Inhalte** (Backend-`CLAUDE.md`).
4. Kevin kopiert den Ordner zusätzlich an einen zweiten Ort (USB-Stick oder Cloud-Laufwerk).

Zurückspielen ginge im Notfall genauso mit `run-as`, solange die App nicht läuft; das
ist nur für den Notfall, nicht zum Testen gedacht.

### R-6 Schon vorhanden
Kopie vor jeder Migration (`backup/avlreminder-v<n>-<Zeit>.db`), 7 Tage ältere Fassungen
auf dem Server, das Schlüssel-Backup im Passwortmanager, Ablehnen neuerer Schemata,
Schutz vor dem Überschreiben einer neueren Sicherung (E-B2b-5).

## Gerätetest

**Auf dem S24 (ohne Risiko):**
1. Vorher R-5.
2. *Jetzt sichern*, dann *Sicherung prüfen* → „entspricht den Daten auf diesem Gerät“.
3. Einen Reminder abhaken, *Sicherung prüfen* → zeigt „seit der Sicherung geändert“ und
   genau eine abweichende Tabelle (Vorkommen). *Jetzt sichern und erneut prüfen* → gleich.
4. Probelauf: *Wiederherstellen* bis zur Übersicht (W-5, Schritt 2), dort *Abbrechen*. Es
   wird nichts getauscht; die Zahlen in der Übersicht stimmen.
5. Alarme vorher und nachher zählen (`dumpsys alarm`), sie bleiben gleich.

**Auf dem Emulator (das echte Wiederherstellen):**
- Android Studio, ein Gerät mit *Google Play* (für die Anmeldung mit Google). Die App aus
  demselben PC hat denselben Debug-Schlüssel, also passt der vorhandene Android-Client.
- Anmelden mit Kevins Konto, Wiederherstellungsschlüssel aus dem Passwortmanager (oder
  eintippen), *Wiederherstellen* → Neustart → *Sicherung prüfen* zeigt „entspricht“. Damit
  ist bewiesen, dass die Daten auf einem fremden Gerät gleich ankommen.
- *Rückgängig* testen: auf dem Emulator vorher einen Testreminder anlegen, wiederherstellen,
  rückgängig machen, der Testreminder ist wieder da.
- Danach auf dem Emulator **nur** *Dieses Gerät abmelden* (R-3), nie *Cloud-Daten löschen*.
  Die Sicherung des S24 bleibt dabei unverändert; das prüft *Sicherung prüfen* auf dem S24.
- **Claude macht auf dem Emulator nach dem Wiederherstellen keine Bildschirmfotos und keine
  UI-Abzüge**, denn dort stehen dann Kevins echte Titel. Claude prüft nur über Zahlen
  (Diagnose-Log, `dumpsys`); was man sehen muss, schaut Kevin an.

**Tests (Robolectric):** Abbruch an jeder Stelle von W-5 (vor und nach dem Umbenennen,
Merker ohne Datei, Datei ohne Merker), beschädigte Sicherung, neueres Schema, Migration
der Prüfdatei lässt die laufende Datenbank unberührt, Vergleich findet eine geänderte
Zeile, eine fehlende und eine zusätzliche, Bremse R-2 mit Ablehnungsfall (wenige
gelöschte Reminder sichern weiter), *Rückgängig*, Löschen ohne Schlüssel-Backup.
Mutationsproben mit Gegenprobe.

## Reihenfolge und Aufwand
1. R-5 manuelle Sicherung (morgen, 15 Minuten).
2. 5a *Sicherung prüfen* (W-1 bis W-4) und R-3, Gerätetest auf dem S24 – etwa ein Tag.
3. R-1 und R-2 – ein halber Tag.
4. 5b *Wiederherstellen* und *Rückgängig* (W-5 bis W-7), *Löschen* (W-8), Emulator-Test –
   ein bis zwei Tage.
5. Später R-4 im Backend.

## Antworten (Kevin, 2026-11-09)
1. Prüfen zuerst, kein Umschalten zwischen Datenbanken: ja.
2. R-3 aufteilen in *Dieses Gerät abmelden* und *Cloud-Daten löschen*: ja.
3. R-2 mit „mehr als die Hälfte oder mehr als 10 Reminder weniger“: passt.
4. R-1 mit 7 täglichen und 4 wöchentlichen Kopien: ja.
5. Emulator-Abbild mit Google Play laden: ja.

Die übrigen Empfehlungen gelten wie oben.

## Umsetzung (2026-11-09)
Pakete `restore` (Dateien, Tausch, Vergleich, lokale Kopien) und `cloud` (Prüfen,
Wiederherstellen, Bremse, Löschen, Abmelden). Abweichungen und Ergänzungen zum Entwurf:
- **W-1/W-4:** Die Prüfdatei wird gleich mit Room geöffnet (migriert und prüft das Schema),
  dann `integrity_check` und `foreign_key_check`. Verglichen wird je Zeile ein SHA-256 über
  alle Spalten (nach Namen sortiert, mit Typ, sodass `NULL`, `''` und `0` verschieden sind),
  zugeordnet über den Primärschlüssel `id`. So zählt das Ergebnis *geändert*, *nur hier* und
  *nur in der Sicherung* je Tabelle. Die Einstellungen werden über ihre Werte verglichen.
- **W-5 (5):** Getauscht wird in `AppGraph`, bevor `AvlDatabase.create` die Datenbank öffnet
  (`DatabaseSwap.applyPending`); die Einstellungen übernimmt der Start danach
  (`finishRestore`). Neustart: Start-Activity mit neuem Task, dann `exit(0)`. Ein Beenden
  des Prozesses löscht keine Alarme, nur *Beenden erzwingen*; der Start plant sie neu.
- **Fehlgeschlagener Tausch:** `restore/fehlgeschlagen.json` mit dem Grund (feste Liste).
  Die Daten bleiben wie sie sind, die Einstellungen zeigen es, und das automatische Sichern
  wartet, bis es bestätigt ist. *Jetzt sichern* bleibt möglich.
- **Rückfrage im Backend:** `Sicherungsablauf.wiederherstellen` gibt der Rückfrage die ganze
  entschlüsselte Sicherung (Backend `6d04e02`), damit die App vor dem Bestätigen prüfen
  und vergleichen kann.
- **W-7:** Nach dem Wiederherstellen von einem anderen Gerät merkt sich die App die
  Server-Fassung 0 und vergisst die letzte Sicherung; das automatische Sichern ruht, und
  *Jetzt sichern* fragt vor dem Überschreiben.
- **R-1:** `SnapshotWorker` täglich und beim App-Start, Abschnitt *Lokale Kopien* in den
  Einstellungen. Eine tägliche Kopie enthält nur die Datenbank; beim Wiederherstellen daraus
  bleiben die Einstellungen. *Rückgängig* nimmt die neueste Kopie vor einem Wiederherstellen
  mit ihren Einstellungen.
- **R-2:** gezählt werden nur Reminder (Löschen eines Reminders löscht seine Termine mit).
  Die Bremse hält das automatische Sichern an, bis von Hand gesichert wird
  (Benachrichtigung „Automatisches Sichern pausiert“); *Jetzt sichern* zeigt die Zahlen.
- **R-3:** *Cloud-Daten löschen* braucht das eingetippte Wort „löschen“. *Dieses Gerät
  abmelden* löscht nichts auf dem Server; ist er nicht erreichbar, wird das Handy trotzdem
  abgemeldet (ohne Schlüssel kann es dort nichts mehr).
- **Gefunden im Emulator (2026-11-09):** Die Tageskopie beim Start entstand erst, nachdem der
  Status geladen war; die Einstellungen zeigten bis zum nächsten Start „Noch keine Kopie“.
  `LocalRestore.takeSnapshot` legt sie jetzt an und zeigt sie sofort (auch aus dem Worker).
- **Gefunden beim Testen:** `VACUUM INTO` auf der laufenden Datenbank öffnete sie ohne WAL
  und schaltete so ihren Journalmodus um (die Datei änderte sich, die Daten nicht). Beide
  Kopien (vor dem Tausch und vor einer Migration) öffnen sie jetzt im WAL-Modus wie Room.
- **Logger:** `sicherung/geprueft`, `sicherung/gebremst`, `sicherung/geloescht`,
  `cloud/abgemeldet`, `wiederherstellen/bereit`, `.../getauscht`, `.../fehlgeschlagen`; nur
  Zahlen, Wahrheitswerte und feste Namen.
