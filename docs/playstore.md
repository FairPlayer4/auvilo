# Play Store: was fehlt

Prüfung vom 2026-11-15 gegen den Stand V1.3c3 (`develop`). Zugehörige Anforderungen:
anforderungen.md 7.28 (PS1–PS9). Noch keine Entscheidungen, nur Bestandsaufnahme und
Empfehlung. Richtlinien ändern sich; vor dem Einreichen jeden Punkt gegen die
aktuelle Play-Console-Hilfe prüfen.

**Annahme (seit 2026-11-18, Anf. 7.28 PS16):** Die erste Play-Fassung kommt **mit dem
Cloud-Backup**. Bis dahin hieß die Annahme „ohne Backend“. Damit kommen dazu: Löschseite im
Web, Datenschutzerklärung für App und Server, App-Zugang für die Prüfer, Freigabe der
Anmeldung, Release-Konfiguration für „Mit Google anmelden“ und eine Datensicherheit mit
erhobenen Metadaten. Alles dazu in Abschnitt 6.

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
| B4 | Variante ohne Backend | Backend-Client fest eingebunden (Composite Build), Cloud-Code in `cloud/` plus 8 Stellen außerhalb | **Entfällt seit 2026-11-18** (Play-Fassung mit Backend, Abschnitt 6). Der Play-Build braucht `../avlreminder-backend` auf dem festen Commit, wie heute die CI. Was für das Backend im Release dazukommt: S3 |
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
  **Engpass Android-Version:** Bis 2026-11-15 war `minSdk = 37`, also hätten alle Tester
  Android 17 gebraucht. Jetzt `minSdk = 34` (Android 14, Anf. 8.1), siehe unten. Ohne 12
  Tester bliebe nur der interne Test (bis 100 Tester, ohne Prüfpflicht für die Produktion,
  aber auch ohne öffentlichen Eintrag); der reicht für das Lernziel „kompletter Weg“
  vermutlich nicht, der geschlossene Test schon.
- **`minSdk` gesenkt auf 34 (2026-11-15, V1.3c9):**
  - **Mit `minSdk = 34` (Android 14) lief schon alles:** Lint ohne neuen Fund, Bau grün,
    alle Tests grün, auch unter Robolectric mit Android 14. Die App ruft keine API über
    Stufe 34 auf. Geändert: die Zeile in `app/build.gradle.kts`; die Alarm-Tests laufen
    seitdem unter Android 14 und 17.
  - **Tiefer nicht:** Mit `minSdk = 26` meldet Lint 34 NewApi- und 17 InlinedApi-Funde.
    Von Stufe 34 kommen Vordergrunddienst-Typ `systemExempted`, `canUseFullScreenIntent`,
    die Einstellungsseite für Vollbild und `LocalDate.ofInstant` (sonst Desugaring). Von
    33 kommen Vibration mit Alarm-Attributen, WLAN-SSID und `POST_NOTIFICATIONS`, von 31
    exakte Alarme, `VibratorManager` und WLAN-Standortinfo. Jede Stelle bräuchte eine
    Versionsweiche mit eigenem Verhalten (z. B. sind exakte Alarme bis Android 13 von selbst
    erlaubt).
  - **Offen, was Lint und Robolectric nicht sehen:** das echte Verhalten von Alarm, Vollbild
    über der Sperre und Vordergrunddienst auf Android 14 bis 16 und auf anderen Herstellern.
    Das größere Risiko sind Hersteller mit aggressivem Energiesparen (Xiaomi, OnePlus,
    Huawei u. a.), nicht die Android-Version. Dafür vor dem Test die Kerntests zum Alarm im
    Emulator `Pixel_API34` (Android 14, angelegt am 2026-11-15) laufen lassen; im
    geschlossenen Test kommt der Rest über die Rückmeldungen.
- **Entwickler-Verifizierung** (Google, ab 2026 schrittweise, weltweit 2027): Mit einem
  verifizierten Play-Konto erledigt; betrifft dann auch die Sideload-APK auf dem S24
  (`adb install` bleibt laut Google möglich). Stand vor der Einführung in Deutschland prüfen.

## 4. Store-Eintrag und App-Inhalte (Play Console)

- **Datenschutzerklärung** als öffentliche URL und in der App verlinkt; mit Backend eine
  gemeinsame Erklärung für App und Server (S7). Inhalt für den App-Teil: wer verantwortlich
  ist, welche Daten lokal liegen, Hintergrund-Standort und WLAN-Kennungen, Löschen durch
  Deinstallieren bzw. in der App.
- **Datensicherheit**: mit Backend erhobene Metadaten (Konto- und Geräte-IDs), siehe S5.
  Daten, die nur auf dem Gerät verarbeitet werden, gelten nicht als erhoben;
  Play-Services-Bibliotheken (`play-services-location`) dabei prüfen.
- **Löschen des Kontos**: Adresse der Löschseite im Formular zur Datensicherheit (S6).
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

