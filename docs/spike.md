# Technischer Spike auf dem S24 Ultra

Ziel: Bevor die eigentliche App gebaut wird, prüfen, ob die kritischen Mechanismen
auf dem echten Gerät zuverlässig funktionieren – vor allem unter Samsungs
Akkumanagement und über mehrere Tage.

Die Spike-App (`spike/`) ist am 2026-11-09 gelöscht worden; Code, Plan und
Verbindungsaufbau stehen in der git-Historie (letzter Stand `91b41e7`). Wie das Handy
mit dem PC verbunden wird, steht in [entwicklungsumgebung.md](entwicklungsumgebung.md).

## Ergebnis Schnelltest (2026-10-24, 15:30–16:30)

| Test | Ergebnis |
|---|---|
| Exakter Alarm (`setAlarmClock`) | ✅ **11 von 11** ausgelösten Alarmen mit **0 s Verspätung** – im leichten Doze, ohne Akku-Ausnahme, nach Prozess-Kill und nach Neustart |
| Vollbild über Sperrbildschirm | ✅ immer angezeigt (gesperrt, Klingelmodus Vibrieren); Vollbild-Berechtigung bei Sideload automatisch erteilt |
| Foreground-Service `systemExempted` | ✅ funktioniert auf Android 17 (Dauer-Vibration, Alarmton, Timeout nach 3 min) |
| Alarmton im Modus Vibrieren | ✅ klingelt über Wecker-Lautstärke (4/15); Samsung-Standardton wird lauter |
| Still | ✅ nur in der Benachrichtigungsleiste, kein Bildschirm, kein Ton |
| Bestätigen | ✅ über Vollbild und über Benachrichtigungs-Aktion |
| Geofence | ✅ Registrierung → "angekommen" nach 12 s; **Verlassen** bei ~300 m sofort; **Ankunft** mit ~1,5 min Verzögerung (Standort-Fix 85 s alt) |
| Zuhause-Prüfung im Hintergrund | ✅ Distanz korrekt (0–5 m zuhause, 326/347 m unterwegs), Genauigkeit 15–100 m |
| Neustart | ✅ BOOT_COMPLETED nach Entsperren, Alarm und Geofence neu registriert, nächster Alarm pünktlich |
| Prozess beendet (`am kill`) | ✅ System startet die App für den Alarm neu, pünktlich |
| Stopp erzwungen (`am force-stop`) | ⚠️ erwartungsgemäß: **alle Alarme und Geofence gelöscht**, Alarm fällt aus. Beim nächsten App-Start liefert Android BOOT_COMPLETED (Verhalten seit Android 15) → App erkennt den verpassten Alarm und stellt alles wieder her |
| Tiefer Doze / Standby-Bucket per adb | ❌ von Samsung blockiert (nur leichter Doze erzwingbar) → Mehrtagestest nötig |

**Folgerungen für die Implementierung**
- `setAlarmClock` + Foreground-Service `systemExempted` + Vollbild-Activity ist die
  richtige Basis – technisch trägt der Ansatz.
- Alle Reminder müssen in der **Datenbank** stehen; Alarme und Geofences werden bei
  Neustart **und** beim App-Start aus der DB neu aufgebaut (Force-Stop-Fall).
- Verpasste Reminder werden erkannt und gemeldet, nicht still übergangen (passt zu
  Leitprinzip 1).
- Ankunft zuhause kommt mit 1–2 min Verzögerung – für "bei Ankunft trainieren" gut genug.
- Offen für den Mehrtagestest: echter Tief-Doze in der Nacht, Samsungs
  "Schlafende Apps" nach Tagen ohne Öffnen, Akkuverbrauch durch Geofence.
- Zu klären: `setAlarmClock` zeigt den Reminder als "nächster Wecker" in Statusleiste
  und Sperrbildschirm. Ggf. nur für eskalierende Reminder nutzen, stille Reminder
  über `setExactAndAllowWhileIdle`.

