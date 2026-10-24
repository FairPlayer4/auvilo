# Logs whether the Android SDK still exists, together with the last boot time.
# The SDK vanished once (2026-10-24, around a reboot at 17:51); this shows when it happens again.
# Registered as a user scheduled task "avlreminder SDK-Wache" (at logon and every 30 minutes).
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$log = "$env:USERPROFILE\sdk-watch.log"
$boot = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToString('yyyy-MM-dd HH:mm')
$state = if (Test-Path "$sdk\platform-tools\adb.exe") { 'OK' } elseif (Test-Path $sdk) { 'UNVOLLSTAENDIG' } else { 'FEHLT' }
"{0}  {1,-14}  Boot {2}" -f (Get-Date -Format 'yyyy-MM-dd HH:mm'), $state, $boot | Add-Content -Path $log -Encoding utf8
