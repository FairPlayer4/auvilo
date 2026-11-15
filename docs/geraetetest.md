# Gerätetest – Kerntests

> Ergänzt die automatischen Tests (`:core` JUnit + Kover, `:app` Robolectric/Compose).
> Hier steht nur, was **nach jedem Update** auf dem echten Gerät geprüft wird. Die
> vollständigen Listen pro Version (Tests 1–96) stehen in
> [geraetetest-archiv.md](geraetetest-archiv.md); die Nummern in Klammern verweisen dorthin.
> Neue Funktionen bekommen beim Release einen eigenen Abschnitt am Ende; was davon
> dauerhaft wichtig ist, wandert danach in die Kerntests, der Rest ins Archiv (zuletzt
> 2026-11-10: V1.3a bis V1.3b0.4 und der Wochenplan V1.3a).

## Vorbereitung
- [ ] Vorige Version mit echten Daten installiert lassen; neue mit `adb install -r`
      darüber installieren, **nie deinstallieren**.
- [ ] Build grün (alle Tests), Kevin hat die Installation freigegeben.

## A. Nach jedem Update (ca. 5 min)

| # | Test | Erwartung |
|---|---|---|
| K1 | Erster Start (20, 85) | Reminder, Verlauf, Nachweise da; bei Schema-Änderung Backup `avlreminder-v<alt>-….db` in `files/backup`; `diag.log` beginnt mit "state app start <version>" |
| K2 | Berechtigungen | kein rotes Banner auf *Heute*; im Diagnose-Log Berechtigungen ok |
| K3 | Alarme neu geplant | `adb shell dumpsys alarm` zeigt die nächsten Fälligkeiten; Widget zeigt die nächste |
| K4 | Reminder *normal*, einmalig in 3 min (2) | Vollbild + Vibration, kein Ton; „+10“ → nach 10 min erneut; danach *Erledigt* → Verlauf „Ziel“ |
| K5 | Darstellung Hell / Dunkel (15) | wirkt sofort; Alarm bleibt dunkel |

## B. Bei Änderungen am jeweiligen Bereich

| # | Bereich | Test | Erwartung |
|---|---|---|---|
| K10 | Eskalation | *streng*, gesperrt, vor und nach `loudFrom` (3, 4, 10) | Vollbild über Sperrbildschirm; Ton erst ab `loudFrom`; Runde 3 min, Pause 10 min |
| K11 | Nicht stören | DND an, dann aus (22, 23, 25) | still, *Heute* zeigt Hinweis; danach nächste Runde normal; mit Ausnahme trotzdem Alarm |
| K12 | Neustart | Handy neu starten bzw. über die Fälligkeit aus (11, 12) | Alarm kommt; Verpasstes wird sofort nachgeholt |
| K13 | Nachweis | Foto, Text, Audio; Nachweis offen lassen bzw. App verlassen (5, 29–33) | Alarm pausiert; nach Ablauf bzw. ca. 1 min wieder da; Prüfungen greifen |
| K14 | Uhr | Reminder *normal* (26) | Aktionen auf der Uhr, verschwinden mit dem Alarm |
| K15 | Ort ohne WLAN, Vorlauf 0 | Reminder mit *Bei Ankunft*, *ab* 0 min; am Tag vor der Uhrzeit heimkommen, an einem anderen Tag zur Uhrzeit weg (42, 44) | Alarm nach der Verweildauer des Geofence; sonst „entfallen“ (Ankunft im Zeitraum: K24–K26) |
| K16 | Checkliste | Punkt abhaken, *Rest auf morgen* (49, 50, 52) | Alarm still; Rest morgen, zählt nicht als Verschieben |
| K17 | Wochenziel | Ziel erreicht, knapp (70, 71) | stille Angebote bzw. eine Stufe strenger |
| K18 | Übersicht, Vorab-Reminder | Übersicht morgens, Vorab 15 min (75, 92, 93) | stille Benachrichtigung; entfällt, wenn schon erledigt |
| K19 | Frist | Fenster, *Auf morgen*, Überfällig (87–89) | Ruhe außerhalb der Fenster; *Überfällig* auf *Heute* |
| K20 | Blocker | *Als Aufgabe planen*, Nachfrage (94, 96) | Aufgabe am Vorabend; Nachfrage ab der Schwelle |
| K21 | Zurücknehmen (7.31) | *Erledigt*, dann innerhalb 1 min *Zurücknehmen* auf *Heute* | wieder offen, klingelt in 10 min; Nachweis zählt wieder |
| K22 | Vorab verschieben (7.32) | im Vorab-Reminder „heute später“ | +30 min/+1/+2 Std ab der geplanten Zeit; zählt aufs Verschieben-Konto |

## C. Cloud-Backup
Auf dem **S24** nur dieses kleine Set (echte Daten, Eigenbetrieb). Alles, was Geräte sperrt,
löscht oder ein zweites Gerät braucht, läuft im **Emulator** mit einem eigenen Testkonto
(Kevin 2026-11-09; Backend-Review Lauf 9 R5).

