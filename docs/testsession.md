# Testsession vor V2 (eine Stunde)

> Verschoben am 2026-11-02 von „vor V1.3“ auf „vor V2“, weil die App im Alltag gut läuft.
> Vor der Session an den dann aktuellen Stand anpassen (Version, neue Funktionen).
>
> Fokussierter Gerätetest mit V1.2b auf dem S24 Ultra. Ziel: alles, was die automatischen
> Tests nicht abdecken (echte Alarme, Sperrbildschirm, „Nicht stören“, Neustart, Doze,
> Benachrichtigungsknöpfe, Zeitpunkte), einmal bewusst durchspielen. Danach wertet Claude
> das Diagnose-Log aus. Nummern in Klammern verweisen auf [geraetetest-archiv.md](geraetetest-archiv.md).

## Rahmen

- **Start (T):** am besten werktags Mo–Do gegen 19:00. Dann ist der Ton aktiv (ab 18 Uhr),
  die Abend-Übersicht fällt in die Stunde, und das Wochenende mit der Rettung (73) kommt
  danach im Alltag.
- **Echte Daten bleiben:** Alle Test-Reminder heißen „Test …“ und werden am Ende gelöscht.
  Echte Reminder nicht anfassen.
- **Abweichungen sofort festhalten:** Glühbirne → kurzer Text, bei Bedarf „wichtig“.
  Zeit, Bildschirm und Reminder werden automatisch mitgespeichert. Am Ende
  Einstellungen → Verbesserungen → „Alle offenen kopieren“ und in den Chat einfügen.
- **Handy:** geladen, aber nicht am Kabel (sonst kein Doze); Lautstärke „Alarm“ hörbar.

## Vor T (ca. 5 min, mit Claude)

1. Kevin gibt das Go, Claude installiert 1.2b über 1.2a (`install -r`, Daten bleiben).
2. App öffnen; Claude prüft im Log Migration 4→5, Backup v4 und die Berechtigungen (67).
3. Einstellungen → Übersicht → abends auf die nächste Zeit **nach T+30** stellen
   (bei T = 19:00: 19:30).

## Block 1: Anlegen (T+0 bis T+5)

Alle Uhrzeiten relativ zu T; einmalig, wenn nicht anders angegeben.

| Name | Zeit | Einstellungen |
|---|---|---|
| Test A | T+5 | locker |
| Test B | T+8 | streng, Nachweis Foto |
| Test C | T+13 | normal, Checkliste mit 3 Punkten, ohne Nachweis |
| Test D | täglich T+21 | normal, Wochenziel: Ziel = restliche Tage dieser Woche inkl. heute (Mo 7 … Do 4), höchstens = Ziel, mindestens 1 |
| Test E | täglich T+45 | locker, Wochenziel 1 / höchstens 1 / mindestens 1 |
| Test F | T+31 | normal, „Auch bei Nicht stören“ an |
| Test G | T+31 | normal, „Auch bei Nicht stören“ aus |
| Test H | T+40 | streng, Nachweis Audio |
| Test I | T+50 | normal |
| Test J | T+58 | normal |

Prüfen: Heute zeigt alle unter „Später heute“ und „Diese Woche“ mit D und E (68). Beim Anlegen nebenbei 79–83 ansehen (Charakter, Bereiche, „andere…“, Schalter).

## Block 2: Ablauf

