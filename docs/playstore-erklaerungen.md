# Play Console: Entwürfe für die Erklärungen

Entwurf vom 2026-11-15 zu [playstore.md](playstore.md) P2 (Vollbild), P3 (Foreground-Service) und
P5 (Standort im Hintergrund), Stand V1.3c6. Die Formulare liegen in der Play Console unter *Überwachen und verbessern → App-Inhalte*.
Ein Konto gibt es noch nicht; die genauen Feldnamen vor dem Einreichen im Formular abgleichen.
Die Texte für Google stehen auf Englisch (die Prüfer arbeiten international); der Name ist schon
AuViLo-Reminder, die `applicationId` folgt vor dem Antrag (playstore.md N1).

Quellen: Play-Console-Hilfe „Understanding foreground service and full-screen intent
requirements“ (answer/13392821), Android-Doku zu den FGS-Typen (`fg-service-types`) und
Play-Console-Hilfe zum Standort im Hintergrund (answer/9799150, answer/11150561; nur aus
Suchergebnissen, nicht ganz gelesen), Stand 2026-11-15.

## P2: Vollbild-Intent (`USE_FULL_SCREEN_INTENT`)

**Was das Formular will:** angeben, ob die Kernfunktion der App *Wecker* oder *Anrufe* ist. Nur
dann erteilt Android die Erlaubnis ab Android 14 von selbst. Laut Hilfe kein Video, kein Freitext.

**Auswahl:** Wecker bzw. *Alarm* (Positionierung als Wecker und Timer, Anf. 7.28 PS10).

**Falls doch ein Freitext verlangt wird:**

> AuViLo-Reminder is an alarm clock for personal reminders. At the time the user set, the app
> rings on the alarm audio stream and shows a full-screen alarm over the lock screen, like a
> clock app. It keeps ringing in rounds until the user responds (done, snooze or skip); an
> emergency stop is always available. The full-screen intent is used only for these
> user-scheduled alarms, never for ads or promotions.

**Prüfen vor dem Einreichen** (playstore.md P2: „fällt ohne Vollbild sauber zurück“):
- Code: `Notifications.kt` setzt `setFullScreenIntent(open, true)`. Ohne Erlaubnis zeigt Android
  die Benachrichtigung als Heads-up; Ton und Vibration kommen weiter aus dem `AlarmService`. Die
  App fragt die Erlaubnis in der Liste ab (*Vollbild-Alarm*, kein Pflichtpunkt).
- Offen: ein Gerätetest ohne die Erlaubnis (Vollbild-Alarm aus, Test-Reminder in 2 min, gesperrt
  und entsperrt). Erwartet: Heads-up mit Knöpfen, Ton läuft, kein Absturz. Wird die Erklärung
  angenommen, betrifft das nur Nutzer, die die Erlaubnis selbst entziehen.

## P3: Foreground-Service `systemExempted`

**Lage:** Die App hat zwei Dienste vom Typ `systemExempted` (`AlarmService`, `SessionService`).
Android erlaubt den Typ für Apps mit `SCHEDULE_EXACT_ALARM` oder `USE_EXACT_ALARM`; ohne die
Erlaubnis starten beide Dienste seit V1.3c4 gar nicht erst. Die Liste der Anwendungsfälle in
der Play-Hilfe kennt `systemExempted` nicht; das Formular erlaubt einen eigenen Anwendungsfall.
Ob die Console den Typ überhaupt abfragt, zeigt sich erst beim ersten Upload. Pro Typ verlangt
sie: Beschreibung, Folgen bei Aufschub oder Unterbrechung, Anwendungsfall, Link zu einem Video.

**Anwendungsfall (eigener Eintrag):** `Alarm clock and timer (app holds SCHEDULE_EXACT_ALARM)`

**Beschreibung:**

> AuViLo-Reminder is an alarm clock and timer. Two foreground services use the type
> systemExempted, which Android allows for apps holding SCHEDULE_EXACT_ALARM; without that
> permission the app does not start them and shows a notification instead.
>
> 1. Alarm (AlarmService): started by an exact alarm (AlarmManager.setAlarmClock) at the time the
>    user set. It plays the alarm sound and vibration in a loop for one round of three minutes,
>    with a full-screen alarm, until the user responds. Then it stops.
> 2. Timer session (SessionService): started when the user taps "I'm starting now" on a reminder
>    set up as a timed session. It shows an ongoing notification with a running chronometer and
>    the actions Done, Partial step and Abort, while exact alarms check in every few minutes and
>    ring a final reminder at the maximum duration. It stops when the session ends.
>
> Both are started only by the user's own alarms or actions, are always visible as a
> notification, and can be stopped from it.

**Folgen bei Aufschub oder Unterbrechung:**

> Deferred: the alarm would ring late or not at all, so the user misses the appointment or task
> the alarm was set for (for example medication). Interrupted: the alarm sound stops before the
> user has responded, and the running timer disappears from the notification, so the user loses
> track of the session.