| # | Wo | Test | Erwartung |
|---|---|---|---|
| C1 | S24 | Nach dem Update *Einstellungen → Cloud* | angemeldet, letzte Sicherung mit Zeit; `diag.log` nur `cloud:`-Zeilen ohne Inhalte |
| C2 | S24 | *Jetzt sichern* | „Gesichert (#n)“; die stündliche Sicherung läuft weiter |
| C3 | S24 | *Sicherung prüfen* | Vergleich je Tabelle, nur Anzahlen |
| C4 | S24 | *Wiederherstellen*, dann *Abbrechen* | nichts getauscht, `files/restore/` leer, Log `cloud: restore declined` |
| C10 | Emulator | zweites Gerät anmelden, dann *Mit Wiederherstellungsschlüssel freischalten* | erst „nicht freigeschaltet“ (sichern abgelehnt), danach freigeschaltet |
| C11 | Emulator | anderes Gerät sichert zuletzt, dann hier *Jetzt sichern* bzw. stündlich | von Hand: Rückfrage vor dem Ersetzen; automatisch: ruht mit Hinweis |
| C12 | Emulator | Fassung des anderen Geräts wiederherstellen, danach sichern | Rückfrage vor dem Ersetzen (W-7) |
| C13 | Emulator | anderes Gerät sperrt dieses | nächster Aufruf: Hinweis „gesperrt“, keine automatische Sicherung mehr |
| C14 | Emulator | mehr als 10 Reminder löschen, stündliche Sicherung abwarten | Bremse (R-2): nichts hochgeladen, Hinweis bis *Jetzt sichern* |
| C15 | Emulator mit Displaysperre (PIN), Testkonto | *Konto löschen* auf einem freigeschalteten Gerät, „löschen“ eintippen, Abfrage der Displaysperre erst abbrechen, dann noch einmal und mit der PIN bestätigen | nach dem Abbrechen „Nicht mit der Displaysperre bestätigt, nichts gelöscht“; nach der PIN Hinweis „auf dem Server gelöscht“, Cloud-Backup aus, Reminder unverändert; im Test-Client ist das Konto weg; neu anmelden richtet ein neues Konto ein |
| C16 | Emulator, Testkonto | zweites, nicht freigeschaltetes Gerät: *Konto löschen* | Dialog sagt „beantragt nur“; danach „Löschung des Kontos beantragt: Ab …“, keine Benachrichtigung auf diesem Gerät |
| C17 | Emulator, Testkonto | nach C16 auf dem freigeschalteten Gerät den stündlichen Lauf abwarten (spätestens etwa 24 h nach der letzten Antwort des Servers; die Uhr nicht vorstellen, sonst lehnt der Server die Erneuerung ab), dann *Löschung abbrechen* | Benachrichtigung „Cloud-Konto wird gelöscht“ mit Termin; nach dem Abbrechen weg, auf beiden Geräten kein Termin mehr |

| C18 | Emulator **ohne** Displaysperre, Testkonto | *Sicherung löschen* | keine Abfrage, Sicherung gelöscht; im Diagnose-Log `ohne_displaysperre` |

| C19 | Emulator, Testkonto (V1.3c3) | neu einrichten: *Im Passwortmanager speichern*, im Hinweis erst *Zurück*, dann *Speichern* | kein Speicher-Dialog von selbst; Hinweis nennt Ende-zu-Ende und den Papiercode des Passwortmanagers; *Zurück* öffnet nichts; nach dem Speichern *Fertig*, Hinweis auf den Papiercode bleibt |

**Nie auf dem S24:** C13–C19 und *Dieses Gerät abmelden* (sperrt das Gerät beim Server).

## D. Neu seit 1.3b0.6 (nach dem ersten bestandenen Test in B einsortieren)

| # | Bereich | Test | Erwartung |
|---|---|---|---|
| K23 | Orte (7.29) | *Einstellungen → Orte*, zuhause im WLAN *Hier festlegen* | „Standort übernommen · WLAN „…“ übernommen“, Zugangspunkte gezählt; `diag.log` ohne WLAN-Namen und BSSIDs |
| K24 | Ankunft im Zeitraum (7.29) | Reminder 19:30 *Bei Ankunft*, ab 90 min; vor 18:00 heimkommen, dann um 18:30 ins WLAN | vor 18:00 kein Alarm (Diag „too early“); *Heute* „ab 18:00 bei Ankunft zuhause, sonst 19:30“; Alarm 5 min nach der Ankunft, wie Android sie erkennt: nach der Verbindungsmeldung oder spätestens nach der nächsten Prüfung (≤ 20 min, W1) |
| K25 | Schon da (F2) | zuhause bleiben, Zeitraum beginnt; dazwischen einmal die App neu starten | kein Alarm zu Beginn und keiner nach dem Neustart (Diag „same connection as before“); genau eine Prüfung um 18:00 im Log („place check … AT“, ohne oder mit kleinem `late=`), danach keine; Alarm um 19:30 |
| K26 | Nicht da (F1) | zur spätesten Uhrzeit weg, WLAN aus Reichweite | *entfällt* bzw. bei *trotzdem klingeln* Alarm; bei „unklar“ klingelt es |
| K27 | Sitzung starten (7.11) | Reminder mit *Sitzung mit Timer* (Nachfrage 2 min, max. 6 min); im Alarm *Ich starte jetzt* | Alarm endet; Sitzungsbildschirm mit Timer; laufende Benachrichtigung mit Timer, *Fertig*/*Abbruch*; *Heute* „läuft seit …“ |
| K28 | Nachfrage und Uhr | 2 min warten | Nachfrage als Banner, auf der Uhr mit *Noch dran / Fertig / Teilschritt / Abbruch*; *Noch dran* auf der Uhr → nächste in 2 min |
| K29 | Maximale Dauer | ohne Antwort bis 6 min | letzter Reminder im Vollbild ohne *Noch dran*; 5 min später *verpasst* im Verlauf, Benachrichtigung weg |
| K30 | Vorab starten (7.20 V2) | vor der Uhrzeit *Vorab* → *Ich starte jetzt*, nach 1 min *Fertig* | Verlauf „Sitzung …–… · 1 min“; zur Uhrzeit kein Alarm |
| K31 | Abbruch zurücknehmen | Sitzung starten, in der Benachrichtigung *Abbruch*, dann *Rückgängig* | „‹Titel› abgebrochen“ mit *Rückgängig*; danach läuft die Sitzung mit dem alten Start, Benachrichtigung „Die Sitzung läuft weiter …“ |
| K32 | Teilschritt nach Limit | Reminder mit Minimum 1×/Woche und Sitzung; Minimum einmal nutzen, dann Sitzung starten | laufende Benachrichtigung ohne *Teilschritt*; Sitzungsbildschirm sagt, dass das Minimum diese Woche aufgebraucht ist |
| K33 | Notaus beim Klingeln (Review-Lauf 5) | Test-Reminder klingeln lassen, in der App *Alle Alarme pausieren* 1 h | Klingeln hört sofort auf; eine **stille** Benachrichtigung des Termins bleibt stehen, Antippen öffnet den Alarm |
| K34 | Formular-Entwurf (Review-Lauf 5) | Reminder bearbeiten, Titel ändern, ohne Speichern Dunkelmodus (Schnelleinstellungen) umschalten | Formular zeigt weiter den geänderten Titel; nach *Speichern* steht er in der Liste |
| K35 | Verschieben pro Woche (Z2b, V1.3c1) | Test-Reminder täglich, *Feineinstellungen*: Verschieben 1× *pro Woche*; heute vorab verschieben, morgen den Alarm öffnen | Zusammenfassung „Verschieben 1× pro Woche“; morgen ist *Verschieben* ausgegraut mit „wieder ab Mo …“ (nächster Montag) |
| K38 | Akku nur empfohlen (7.28 PS12, V1.3c6) | nach dem Update *Einstellungen* in der App öffnen; *Akku „Nicht eingeschränkt“* antippen; in der App-Info unter *Akku* nachsehen | die Ausnahme bleibt nach dem Update bestehen (Punkt grün, `diag.log` `batteryExempt=true`); das Antippen öffnet die App-Info von AuViLo-Reminder, nicht Androids Dialog; fehlt die Ausnahme, gibt es auf *Heute* keine Warnung |
| K37 | Wecker und Erinnerungen (7.28 PS10, V1.3c4) | direkt nach dem Update: `adb shell appops get com.avlreminder SCHEDULE_EXACT_ALARM`; falls aus, in der App über das rote Banner erlauben. Dann einen Test-Reminder in 5 min anlegen; *Einstellungen → Apps → AuViLo-Reminder → Wecker und Erinnerungen* aus, App öffnen; wieder an, App öffnen | nach dem Erlauben: kein rotes Banner, `diag.log` `exact=true`, Alarm pünktlich mit Vollbild. Aus: *Heute* zeigt „Ohne die Erlaubnis … funktioniert die App nicht richtig …“, eine Benachrichtigung „Erlaubnis … fehlt“, `diag.log` „exact alarms not allowed“. Wieder an: Banner und Benachrichtigung weg, im Log `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, `dumpsys alarm` zeigt den Test-Reminder als Wecker |
| K36 | Konto-ID (Auskunft, V1.3c2) | *Einstellungen → Cloud-Backup*, Konto-ID lange drücken und kopieren | 32 Zeichen (hex), lässt sich markieren und kopieren |

## E. Nach dem Test
- Claude liest `adb logcat -d -s AvlDiag` und `files/diag/diag.log` (per `run-as`) und
  gleicht sie mit den Erwartungen ab. Simulationen (DND, Standort, Zeit) zurücksetzen.
