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

- Für Kommandozeile/Tools: Benutzer-Umgebungsvariable `JAVA_TOOL_OPTIONS` mit obigem
  Wert – **seit 2026-10-18 dauerhaft gesetzt** (wirkt in neu gestarteten Terminals/
  Programmen; erzeugt die harmlose Meldung `Picked up JAVA_TOOL_OPTIONS`).
  Das Verzeichnis `%USERPROFILE%.Bartik\.jt` muss existieren.
- Für Gradle zusätzlich in `gradle.properties` → `org.gradle.jvmargs`.
- Android Studio (noch nicht getestet): vermutlich gleiche Option in
  *Help → Edit Custom VM Options* nötig.

## Noch offen / nicht verifiziert

- **Android Studio**: nicht installiert. Die ZIP-Variante lässt sich ohne Admin nach
  `%LOCALAPPDATA%\Programs` entpacken. Alternativ reicht VS Code + Gradle-CLI.
- **Emulator gestartet**: noch nicht – dafür fehlt ein System-Image (~1,5 GB).
  Beschleunigung ist aber bestätigt.
- **Firmen-Sicherheitssoftware** könnte adb über WLAN oder das Emulator-Netzwerk
  blockieren (USB funktioniert, siehe unten).

## ⚠️ Vorfall: Android-SDK verschwunden (2026-10-24)

- `%LOCALAPPDATA%\Android` war um 18:11 komplett weg (um 17:39 noch da). Dazwischen lag ein
  **Neustart um 17:51**. Gelöscht wurde nur dieser Ordner; JDK, Gradle (`%LOCALAPPDATA%\Programs`),
  `~\.gradle` und `~\.android` blieben erhalten. Defender meldete nichts (keine Funde/Quarantäne).
- Vermutung: eine Bereinigung durch Firmenrichtlinie/Verwaltungssoftware beim Herunterfahren oder
  Anmelden, die gezielt diesen Ordner trifft. Ein normaler Neustart löscht nichts in AppData.
- **Neu eingerichtet am 2026-10-24** am selben Ort: cmdline-tools (`commandlinetools-win-16111833`),
  `platform-tools` (adb 37.0.1), `platforms/android-37.0`, `build-tools/37.0.0`; Gradle installiert
  `build-tools/36.0.0` bei Bedarf selbst nach. Emulator nicht neu installiert (aktuell nicht gebraucht).
  Lizenz liegt wieder in `Sdk\licenses`.
- **Beobachtung:** `tools/sdk-watch.ps1` schreibt Zeit, Zustand (`OK`/`UNVOLLSTAENDIG`/`FEHLT`) und
  letzte Bootzeit nach `~\sdk-watch.log`. Damit es regelmäßig läuft, als Benutzer-Aufgabe registrieren
  (bei Anmeldung + alle 30 min, siehe Kommentar im Skript).
- Falls es wieder passiert: SDK außerhalb von AppData ablegen (z. B. `%USERPROFILE%.Bartik\Android\Sdk`,
  dann `sdk.dir` in `local.properties` anpassen) und bei der IT nach der Bereinigungsregel fragen.

## Echtes Gerät: ✅ per USB (2026-10-18)

- S24 Ultra per USB-Kabel angeschlossen, USB-Debugging aktiviert → `adb devices`
  zeigt das Gerät. **Kein Treiber nötig**, Windows lädt die ADB-Schnittstelle selbst.
  Wireless Debugging ist damit nur Ausweichlösung.
- Gerätedaten:

| Eigenschaft | Wert |
|---|---|
| Modell | SM-S928B (S24 Ultra, Europa) |
| Android | 17, API 37 (`REL`) |
| One UI | 9.0 |
| Build | CP2A.260605.016.S928BXXU6EZIF |
| Sicherheitspatch | 2026-09-05 |
| Google AICore | vorhanden (2026-09-17) → Gemini Nano voraussichtlich nutzbar |
| Samsung AICore | vorhanden (2.1.09.57) |
| Google Play-Dienste | 26.40.32 (Geofencing, Fused Location verfügbar) |