## 6. Mit Backend (Cloud-Backup)

Ergänzt am 2026-11-18 (Anf. 7.28 PS16): Die erste Play-Fassung bringt das Cloud-Backup mit.
Das Backend (`../avlreminder-backend`) ist schon auf DSGVO und Ende-zu-Ende-Verschlüsselung
ausgelegt; seine Datenschutz-Unterlagen liegen dort in `docs/datenschutz/` (Entwürfe), die
Play-Punkte des Backends in dessen `anforderungen.md` 4.4 (P1–P10) und Etappe B6. Hier steht,
was davon die App und die Play Console betrifft.

| # | Thema | Stand heute | Fehlt für Play |
|---|---|---|---|
| S1 | Sicherheitsphase (backend.md) | Eigenbetrieb: Claude darf Schlüssel und Token kennen | **Vor dem ersten Tester gegen die Cloud:** Claude kündigt sie an, das Backend wird zurückgesetzt, Kevin richtet die App mit neuen Schlüsseln ein, Claude sieht sie nicht mehr. A-15 (Schlüssel im Passwortmanager) ist dafür umgesetzt |
| S2 | Wer darf sich anmelden? | Freigabeliste im Backend (`freigegebene_kennungen`), nur Kevin | **Entschieden 2026-11-18: auf Einladung** (Anf. 7.28 PS16), im geschlossenen Test und in der Produktion; offen frühestens nach B6 mit Play Integrity (S9), neu entscheiden. **In der App umgesetzt in V1.3c12:** Bei einer nicht freigegebenen Kennung sagt die App „Das Cloud-Backup gibt es nur auf Einladung“, die App gehe ohne genauso, und bietet *Einladung per E-Mail anfragen* mit der Kennung im Text an; Schalter und Einschalt-Dialog nennen „nur auf Einladung“. Offen: der Store-Text (S11) |
| S3 | Release-Konfiguration | Server-Werte in `gradle.properties`; Android-OAuth-Client nur mit dem Debug-Schlüssel (cloud-entscheidungen.md, Einrichtung) | Android-Client im GCP-Projekt zusätzlich mit dem **SHA-1 des Play-App-Signaturschlüssels** und des Upload-Schlüssels (B2), sonst scheitert „Mit Google anmelden“ in der Store-Fassung. Nach der Umbenennung (N1, B7) ein Client für die neue `applicationId`. Release-Build aus dem festen Backend-Commit (`-PbackendStreng=true`). R8-Regeln für Tink, Protobuf, Credential Manager und `googleid` (B1) |
| S4 | OAuth-Zustimmungsbildschirm | vermutlich Status *Testen* | Im Status *Testen* können sich nur eingetragene Testnutzer (höchstens 100) anmelden; für den geschlossenen Test reicht das. Für die Produktion auf *In Produktion* stellen, mit Startseite und Datenschutzerklärung; dafür verlangt Google wahrscheinlich eine eigene, bestätigte Domain (Backend P10, offene Frage Domain). Nur `openid`-Bereiche, also keine aufwendige Prüfung sensibler Bereiche |
| S5 | Datensicherheit (Play Console) | geplant „keine Daten erfasst“ | Neu ausfüllen, siehe unten |
| S6 | Konto löschen | in der App umgesetzt (*Konto löschen*, R-3, V1.3c2) | **Löschseite im Web** (Backend P10/K7, Etappe B6) und ihre Adresse im Formular zur Datensicherheit. Play verlangt beides, sobald man in der App ein Konto anlegen kann; die Google-Anmeldung legt eins an |
| S7 | Datenschutzerklärung | Entwurf im Backend (`docs/datenschutz/datenschutzerklaerung.md`) | **Eine** Erklärung für App und Backend: den App-Teil ergänzen (lokale Daten, Kamera, Mikrofon, Standort im Hintergrund, WLAN-Kennungen, Sicherungsdatei, Löschen durch Deinstallieren), öffentliche Adresse auf dem Dienst `web` (P10), verlinkt im Store-Eintrag, im OAuth-Bildschirm und in der App (*Einstellungen → Hilfe* und beim Einschalten des Cloud-Backups). Ob ein Impressum nötig ist, mit der Prüfung klären |
| S8 | App-Zugang für die Prüfer | – | Alles außer dem Cloud-Backup geht ohne Anmeldung; das in *App-Zugang* so angeben. Für das Backup ein **eigenes Google-Testkonto** für die Prüfer (Kevin legt es an, Zugangsdaten nur in der Play Console, nie im Repo oder bei Claude), auf der Freigabeliste und als OAuth-Testnutzer; dazu kurze Schritte (einschalten, anmelden, Schlüssel im Passwortmanager oder *auf Papier aufgeschrieben*, *Jetzt sichern*). Ohne Zugang lehnen Prüfer erfahrungsgemäß mit „Need login credentials“ ab |
| S9 | Missbrauch und Kosten | Limits pro Konto, Budget 20 €/Monat mit Kill-Switch | Play Integrity (Backend ID5, B6) vor einer offenen Anmeldung. Der Kill-Switch trifft dann alle Nutzer; das passt zu „Kosten vor Verfügbarkeit“, muss aber im Store-Text und in der App stehen (S11) |
| S10 | DSGVO-Unterlagen | Entwürfe im Backend: VVT, TOMs, DSFA, Betroffenenrechte, Datenpannen | Abschließen und prüfen lassen (Backend `docs/datenschutz/README.md`, „Was noch zu tun ist“): Testerinformation vor dem geschlossenen Test, DSFA (Erinnerungen können Gesundheitsdaten sein, Art. 9), Aufsichtsbehörde eintragen, Prüfung durch jemanden mit Datenschutzerfahrung. Mindestalter 16 (Backend P8) passt zur Zielgruppe 18+ in der Play Console |
| S11 | Store-Text und Einstieg | Cloud-Backup heißt in der App „(experimentell)“ | Im Store-Text: freiwillig, Ende-zu-Ende verschlüsselt, Server in der EU, kein verlässliches Backup (Kosten vor Verfügbarkeit), ohne Wiederherstellungsschlüssel sind die Daten weg. Die Sicherungsdatei (N4) als Weg ohne Cloud nennen |
| S12 | Diagnosen und Vorschläge von Testern | nur Kevins Konto darf senden (Backend P9, E-D-3) | So lassen, bis es einen Auftragsverarbeitungsvertrag mit Anthropic gibt; bei Testern *Diagnose senden* ausblenden oder ohne Erlaubnis klar ablehnen. Geplant (Anf. 7.36, [rueckmeldung-entwurf.md](rueckmeldung-entwurf.md)): Senden auf Einladung mit aktiver Wahl, Vorschau und dem Hinweis „nicht Ende-zu-Ende verschlüsselt, teilweise von KI ausgewertet“ |

