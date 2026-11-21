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
| C20 | Emulator, ein Google-Konto **nicht** auf der Freigabeliste (V1.3c12) | Cloud-Backup einschalten (Dialog nennt „nur auf Einladung“), *Mit Google anmelden*, dann *Einladung per E-Mail anfragen* | Text „Das Cloud-Backup gibt es nur auf Einladung …“ mit der Kennung, kein Absturz, Phase wieder *Mit Google anmelden*; die Mail-App öffnet sich an die Kontaktadresse mit Betreff „AuViLo-Reminder: Einladung zum Cloud-Backup (Version …)“ und der Kennung im Text (fehlt der Text in der Mail-App, lässt sich die Kennung aus der Meldung kopieren). Nicht senden |

**Nie auf dem S24:** C13–C19 und *Dieses Gerät abmelden* (sperrt das Gerät beim Server).

## D. Neu seit 1.3b0.6 (nach dem ersten bestandenen Test in B einsortieren)

| # | Bereich | Test | Erwartung |
|---|---|---|---|
| K44 | Watch-App einrichten (7.14, V1.4a) | erst die Handy-App installieren, dann die Uhr (design.md 17 „Uhr als Gerät“); Watch-App auf der Uhr öffnen, Benachrichtigungen erlauben; Kachel *Sitzung* hinzufügen | ohne Sitzung zeigt die App „Als Nächstes: … · HH:mm“ oder „Heute nichts mehr“, kein „Keine Verbindung zum Handy“; die Kachel zeigt dasselbe; `diag.log` am Handy ohne Fehler zu `watch` |
| K45 | Sitzung auf der Uhr (G5, G7) | Reminder mit Sitzung fällig werden lassen, auf der Uhr *Ich starte jetzt* (in der App oder auf der Kachel); Handy weglegen, nach dem Minimum *Minimum* oder *Fertig*; dasselbe noch einmal mit *Abbruch* und *Rückgängig* | Timer läuft auf der Uhr, Zifferblatt zeigt die laufende Aktivität, die Kachel die Minuten; am Handy startet die Sitzung (laufende Benachrichtigung) und der Alarm hört auf; *Fertig* beendet sie am Handy, `diag.log` `watch action occ=… DONE → Ok`; nach *Abbruch* ist *Rückgängig* 60 s da und nimmt den Abbruch zurück. Kein doppeltes Gerät: auf der Uhr keine gespiegelte Sitzungs-Benachrichtigung |
| K46 | Nachfrage auf der Uhr (G6) | während der Sitzung auf die erste Nachfrage warten, auf der Uhr *Noch dran*; bei der nächsten nichts tun; einmal mit Kinomodus auf der Uhr | die Uhr vibriert mit eigenem Muster, die Nachfrage öffnet die Ansicht; das Handy klingelt wie bisher nach seiner Strenge; *Noch dran* auf der Uhr beendet die Nachfrage auf beiden; im Kinomodus vibriert die Uhr nicht, das Handy klingelt trotzdem. Beim letzten Reminder des Tages fehlt *Noch dran* |
| K47 | Ohne Verbindung (G9, G9b) | während einer Sitzung Bluetooth am Handy ausschalten (Uhr und Handy im selben WLAN lassen), auf der Uhr die App öffnen; nach 2 min Bluetooth wieder an | ohne Bluetooth „Keine Verbindung zum Handy – Stand HH:mm“, alle Knöpfe gesperrt, Nachfragen nur am Handy, und dann wieder als gespiegelte Benachrichtigung auf der Uhr (prüfen, wie schnell der Wechsel greift); mit Bluetooth wieder frei, Stand aktuell |
| K48 | Watch-App mit Nachweis am Handy (G4) | Reminder mit Sitzung und Foto-Nachweis auf der Uhr starten, nach dem Minimum auf die Uhr schauen | *Fertig* und *Teilschritt* fehlen, die Uhr sagt „Fertig am Handy“; am Handy geht *Fertig* mit Foto wie bisher |
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
| K39 | Hinweis vor dem Standort-Dialog (7.28 PS13, V1.3c7) | in *Einstellungen → Orte* bzw. bei einem Reminder mit *Bei Ankunft* prüfen, dass *Standort „Immer erlauben“* in der Berechtigungsliste steht; nur wenn er fehlt: *Erteilen* antippen, *Nicht jetzt*, dann noch einmal und *Weiter* | erst der Hinweis „Standort im Hintergrund“, Android fragt nicht vorher; *Nicht jetzt* schließt ihn ohne Systemdialog; *Weiter* öffnet Androids Dialog bzw. die Seite mit *Immer erlauben*. Ist die Erlaubnis schon da (Kevins Handy), steht dort „erteilt“ und es gibt nichts zu tippen |
| K40 | Sicherungsdatei (7.28 PS14, V1.3c8) | *Einstellungen → Lokale Kopien → Exportieren …*, Passwort aus dem Passwortmanager zweimal, in *Downloads* speichern; dann *Importieren …*, erst ein falsches Passwort, dann das richtige, Rückfrage mit *Nein* beantworten | Export dauert etwa eine Sekunde, Hinweis „Gespeichert: AuViLo-Sicherung-….auvilo“; falsches Passwort: „Passwort falsch oder Datei verändert. Nichts geändert.“; richtiges: Vergleich wie bei einer Tageskopie (keine Unterschiede), *Nein* ändert nichts. Die Datei liegt danach nur in *Downloads* |
| K41 | Einstieg (7.28 PS15, V1.3c9) | nach dem Update: App öffnen; dann *Einstellungen → Hilfe → Einstieg zeigen*, alle Seiten mit *Weiter* durchgehen, auf *Probealarm* *Probealarm stellen*, Handy sperren, warten; danach noch einmal *Einstieg zeigen* und *Überspringen* | nach dem Update kein Einstieg von selbst (es gibt Reminder); Seite 2 zeigt die nötigen Berechtigungen als „erteilt“ und „Alles Nötige ist erlaubt.“; der Probealarm nennt die Uhrzeit und klingelt dann wie ein normaler Reminder (Vollbild über der Sperre), *Erledigt* beendet ihn; *Überspringen* führt zurück auf *Heute*. Den Probealarm danach unter *Reminder* löschen. *Einstellungen → Hilfe → Rückmeldung per E-Mail* öffnet Gmail mit Adresse und Betreff samt Version (nicht senden) |
| K42 | Foto-Nachweis mit Lautstärketasten (7.5 N2a, V1.3c10) | Reminder mit Foto-Nachweis fällig werden lassen, *Erledigt*, Kamera erscheint; einmal *Leiser* drücken, dann im neuen Versuch *Lauter* lang halten; dasselbe bei gesperrtem Handy über dem Sperrbildschirm; zuletzt außerhalb des Nachweises *Leiser* drücken | jeder Tastendruck gibt genau ein Foto, die Lautstärkeanzeige erscheint nicht, der Alarmton wird nicht leiser; außerhalb der Kamera ändert die Taste wie gewohnt die Lautstärke |
| K43 | Schnellerinnerung per Sprache (7.35, V1.3c11) | Widget *Sprach-Reminder* auf den Startbildschirm, antippen, beim ersten Mal das Mikrofon erlauben; „Zeit: in 2 Minuten. Name: Tee machen“ sagen, kurz warten, *Fertig*; *Anlegen*. Dann noch einmal mit „Name Wäsche“ ohne Zeit, *Nochmal*, dann *Abbrechen*; zuletzt antippen und gleich *Fertig* ohne etwas zu sagen | Muster steht auf dem Bildschirm, der Text erscheint beim Sprechen; Zusammenfassung „Tee machen, in 2 min“ mit der Angabe, welche Erkennung lief; nach *Anlegen* Hinweis „Erinnerung um …“ und der Reminder klingelt nach 2 min ohne Nachweis. Ohne Zeit: „Keine Zeit erkannt, Standard 10 min“; *Nochmal* hört wieder zu, *Abbrechen* legt nichts an. Ohne Sprechen: „Nichts verstanden.“ |
| K38 | Akku nur empfohlen (7.28 PS12, V1.3c6) | nach dem Update *Einstellungen* in der App öffnen; *Akku „Nicht eingeschränkt“* antippen; in der App-Info unter *Akku* nachsehen | die Ausnahme bleibt nach dem Update bestehen (Punkt grün, `diag.log` `batteryExempt=true`); das Antippen öffnet die App-Info von AuViLo-Reminder, nicht Androids Dialog; fehlt die Ausnahme, gibt es auf *Heute* keine Warnung |
| K37 | Wecker und Erinnerungen (7.28 PS10, V1.3c4) | direkt nach dem Update: `adb shell appops get com.avlreminder SCHEDULE_EXACT_ALARM`; falls aus, in der App über das rote Banner erlauben. Dann einen Test-Reminder in 5 min anlegen; *Einstellungen → Apps → AuViLo-Reminder → Wecker und Erinnerungen* aus, App öffnen; wieder an, App öffnen | nach dem Erlauben: kein rotes Banner, `diag.log` `exact=true`, Alarm pünktlich mit Vollbild. Aus: *Heute* zeigt „Ohne die Erlaubnis … funktioniert die App nicht richtig …“, eine Benachrichtigung „Erlaubnis … fehlt“, `diag.log` „exact alarms not allowed“. Wieder an: Banner und Benachrichtigung weg, im Log `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, `dumpsys alarm` zeigt den Test-Reminder als Wecker |
| K36 | Konto-ID (Auskunft, V1.3c2) | *Einstellungen → Cloud-Backup*, Konto-ID lange drücken und kopieren | 32 Zeichen (hex), lässt sich markieren und kopieren |

## E. Nach dem Test
- Claude liest `adb logcat -d -s AvlDiag` und `files/diag/diag.log` (per `run-as`) und
  gleicht sie mit den Erwartungen ab. Simulationen (DND, Standort, Zeit) zurücksetzen.
