# Entwicklungsumgebung

Geprüft am 2026-10-18 auf dem bevorzugten Rechner (Windows 11 Enterprise, **ohne Adminrechte**,
i7-10510U, 16 GB RAM, ~900 GB frei).

## Ergebnis: Entwicklung ist ohne Adminrechte möglich ✅

| Komponente | Status | Ort / Anmerkung |
|---|---|---|
| JDK 21 (Temurin) | ✅ vorhanden | `%LOCALAPPDATA%\Programs\jdk-21.0.12.1+1` |
| Android SDK Commandline-Tools 23 | ✅ installiert | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` |
| platform-tools (adb 37.0.1) | ✅ installiert, läuft | `%LOCALAPPDATA%\Android\Sdk\platform-tools` |
| Emulator 37.2 | ✅ installiert | WHPX-Beschleunigung **verfügbar** (`emulator -accel-check`) |
| Gradle 8.11.1 | ✅ installiert | `%LOCALAPPDATA%\Programs\gradle-8.11.1` (später Gradle-Wrapper im Projekt) |
| Netzwerk | ✅ | google/maven/gradle-Repos direkt erreichbar, kein Proxy |
| Test-Build | ✅ | Minimal-App (Kotlin, AGP 8.7.3, compileSdk 35) → `app-debug.apk` gebaut |
| Android-SDK-Lizenz | ✅ akzeptiert | `Sdk\licenses\android-sdk-license` |

## ⚠️ Stolperstein: Java "Unable to establish loopback connection"

Auf diesem Rechner schlägt in Java `Selector.open()` (und damit `HttpClient`, Gradle,
Android-CLI) fehl, weil der Unix-Domain-Socket im Standard-Temp-Verzeichnis
(`%USERPROFILE%~1.BAR\AppData\Local\Temp`) nicht angelegt werden kann
(vermutlich Sicherheitssoftware/Richtlinie oder Kurzname im Pfad).

**Workaround (ohne Admin):** Java ein anderes Verzeichnis für die Sockets geben:

```
-Djdk.net.unixdomain.tmpdir=%USERPROFILE%.Bartik\.jt
```

- Für Kommandozeile/Tools: Umgebungsvariable `JAVA_TOOL_OPTIONS` mit obigem Wert
  (am besten dauerhaft als Benutzer-Umgebungsvariable; erzeugt die harmlose Meldung
  `Picked up JAVA_TOOL_OPTIONS`).
- Für Gradle zusätzlich in `gradle.properties` → `org.gradle.jvmargs`.
- Android Studio (noch nicht getestet): vermutlich gleiche Option in
  *Help → Edit Custom VM Options* nötig.

## Noch offen / nicht verifiziert

- **Android Studio**: nicht installiert. Die ZIP-Variante lässt sich ohne Admin nach
  `%LOCALAPPDATA%\Programs` entpacken. Alternativ reicht VS Code + Gradle-CLI.
- **Emulator gestartet**: noch nicht – dafür fehlt ein System-Image (~1,5 GB).
  Beschleunigung ist aber bestätigt.
- **Echtes Gerät per USB**: Hersteller-USB-Treiber brauchen oft Admin.
  Ausweg: **Wireless Debugging** (Android 11+, `adb pair`) – braucht keinen Treiber.
  Für die App ist Testen am echten Gerät wichtig (Akku-Management, Alarme, Geofences).
- **Firmen-Sicherheitssoftware** könnte adb über WLAN oder Emulator-Netzwerk blockieren
  – beim ersten echten Test prüfen.
