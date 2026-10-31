# Entwicklungsumgebung

Kevins privater Rechner (Windows 11 Pro), eingerichtet am 2026-10-31.

| Komponente | Ort / Anmerkung |
|---|---|
| JDK 21 (Microsoft OpenJDK) | `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot`, `JAVA_HOME` systemweit gesetzt |
| Android SDK | `%USERPROFILE%\Android\Sdk`, `ANDROID_HOME`/`ANDROID_SDK_ROOT` als Benutzervariablen |
| SDK-Pakete | `platforms/android-37.0`, `build-tools/37.0.0` (Gradle zieht `36.0.0` selbst nach), `platform-tools` (adb 37.0.1), Emulator |
| Emulator | AVD `Pixel_API37` |
| Android Studio | installiert |
| Gradle | nur über den Wrapper im Projekt (`./gradlew`) |
| Gradle-Cache | `GRADLE_USER_HOME=E:\caches\gradle` (Dev Drive) |
| Repo | `E:\src\avlreminder` (Dev Drive, ReFS) |

`platform-tools`, `cmdline-tools/latest/bin` und `emulator` liegen im `PATH`, `adb` geht also direkt.
`local.properties` ist nicht nötig, weil `ANDROID_HOME` gesetzt ist.

## Hinweise

- `compileSdk = 37` funktioniert nicht, es braucht
  `compileSdk { version = release(37) { minorApiLevel = 0 } }`.
- Statt `sdkmanager` das neue `android`-CLI verwenden (Paketnamen mit `/`, z. B.
  `android sdk install platforms/android-37.0`).

## Echtes Gerät (S24 Ultra)

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