**Datensicherheit mit Backend (S5).** Laut Play-Console-Hilfe gelten Daten, die
Ende-zu-Ende verschlüsselt übertragen werden und nur für Sender und Empfänger lesbar sind,
nicht als erhoben. Die Sicherung selbst (Reminder, Nachweise, Einstellungen) und das
Schlüssel-Backup zählen deshalb nicht. Erhoben werden aber Metadaten:
- **Nutzer-IDs:** die pseudonyme Konto-ID bzw. Google-Kennung (`sub`); Zweck
  Kontoverwaltung und Funktion der App.
- **Geräte- oder andere IDs:** Geräte-ID und öffentlicher Geräteschlüssel.
- **Server-Logs** mit IP-Adresse bis 30 Tage (Backend K4): prüfen, ob Google das als
  erhoben verlangt; im Zweifel angeben.
- **App-Aktivität/Diagnose** und **Nutzerinhalte/Sonstiges** (Vorschläge): nur, wenn das
  Senden für Tester offen ist (S12, Anf. 7.36); die Verschlüsselung dort zählt nicht als
  Ende-zu-Ende, weil der Entwickler lesen kann;
  sonst nicht.

Dazu: alles **optional** (das Backup ist ausgeschaltet, bis man es einschaltet),
**verschlüsselt übertragen** (HTTPS), **Löschen möglich** (in der App und über die
Löschseite, S6), **nicht geteilt** (Google Cloud ist Auftragsverarbeiter; die Google-Anmeldung
verantwortet Google selbst, Backend P3). Kamera, Mikrofon, Standort und WLAN bleiben auf
dem Gerät und zählen nicht. Vor dem Ausfüllen gegen die aktuelle Hilfe prüfen.

## Empfohlene Reihenfolge
1. Entscheiden: `applicationId` zum Namen AuViLo-Reminder (N1, B7), eigene Domain (S4).
   Entschieden: Anmeldung auf Einladung (S2).
2. Code, der auch der Sideload-App nicht schadet: P1, P3, P4, P5 (Offenlegung), B1, B5 (B4 entfällt).
   P1 und P4 ändern Kevins Alltag kaum, brauchen aber je einmal einen Gerätetest
   (Erlaubnis entziehen, Alarm kommt trotzdem bzw. Hinweis erscheint).
3. Erledigt: N4 Export/Import (V1.3c8), N3 Einstieg (V1.3c9).
4. Backend B6 im Backend-Repo: Löschseite und Datenschutzerklärung auf dem Dienst `web`
   (S6, S7), Play Integrity, DSGVO-Unterlagen abschließen (S10). Release-Konfiguration für die
   Anmeldung (S3), sobald Upload- und Signaturschlüssel feststehen.
5. Sicherheitsphase (S1), dann Konto anlegen, Store-Eintrag, Datensicherheit (S5), interner
   Test, dann geschlossener Test mit 12 Testern (Android 14 oder neuer, minSdk 34), Tester
   auf der Freigabeliste und als OAuth-Testnutzer.