## Zwischenstand Mehrtagestest (2026-10-25, 16:00)

Laufzeit seit 2026-10-24 16:35, knapp 1 Tag, App nicht geöffnet.

| Test | Ergebnis |
|---|---|
| Plan-Alarme | ✅ **5 von 5** (19:00, 20:30, 02:00, 08:00, 13:00), Verspätung 0–1 s |
| Nacht (02:00) | ✅ pünktlich trotz Energiesparmodus (`powerSave=true`) und "Nicht stören" (`dnd=2`) |
| Prozess vom System beendet | ✅ vor fast jedem Alarm neu gestartet – Android weckt die App zuverlässig |
| Vollbild | ✅ bei gesperrtem Handy (08:00, 13:00, 19:00); bei benutztem Handy Banner, bestätigt per Benachrichtigung |
| Geofence | ✅ Verlassen 09:31 / Ankunft 09:36 (Standort-Fix 117 s alt), Verlassen 13:54 – stimmt, Handy ist seitdem unterwegs |
| Standby-Bucket | `bucket=5` durchgehend, Samsung hat die App bisher nicht schlafen gelegt |

Noch offen bis zum Ende: Samsungs "Schlafende Apps" nach mehreren Tagen ohne
Öffnen, Ankunft nach längerer Abwesenheit, Akkuverbrauch.

## Abschluss Mehrtagestest (2026-10-27, 07:30)

Laufzeit 2026-10-24 16:35 bis 2026-10-27 07:30, gut 2,5 Tage, App nie geöffnet
(nur die V1-App benutzt).

| Test | Ergebnis |
|---|---|
| Plan-Alarme | ✅ **13 von 13** pünktlich, Verspätung 0–1 s; nächster Alarm (08:00) korrekt im System eingeplant |
| Nacht (02:00) | ✅ drei Nächte, jeweils Energiesparmodus und "Nicht stören" |
| Android-Update + Neustart | ✅ 2026-10-26 18:09 (Sicherheitspatch 2026-10-05): Neustart erkannt, Geofence neu registriert, Alarm 19:00 pünktlich |
| Samsung "Schlafende Apps" | ✅ nichts beobachtet; Standby-Bucket durchgehend `5` (exempted) |
| Akku | ✅ nicht messbar (0,001 mAh seit letztem Laden; kurzer Messzeitraum, Handy lädt nachts) |
| Ankunft nach längerer Abwesenheit | ✅ 2026-10-25: Verlassen 13:54, Ankunft 16:50 (Fix 42 s alt) |
| Verlassen morgens | ✅ mit Verzögerung bis ca. 3 min (Fix 174 s alt) – unkritisch |
| Geofence-Größe | ⚠️ siehe unten |

**Geofence ist eher grob (Radius 150 m):** Am 2026-10-26 fuhr Kevin auf dem
Heimweg nah am Zuhause vorbei (gegen 18:09 bis 18:42 im Geofence, ohne
Ankunftsmeldung direkt nach dem Update-Neustart), dann einkaufen (Verlassen 18:42)
und kam gegen 19:00 heim (Ankunft 18:59:55). Bei der Prüfung um 19:00 war das Handy
noch 110 m vom Zuhause-Punkt entfernt, galt aber schon als "zuhause". Folgen für
V1.1 (siehe Anforderungen T2):
- Vorbeifahren kann als Ankunft zählen → Ankunft optional erst nach kurzer
  **Verweildauer** bestätigen; in der Praxis einstellen.
- Nach einem Neustart den Standort aktiv prüfen, statt sich auf die automatische
  Meldung beim Registrieren zu verlassen.

**Fazit:** Alarme, Neustart, Update und Samsungs Akkumanagement sind für V1 kein
Problem. Der Ort-Trigger funktioniert, braucht aber eine Bestätigung der Ankunft.
Spike abgeschlossen; die App kann deinstalliert werden.
