# Play Store: was fehlt

Prüfung vom 2026-11-15 gegen den Stand V1.3c3 (`develop`). Zugehörige Anforderungen:
anforderungen.md 7.28 (PS1–PS9). Noch keine Entscheidungen, nur Bestandsaufnahme und
Empfehlung. Richtlinien ändern sich; vor dem Einreichen jeden Punkt gegen die
aktuelle Play-Console-Hilfe prüfen.

**Annahme:** Die erste Play-Fassung kommt **ohne Backend**. Dann fallen weg: Konto-Löschung
im Store-Eintrag (Pflicht nur bei Konten), Hosting in der EU, Auskunft und Export nach DSGVO
für Serverdaten, App-Zugang für die Prüfer (keine Anmeldung). Die Datensicherheit wird
„keine Daten erfasst, keine geteilt“, solange nichts das Gerät verlässt.

## 1. Blocker: Berechtigungen und Richtlinien

| # | Thema | Stand heute | Problem im Play Store | Empfehlung |
|---|---|---|---|---|
| P1 | Exakte Alarme (**umgesetzt in V1.3c4**) | bis V1.3c3 `USE_EXACT_ALARM` (bei Installation erteilt) | Eingeschränkt auf Apps, deren Kern Wecker/Timer oder Kalender mit Termin-Benachrichtigungen ist; Erklärung und Prüfung. Mit der Positionierung als Wecker und Timer (anforderungen.md 7.28 PS10) vertretbar, aber nicht sicher | Auf `SCHEDULE_EXACT_ALARM` umstellen, in allen Builds (der Sideload testet so jeden Tag den Store-Weg); der zusätzliche Schritt ist in Ordnung (PS10). Mit Erlaubnis technisch identisch (`setAlarmClock`, `systemExempted`). Aufwand klein, siehe unten |
| P2 | Vollbild-Alarm | `USE_FULL_SCREEN_INTENT`, Nutzer erteilt es schon heute | Erklärung in der Play Console nötig; automatisch erteilt nur für Wecker- und Anruf-Apps | Erklärung ausfüllen (Entwurf in [playstore-erklaerungen.md](playstore-erklaerungen.md)); die App fragt schon und fällt ohne Vollbild auf die Benachrichtigung zurück (Gerätetest dazu offen) |
| P3 | Foreground-Service `systemExempted` (**Code umgesetzt in V1.3c4**) | `AlarmService`, `SessionService` starten ohne exakte Alarme gar nicht erst, sondern zeigen eine Benachrichtigung | Android erlaubt den Typ nur, solange die App exakte Alarme darf. Außerdem Erklärung pro FGS-Typ in der Play Console | Erklärung als Wecker/Timer: Entwurf mit Video-Drehbuch in [playstore-erklaerungen.md](playstore-erklaerungen.md) |
| P4 | Akku-Optimierung (**umgesetzt in V1.3c6**, Anf. 7.28 PS12: nur empfohlen, Knopf öffnet die App-Info) | bis V1.3c5 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + direkter Dialog | Play verbietet den direkten Antrag, außer der Kern der App bricht sonst; zugelassene Fälle: Sicherheit, Messenger, Begleit-Apps, „Task automation“ | Erledigt: Permission entfernt; der Knopf öffnet die App-Info (`ACTION_APPLICATION_DETAILS_SETTINGS`, dort *Akku → Nicht eingeschränkt*) statt der langen Liste `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. Kein Pflichtpunkt, `setAlarmClock()` weckt auch aus Doze |
| P5 | Standort im Hintergrund | `ACCESS_BACKGROUND_LOCATION` für Geofence und WLAN-Ort (7.29) | Erklärung mit Video (≤ 30 s), **auffällige Offenlegung in der App vor dem Systemdialog** (Wörter „Standort“ und „im Hintergrund“/„wenn die App geschlossen ist“, welche Funktion), Datenschutzerklärung. Prüfer fragen, ob es ohne geht | **Entschieden 2026-11-15** (Anf. 7.28 PS13): behalten und begründen, erst bei Ablehnung streichen; Entwurf in [playstore-erklaerungen.md](playstore-erklaerungen.md). Auch das WLAN braucht „Immer erlauben“, Streichen hieße also Ankunft im Play-Build ganz aus. Hinweis vor dem Dialog umgesetzt in V1.3c7 (`LocationDisclosure`). Die Funktion „Ankunft am Ort“ im Store-Text als Kernfunktion beschreiben |
| P6 | Kamera, Mikrofon | Laufzeit-Berechtigungen | Unkritisch, aber in Datenschutzerklärung und Datensicherheit nennen (bleibt auf dem Gerät) | nichts am Code |
| P7 | Strenge Mechanismen | Vollbild, Dauer-Alarm, DND-Ausnahme, Notaus (PS7) | Richtlinie „Missbrauch von Gerät und Netzwerk“/„irreführendes Verhalten“: Alarme dürfen das Gerät nicht unbenutzbar machen | Notaus ist da und gut. Künftige Ideen mit Overlay (W3), Apps sperren (P2) oder Bedienungshilfen (T5) brauchen eigene Erklärungen; Bedienungshilfen-API ist im Play Store sehr streng |

**P1 im Detail: Umbau auf `SCHEDULE_EXACT_ALARM`** (umgesetzt in V1.3c4, wie geplant; Aufbau
jetzt in design.md 12). Am S24 beobachtet (2026-11-15): Ist die App von der Akku-Optimierung
ausgenommen, plant Android exakte Alarme auch ohne die Erlaubnis (`dumpsys alarm`:
`exactAllowReason=allow-listed`). Der Rückfall greift also erst, wenn beides fehlt; das
betrifft P4.
- Manifest: `USE_EXACT_ALARM` durch `SCHEDULE_EXACT_ALARM` ersetzen.
- `AndroidAlarmPort` und die Prüfungen in `AndroidWifi` rufen `setAlarmClock` bzw.
  `setExactAndAllowWhileIdle` auf. Ohne Erlaubnis wirft das eine `SecurityException`.
  Davor `canScheduleExactAlarms()` prüfen und sonst `setAndAllowWhileIdle` nehmen
  (kommt, kann aber einige Minuten zu spät sein), mit Eintrag im Diagnose-Log.
- `SystemReceiver`: auf `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` hören und
  alles neu planen (`graph.rebuild`). Beim Entzug beendet Android die App und löscht ihre
  exakten Alarme; beim Erteilen kommt dieser Broadcast.
- Berechtigungsliste: neuer Pflichtpunkt *Wecker & Erinnerungen*
  (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).
- Tests mit Robolectric (`ShadowAlarmManager.setCanScheduleExactAlarms`): mit Erlaubnis wie
  bisher, ohne Erlaubnis kein Absturz, Rückfall wird geplant.
- Kernfunktion: Mit Erlaubnis ändert sich nichts. Nur wenn der Nutzer sie entzieht, kommen
  Alarme ungenauer und ohne Dauer-Alarm; die Berechtigungsliste zeigt das dann rot.
- Am S24: Nach dem Update ist die Erlaubnis vermutlich erst aus (Android 14+ erteilt sie bei
  neu deklarierten Apps nicht von selbst). Direkt nach `adb install -r` erteilen
  (`adb shell appops set com.avlreminder SCHEDULE_EXACT_ALARM allow` oder in der App), dann
  prüfen, dass `exact=true` im Diagnose-Log steht.

## 2. Build und Release

| # | Thema | Stand heute | Fehlt |
|---|---|---|---|
| B1 | Release-Build | kein `buildTypes`-Block, nur Debug wird gebaut und installiert | Release-Typ mit R8 (`isMinifyEnabled`, `isShrinkResources`), ProGuard-Regeln (Room, Glance, Credentials), Release-Tests auf dem Gerät |
| B2 | Signatur | Debug-Schlüssel | Upload-Schlüssel erzeugen und sicher verwahren (nicht im Repo; Passwortmanager), Play App Signing, Signatur über Gradle-Properties/CI-Secrets |
| B3 | Format | APK | Android App Bundle (`bundleRelease`) |
| B4 | Variante ohne Backend | Backend-Client fest eingebunden (Composite Build), Cloud-Code in `cloud/` plus 8 Stellen außerhalb | Produkt-Flavor `play` ohne `cloud/`, ohne `verbindung`, Credentials und `googleid`, **ohne `INTERNET`**. Dann baut der Play-Build auch ohne `../avlreminder-backend`. Der heutige Weg (Ausblenden ohne Server-Werte) reicht nicht: Permission und Bibliotheken blieben drin |
| B5 | Diagnose-Log | schreibt Titel nach `files/diag` (design.md 13, Ausnahme Entwicklungsphase) | Im Release ohne Titel; Abruf ohne `run-as` (Teilen-Knopf o. ä.), sonst ist es für Fehlersuche bei Testern wertlos |
| B6 | Schnittstelle für Claude | nur im Debug-Manifest | ok, im Release nicht vorhanden; im Flavor-Aufbau so lassen |
| B7 | `applicationId` | `com.avlreminder` | **Nach dem ersten Hochladen nicht mehr änderbar.** Vorher entscheiden (siehe N1). Ein Wechsel heißt: neue App neben der Sideload-App, Daten über Export/Backup übertragen |
| B8 | Versionen | `versionName` „1.3c3-backup5“ | Für den Store ein lesbares Schema; `versionCode` muss über alle Tracks steigen (bleibt wie heute) |
| B9 | CI | baut Debug nach Merge auf `main` | Release-AAB signiert bauen und optional per Play Developer API in den internen Test hochladen |
| B10 | 16-KB-Seiten | nicht geprüft | CameraX bringt native Bibliotheken mit; mit APK-Analyzer bzw. Lint prüfen, dass sie 16 KB ausgerichtet sind |

## 3. Konto und Testpflicht

- **Entwicklerkonto** (25 USD einmalig, Identitätsprüfung mit Ausweis). Privates Konto:
  Name ist öffentlich, eine Kontakt-E-Mail auch; dafür eine eigene Adresse anlegen.
- **Händlerstatus nach DSA** (EU): als Nicht-Händler angeben, solange kein Geld fließt.
  Als Händler würden Anschrift und Telefon öffentlich.
- **Geschlossener Test**: Private Konten, die nach dem 13.11.2023 angelegt wurden, brauchen
  vor der Produktion **12 Tester, 14 Tage am Stück** im geschlossenen Test.
  **Größter praktischer Engpass:** Mit `minSdk = 37` brauchen alle 12 Tester ein Gerät
  mit Android 17. Vorher klären, wie viele es im Umfeld gibt; sonst bleibt es beim internen
  Test (bis 100 Tester, ohne Prüfpflicht für die Produktion, aber auch ohne öffentlichen
  Eintrag). Der interne Test reicht für das Lernziel „kompletter Weg“ vermutlich nicht,
  der geschlossene Test schon.
- **Bewertung `minSdk` senken (2026-11-15, noch keine Entscheidung; 8.1/PS1 sagen weiter
  Android 17):** Probeweise mit niedrigerem `minSdk` gebaut, nichts committet.
  - **Mit `minSdk = 34` (Android 14) läuft schon heute alles:** Lint ohne neuen Fund, Bau
    grün, alle 401 Tests grün, auch unter Robolectric mit Android 14 (`sdk=34`). Die App
    ruft keine API über Stufe 34 auf. Codeänderung: nur die eine Zeile in
    `app/build.gradle.kts`.
  - **Darunter wird es teurer:** Mit `minSdk = 26` meldet Lint 34 NewApi- und 17
    InlinedApi-Funde. Von Stufe 34 kommen Vordergrunddienst-Typ `systemExempted`,
    `canUseFullScreenIntent`, die Einstellungsseite für Vollbild und
    `LocalDate.ofInstant` (sonst Desugaring). Von 33 kommen Vibration mit Alarm-Attributen,
    WLAN-SSID und `POST_NOTIFICATIONS`, von 31 exakte Alarme, `VibratorManager` und
    WLAN-Standortinfo. Jede Stelle bräuchte eine Versionsweiche mit eigenem Verhalten
    (z. B. sind exakte Alarme bis Android 13 von selbst erlaubt). Das lohnt sich nicht.
  - **Was Lint und Robolectric nicht sehen:** das echte Verhalten von Alarm, Vollbild über
    der Sperre und Vordergrunddienst auf Android 14 bis 16 und auf anderen Herstellern.
    Das größere Risiko sind Hersteller mit aggressivem Energiesparen (Xiaomi, OnePlus,
    Huawei u. a.), nicht die Android-Version. Dafür vor dem Test ein Emulator-Abbild API 34
    laden (heute nur 36 und 37 da) und die Kerntests zum Alarm dort und auf 36 laufen lassen;
    im geschlossenen Test über die Rückmeldungen.
  - **Empfehlung:** `minSdk = 34`. Dann reichen fast alle Handys, die noch Updates
    bekommen, und der Aufwand ist klein.
- **Entwickler-Verifizierung** (Google, ab 2026 schrittweise, weltweit 2027): Mit einem
  verifizierten Play-Konto erledigt; betrifft dann auch die Sideload-APK auf dem S24
  (`adb install` bleibt laut Google möglich). Stand vor der Einführung in Deutschland prüfen.

## 4. Store-Eintrag und App-Inhalte (Play Console)

- **Datenschutzerklärung** als öffentliche URL (z. B. GitHub Pages) und in der App verlinkt.
  Pflicht wegen Kamera, Mikrofon und Standort, auch wenn nichts das Gerät verlässt. Inhalt:
  wer verantwortlich ist, welche Daten lokal liegen, dass nichts übertragen wird,
  Hintergrund-Standort und WLAN-Kennungen, Löschen durch Deinstallieren bzw. in der App.
- **Datensicherheit**: ohne Backend „keine Daten erfasst“. Achtung: Daten, die nur auf dem
  Gerät verarbeitet werden, gelten nicht als erfasst; Play-Services-Bibliotheken
  (`play-services-location`) dabei prüfen.
- **Fragebögen**: Inhaltseinstufung (IARC), Zielgruppe (18+ empfohlen, keine Kinder),
  Werbung (keine), Gesundheits-Apps (nein bzw. „Gewohnheiten“ prüfen), Finanzfunktionen
  (nein), Behörden-App (nein), Nachrichten-App (nein).
- **Erklärungen zu Berechtigungen**: P1 (falls doch `USE_EXACT_ALARM`), P2, P3 je FGS-Typ, P5,
  jeweils mit Video. Die Videos lassen sich am S24 aufnehmen.
- **Grafiken**: Symbol 512×512, Feature-Grafik 1024×500, mindestens 2 Screenshots
  (Handy), Kurzbeschreibung (80 Zeichen), Beschreibung (4000). Deutsch reicht.
- **Lizenzen**: Apache-2.0-Bibliotheken; eine Seite *Open-Source-Lizenzen* in den
  Einstellungen ist üblich (nicht von Play gefordert).

## 5. Name und Inhalt der App

- N1 **Name**: „AVL“ ist ein eingetragenes Zeichen eines großen Unternehmens
  (AVL List GmbH), im Store droht eine Markenbeschwerde. **Entschieden 2026-11-15:
  „AuViLo-Reminder“** (Anf. 7.28 PS11). Der Anzeigename ist seit V1.3c5 umgestellt. Vor dem
  ersten Upload noch offen:
  - `applicationId` (B7), Namespace und Paket `com.avlreminder` passend umbenennen;
  - Logo ohne „AVL“ (`ic_launcher_foreground.xml`), Store-Grafiken mit dem neuen Namen;
  - Repo- und Dateinamen, Texte in der Doku; im Backend Paketname und Namen prüfen
    (`backend.md`);
  - Wortmarke „AuViLo“ anmelden, falls der Store kommt (Kevin entscheidet).
- N2 **Texte im Code**: für eine deutsche Fassung kein Muss, für eine zweite Sprache Pflicht.
  Bewertung vom 2026-11-15 (V1.3c8): kein großer Umbau, aber eine eigene Etappe von etwa
  4–6 Sitzungen.
  - Umfang: rund 250 feste Texte in `app` (davon 45 im Formular, etwa 40 mit Platzhaltern,
    einige Mehrzahlformen in `Format.kt`), etwa 120 in `core` (Prüfmeldungen in `Changes.kt`,
    Vorlagen in `Presets.kt`, Texte in `Lifecycle`, `Digest`, `Proofs`), dazu feste
    Datumsformate (`dd.MM.`) und Benachrichtigungen, Widget und Worker außerhalb von Compose.
  - Mechanisch: `app`-Texte nach `values/strings.xml` (Deutsch bleibt Standard, Englisch in
    `values-en/`), in Compose `stringResource`/`pluralStringResource`, sonst `getString`. Die
    Tests mit deutschen Texten bleiben gültig, weil Deutsch der Standard bleibt.
  - Umbau: `core` hat keine Android-Ressourcen und liefert heute fertige Sätze; dafür
    Kennungen (z. B. `Problem.TITLE_MISSING`) statt Texten, übersetzt in `app`.
  - Heikel: Einige Texte sind **gespeicherte Daten** und werden verglichen, z. B.
    `Prep.READY_YES` („Ja, sofort“, Antwort in der DB), `Lifecycle.COUNTED`, die
    Abschluss-Gründe in `Blockers` („pausiert“, „Notaus“). Sie bleiben als feste Kennungen in
    der DB und werden erst bei der Anzeige übersetzt; sonst braucht es eine Migration.
  - Danach kostet jeder neue Text zwei Fassungen; eine Regel wie `ClockRuleTest` verhindert
    neue feste Texte. Store-Eintrag, Datenschutzerklärung und Erklärungen kommen pro Sprache
    dazu.
  - Empfehlung: jetzt nicht. Die erste Play-Fassung auf Deutsch (DE, AT, CH) braucht es nicht;
    vor einer englischen Fassung als eigene Etappe, zuerst `core` auf Kennungen.
- N3 **Einstieg für Fremde**: ~~fehlt~~ umgesetzt in V1.3c9 (Anf. 7.28 PS15): vier Seiten
  nach einer frischen Installation (was die App macht, Berechtigungen der Reihe nach,
  Probealarm, erster Reminder, Rückmeldung über *Verbesserung vorschlagen* und *Teilen*),
  wieder über *Einstellungen → Hilfe → Einstieg zeigen*. Kontakt: *Rückmeldung per E-Mail* an
  `kevin.bartik@gmail.com`, dieselbe Adresse wie im Store-Eintrag und in der
  Datenschutzerklärung. Händlerstatus (EU-Gesetz über digitale Dienste): als Privatperson ohne Einnahmen
  „kein Händler“, dann wird nur die E-Mail gezeigt; ob die Datenschutzerklärung eine
  Postanschrift braucht, vor dem Antrag prüfen.
- N4 **Daten-Export/Import als Datei**: ~~fehlt~~ umgesetzt in V1.3c8 (Anf. 7.28 PS14):
  *Sicherungsdatei* in den lokalen Kopien, mit eigenem Passwort verschlüsselt, ohne Fotos und
  Audio. Ohne Backend die einzige Sicherung für Tester und der Weg, Kevins Daten aus der
  Sideload-App zu übernehmen, falls sich die `applicationId` ändert. Für die Data-Safety-
  Angaben: Die Datei geht nur dorthin, wo der Nutzer sie im Dateidialog ablegt; die App
  sendet nichts.

## Empfohlene Reihenfolge
1. Entscheiden: `applicationId` zum Namen AuViLo-Reminder (N1, B7), Play-Fassung ohne Backend
   (Annahme oben).
2. Code, der auch der Sideload-App nicht schadet: P1, P3, P4, P5 (Offenlegung), B1, B4, B5.
   P1 und P4 ändern Kevins Alltag kaum, brauchen aber je einmal einen Gerätetest
   (Erlaubnis entziehen, Alarm kommt trotzdem bzw. Hinweis erscheint).
3. Erledigt: N4 Export/Import (V1.3c8), N3 Einstieg (V1.3c9).
4. Konto anlegen, Datenschutzerklärung, Store-Eintrag, interner Test, dann geschlossener
   Test mit 12 Testern (Android 17 vorher klären).