**Video (Drehbuch, Bildschirmaufnahme am Handy, unter 30 s, z. B. als nicht gelistetes
YouTube-Video):**
1. In der App einen Reminder „Test“ in 1 Minute anlegen (Uhrzeit sichtbar).
2. Handy sperren; zur Zeit erscheint der Vollbild-Alarm über dem Sperrbildschirm, Ton läuft.
3. *Erledigt* tippen; Alarm endet, Benachrichtigung verschwindet.
4. Bei einem zweiten Reminder mit *Sitzung mit Timer* auf *Ich starte jetzt* tippen; die
   laufende Benachrichtigung mit Uhr erscheint.
5. In der Benachrichtigung *Fertig* tippen; sie verschwindet.
Nur App-Inhalte zeigen, keine Systemeinstellungen; Testdaten statt echter Reminder.

**Risiko und Ausweg:** Für den Wecker ist die Begründung klar. Beim `SessionService` (Timer) ist
sie schwächer: Er misst selbst nichts, die Prüfungen kommen über exakte Alarme. Lehnt Google ihn
ab, zeigt die Sitzung ihre Benachrichtigung ohne Foreground-Service; den Rückfall gibt es schon
(„plain notification“, wenn `startForeground` fehlschlägt). Dann würde Android die
Benachrichtigung aber wegwischbar machen. Empfehlung: so einreichen, nur bei Ablehnung umbauen.

## P5: Standort im Hintergrund (`ACCESS_BACKGROUND_LOCATION`)

**Entscheidung** (Anf. 7.28 PS13): behalten und begründen, erst bei einer Ablehnung streichen.
Das WLAN braucht dieselbe Erlaubnis (BSSID und SSID gibt Android nur mit Standort heraus, im
Hintergrund nur mit „Immer erlauben“). Die Begründung nennt deshalb beides; das WLAN ist heute
der Hauptweg, der Geofence die Ergänzung.

**Was Google will:** im Formular die Funktion beschreiben, die den Standort im Hintergrund
braucht, und begründen, warum es im Vordergrund nicht geht; ein kurzes Video (YouTube oder
Google Drive) mit dem Öffnen der App, dem Weg zur Offenlegung und dem Zustimmungsdialog. Dazu
die Offenlegung in der App und die Datenschutzerklärung. Feldnamen vor dem Einreichen abgleichen.

**Funktion (Kurzbeschreibung):**

> Arrival reminders: a reminder can wait until the user arrives at a place they saved (for
> example "after 18:00, when I get home, otherwise at 19:30").

**Begründung:**

> AuViLo-Reminder is an alarm clock for personal reminders. The user can tie a reminder to a
> place they saved in the app ("Home"). When they arrive there, the reminder rings; if they
> do not arrive by the latest time they set, it rings anyway or is dropped, as they chose.
>
> Arrival is noticed while the app is closed, usually with the phone in a pocket, so it needs
> location in the background:
> - Wi-Fi: when the phone connects to the Wi-Fi network saved for the place, the app checks
>   its BSSID and SSID. Android only provides these with location permission, and in the
>   background only with "Allow all the time".
> - Geofence: one geofence per saved place (Google Play services, dwell transition) as a
>   second signal, for example when the phone does not join the Wi-Fi.
>
> Location is only used for these arrival checks and only for places the user saved. Nothing
> leaves the device: no server, no ads, no analytics. Without the permission the reminder
> rings at its latest time, and the app works as a plain alarm clock.

**Offenlegung in der App (fehlt noch im Code):** ein eigener Bildschirm vor dem Systemdialog,
wenn ein Reminder zum ersten Mal *Bei Ankunft* bekommt oder in der Berechtigungsliste
*Standort „Immer erlauben“* angetippt wird. Text-Entwurf:

> **Standort im Hintergrund**
> AuViLo-Reminder nutzt deinen Standort und das verbundene WLAN, um zu erkennen, wann du an
> einem gespeicherten Ort ankommst – auch wenn die App geschlossen ist oder nicht benutzt wird.
> Nur dann klingeln Reminder mit „Bei Ankunft“ beim Ankommen statt erst zur spätesten Uhrzeit.
> Standort und WLAN bleiben auf dem Handy und werden nicht weitergegeben.
> Im nächsten Schritt fragt Android nach dem Standort; wähle dort „Immer erlauben“.
> [Weiter] [Nicht jetzt]

„Nicht jetzt“ lässt alles wie ohne Erlaubnis (Reminder klingeln zur spätesten Uhrzeit).

**Video (Drehbuch, unter 30 s):**
1. App öffnen, *Einstellungen → Orte*, *Hier festlegen* für „Zuhause“ (mit Testort).
2. Einen Reminder mit *Bei Ankunft* anlegen; die Offenlegung erscheint, *Weiter*.
3. Androids Dialog, *Immer erlauben* wählen.
4. Mit geschlossener App am Ort ankommen bzw. das WLAN verbinden: der Reminder klingelt.

**Risiko und Ausweg:** Prüfer fragen, ob es ohne geht; für einen Wecker ist die Ankunft keine
Kernfunktion. Deshalb steht sie im Store-Text als eigene Funktion. Bei Ablehnung im Play-Build
*Bei Ankunft* ausblenden und die Standort-Berechtigungen aus dem Manifest nehmen (Flavor, B4);
der Sideload behält sie.