| Zeit | Was tun | Erwartung | Warum real |
|---|---|---|---|
| T+5 | Handy gesperrt liegen lassen. A kommt → in der Benachrichtigung „Erledigt“ | Banner + Vibration, kein Vollbild; Verlauf „Ziel“ (1) | Knopf in der Benachrichtigung ohne App |
| T+8 | B kommt bei gesperrtem Handy → „+10 min“ | Vollbild über dem Sperrbildschirm, mit Ton (4) | Vollbild + Ton nur auf dem Gerät prüfbar |
| T+13 | C kommt → in der Benachrichtigung „✓ Punkt 1“; dann App öffnen, im Vollbild Punkt 2 abhaken, „Rest auf morgen“ | stille Folge-Benachrichtigung „✓ Punkt 2“; Heute „2 von 3“; C morgen zur gleichen Zeit (49, 50, 52) | Benachrichtigungsknöpfe |
| T+18 | B kommt erneut → „Erledigt“ → Foto | Verlauf „Ziel · mit Foto“; Foto nicht in der Galerie (5) | Kamera |
| T+21 | D kommt | eine Stufe strenger als „normal“: Vollbild mit Ton; „Diese Woche“ rot (70). Erledigen → „1 von …, Minimum erreicht“ (69) | Wochenziel-Stufe am echten Alarm |
| T+24 | D bearbeiten → „Statistik und Serie“ | Woche 1 × Ziel; Serie plausibel (74) | |
| T+26 | Auf Heute bei E „Erledigen“ (vorab) | E verschwindet aus „Später heute“; Reminder-Liste: E erst wieder am Montag (72); Statistik von E: „vorab 1“ | |
| T+29 | „Nicht stören“ einschalten (die Ausnahme in den Berechtigungen bleibt erteilt) | | |
| T+31 | F und G fällig | F klingelt trotz „Nicht stören“; G still, Heute: „1 Reminder wartet“ (22, 25). F erledigen | DND-Verhalten hängt am System |
| T+34 | „Nicht stören“ ausschalten | G meldet sich spätestens zur nächsten Runde (23); erledigen | |
| T+30–40 | Abend-Übersicht kommt nebenbei | stille Benachrichtigung mit Rückblick, Wochenzielen, morgen; Tipp öffnet die App (76) | inexakter Alarm, Zeitpunkt nur real |
| T+40 | H kommt → „Erledigt“ → Audio unter Mindestlänge, dann richtig | erst „zu kurz“, dann „Ziel · mit Audio“ (33) | Mikrofon |
| T+44 | Handy neu starten, App **nicht** öffnen | I kommt um T+50 trotzdem (11) | Wiederherstellung nach Neustart |
| T+50 | I erledigen. Handy gesperrt, still, nicht am Kabel liegen lassen | | |
| T+58 | J kommt | pünktlich (Claude prüft die Verspätung im Log) | Doze-Nähe |
| T+60 | Aufräumen: Test A–J löschen, „Nicht stören“ aus, Übersicht abends zurück auf 20:30 | | |

**Optional echtes Doze:** Für J kann Claude vor T+51 das Handy per adb in den Doze-Modus
zwingen (`dumpsys deviceidle force-idle`) und danach wieder lösen. Das ist ein Eingriff
in den Systemzustand und läuft nur mit Kevins ausdrücklichem OK.

## Nebenbei im Alltag (bis V1.3)

Lässt sich nicht in eine Stunde pressen. Bitte nur bei Abweichung per Glühbirne melden.

- Morgen-Übersicht 7:30 (75), Übersicht „aus“ (77)
- Echte Wochenziele: stille Angebote nach erreichtem Ziel (71), Rettung Sa/So 10:00 (73)
- Tagesende 23:00: Offenes wird verpasst bzw. „ausgelassen – Wochenziel erreicht“
- Ankunft zuhause (42–48), Serie über mehrere Tage

## Auswertung durch Claude

Nur lesend, über adb:

- `adb logcat -d -s AvlDiag`: Ringpuffer, nach dem Neustart unvollständig
- `adb shell run-as com.avlreminder cat files/diag/diag.log` (und `diag.1.log`): bleibt über Neustarts
- `adb shell dumpsys alarm`: welche Alarme der App gesetzt sind

Das Log enthält Zeitpunkt, Reminder-Titel, IDs, Stufe, Zustände, Berechtigungen und
Verspätung je Alarm, keine Nachweis-Inhalte und keine Positionen. Claude gleicht es mit
den Vorschlägen aus der Session ab und schreibt einen Befund mit Korrekturen für V1.3.
