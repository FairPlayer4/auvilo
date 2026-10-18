# Technischer Spike auf dem S24 Ultra

Ziel: Bevor die eigentliche App gebaut wird, prüfen, ob die kritischen Mechanismen
auf dem echten Gerät zuverlässig funktionieren – vor allem unter Samsungs
Akkumanagement und über mehrere Tage.

## 1. Verbindung Handy ↔ PC

### Am Handy (einmalig)
1. **Entwickleroptionen freischalten:** Einstellungen → Telefoninfo →
   Softwareinformationen → 7× auf **Buildnummer** tippen (PIN bestätigen).
2. Einstellungen → **Entwickleroptionen** → **USB-Debugging** einschalten.

### Variante A: USB-Kabel (bevorzugt, falls es ohne Treiber klappt)
1. Handy mit einem **Datenkabel** anschließen (manche Ladekabel können keine Daten).
2. Am Handy erscheint "USB-Debugging zulassen?" → **Immer von diesem Computer
   zulassen** → Zulassen.
3. Am PC: `adb devices` muss das Gerät als `device` zeigen.

Risiko: Windows braucht für die ADB-Schnittstelle einen Treiber. Neuere Geräte
melden sich oft so, dass Windows den Standardtreiber (WinUSB) selbst lädt. Wenn
nicht, bräuchte der Samsung-USB-Treiber Adminrechte → dann Variante B.

### Variante B: WLAN (Wireless Debugging, ohne Treiber)
1. Handy und PC im **selben Netz** (Alternative, falls das Firmen-WLAN Geräte
   voneinander abschottet: PC mit dem Hotspot des Handys verbinden).
2. Entwickleroptionen → **Drahtloses Debugging** einschalten → **Gerät mit
   Kopplungscode koppeln**. Das Handy zeigt IP:Port und einen 6-stelligen Code.
3. Am PC: `adb pair <IP>:<Port> <Code>`, danach `adb connect <IP>:<Port des
   Hauptbildschirms>`.
4. Die Kopplung bleibt bestehen; nach Neustart des Handys nur erneut `adb connect`.

## 2. Was über adb gemacht wird

- **Nur lesen:** Geräte-Infos (Android-Version, One-UI-Version, Akku- und
  Doze-Status, ob On-Device-KI/AICore vorhanden ist).
- **Spike-App installieren** und wieder deinstallieren.
- **Logs** der Spike-App auslesen (`logcat`, eigene Protokolldatei der App).
- Optional: Berechtigungen der Spike-App per Befehl setzen statt durch die
  Einstellungen zu klicken (exakte Alarme, Vollbild, Akku "Nicht eingeschränkt").

Keine anderen Apps, Daten oder Einstellungen werden angefasst. USB-/Drahtloses
Debugging kann nach dem Spike wieder ausgeschaltet werden.

## 3. Spike-App

Kleine Test-App (nicht die spätere App), die protokolliert, was wann passiert:

| Test | Prüft | Erfolgskriterium |
|---|---|---|
| Exakter Alarm (`setAlarmClock`) | mehrmals täglich, auch nachts im Doze | Auslösung ≤ 1 min verspätet |
| Vollbild über Sperrbildschirm | Full-Screen-Intent bei gesperrtem, lautlosem Handy | Vollbild erscheint, auch bei "Nicht stören" |
| Vibrationsmuster | lange/ungewöhnliche Muster, lautlos-Modus | Vibration spürbar unterscheidbar |
| Alarmton (Alarm-Audiostream) | 18–21 Uhr laut trotz Lautlos | Ton hörbar |
| Wiederholte Benachrichtigung | bis zur Reaktion | läuft weiter, bis bestätigt |
| Geofence "zuhause" | Ankunft/Verlassen über mehrere Tage | Auslösung, Verzögerung gemessen |
| "Um 19 Uhr zuhause?" | Standort/Geofence-Status zum Zeitpunkt | korrekte Entscheidung |
| Neustart | Alarme nach Reboot neu geplant | keine Alarme verloren |
| Samsung-Akkumanagement | App mehrere Tage nicht öffnen | keine Alarme verloren |

Laufzeit: ca. 3–7 Tage normale Nutzung, danach Logs auswerten.

## 4. Vorbereitung am PC

- Android-SDK-Plattform **android-37** installieren und eine AGP-Version mit
  API-37-Unterstützung verwenden (Download, siehe
  [entwicklungsumgebung.md](entwicklungsumgebung.md)).
- Spike-App unter `spike/` im Repo anlegen.
