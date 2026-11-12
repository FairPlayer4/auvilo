# Anforderungen – Archiv der Entscheidungen

> Die Entscheidungen aus [anforderungen.md](anforderungen.md) Kap. 9 bis 2026-10-31, neueste
> zuerst. Kap. 7 dort enthält immer den gültigen Stand; diese Einträge sind Historie.
> Überholte Einträge, die schon durch spätere ersetzt waren, stehen nur noch im Git-Verlauf.

| Datum | Entscheidung |
|---|---|
| 2026-10-31 | Mehrmals täglich: Serie hält mit mindestens einem eingehaltenen Termin pro Tag. Wochenend-Zeiten gelten auch für die Tagesgrenze (7.25) |
| 2026-10-31 | V1.2b bestätigt wie Entwurf. UI-Feinschliff aus den ersten Vorschlägen noch vor der Testsession; mehrmals täglich und Wochenend-Zeiten nach V1.3 (7.25) |
| 2026-10-29 | Reminder-Liste: abgeschlossene Einmal-Reminder automatisch eingeklappt, Archivieren von Hand, Gruppen und Sortierung (V1.2). Einmal-Reminder mit neuer Zeit wird wieder eingeplant und eingeschaltet (Bugfix, sofort) |
| 2026-10-29 | V1.2 in drei Etappen: a Alltag (Vorschläge, Liste), b Ziele über die Zeit, c Fristen und Blocker; Vorab-Reminder (T6) in V1.2c |
| 2026-10-28 | "Verbesserung vorschlagen" in der App: von überall erreichbar, Kontext automatisch, lokal gespeichert, als Text teilbar (V1.2) |
| 2026-10-27 | Alarm verstummt, solange der Nachweis läuft (mit Frist, Standard 10 min); Text-Nachweis mit Mindestzeichen und Audio-Nachweis mit Mindestdauer, Nachweise kombinierbar – alles V1.1; Audio von der Uhr in V1.4 |
| 2026-10-27 | Spike abgeschlossen: 13/13 Alarme pünktlich, Update + Neustart überstanden, kein Problem mit Samsungs Akkumanagement. Ankunft zuhause in V1.1 erst nach Verweildauer werten (Geofence grob) |
| 2026-10-26 | Vollbild-Alarme ("normal", "streng") erscheinen nicht auf der Uhr → zusätzliche spiegelbare Benachrichtigung in V1.1 (G1b) |
| 2026-10-26 | Feste Ruhezeit (außerhalb 7–21 Uhr still) entfällt ab V1.1; stattdessen gilt "Nicht stören" des Handys. Pro Reminder "auch bei Nicht stören" für besondere Ereignisse (ersetzt die Aufsteh-Wecker-Ausnahme). Laut weiterhin ab 18 Uhr; verpasst ab Tagesgrenze, Standard 23 Uhr |
| 2026-10-26 | Verschiebe-Knöpfe pro Reminder (1–2, z. B. +10 oder +30/+60 min), V1.1; kurzes Verschieben ohne Limit, aber gezählt, nicht über die Tagesgrenze |
| 2026-10-25 | Mehrere Personen (V3) möglichst ohne Backend über lokale Synchronisation im Haushalt |
| 2026-10-25 | Sensible Daten grundsätzlich nur lokal auf dem Gerät |
| 2026-10-25 | GPS-Routen erst V2/V3 – punktuelle Ortserkennung reicht vorerst |
| 2026-10-25 | Bewegungsdaten von Handy und Uhr als Forschungsprojekt (messen, später auswerten), V2 |
| 2026-10-25 | Startklar-Fragen (Ziel, Hindernisse, Startbereitschaft, erster Schritt) Pflicht bei Überwindung, änderbar mit Verlauf (V1.1); Blocker als Vorbereitungsaufgaben, Nachfragen bei wiederholtem Verschieben, "Blocker gelöst" als Minimum (V1.2) |
| 2026-10-25 | Aufsteh-Wecker zweistufig (normal, dann nicht abschaltbar bis Nachweis), V2; einfachste Variante (Schritte) als PoC früher möglich, Home-Assistant-Varianten später |
| 2026-10-25 | Haushaltsinventar (grob, "zuletzt gesehen", Import aus externer KI-Erfassung) und Einkaufsliste mit Geschäfts-Trigger, V3 – vielleicht nie; keine Anbindung an externe Listen-Apps; geteilte Liste ggf. über Home Assistant, Backend nur wenn wirklich nötig – Entscheidung in V3 |
| 2026-10-25 | Reminder vorab erledigen (V1.1) und vorgezogen starten (V1.3); "vor dem Reminder erledigt" wird positiv ausgewiesen |
| 2026-10-25 | Raumerkennung über Bluetooth-Empfänger mit der Uhr als Sender, ergänzt um aktive Methoden (Bewegung, Präsenzmelder, Taster) |
| 2026-10-25 | Watch-App ist Fernbedienung und Anzeige, Logik bleibt auf dem Handy (Handy immer in der Nähe); laufende Reminder komplett über die Uhr bedienbar |
| 2026-10-25 | Leitprinzip 10: nicht ans Handy gebunden |
| 2026-10-25 | Eigene Watch-App in V1.4 – Bedienung laufender Reminder ohne Handy |
| 2026-10-25 | NFC-Kontrollpunkte nach V3 – zu unflexibel; stattdessen Raumerkennung per Bluetooth und Taster über Home Assistant, V2 (ggf. V3) |
| 2026-10-25 | Watch: Benachrichtigungen samt Aktionen kommen bereits an – kein eigener Spike nötig |
| 2026-10-25 | Training: nur Dauer und erledigte Schritte/Sätze; Wiederholungen/Gewicht bleiben V2 |
| 2026-10-25 | Dauer wird getrackt und mit früheren Sitzungen verglichen (schnell fertig werden soll sich lohnen) |
| 2026-10-25 | Maximale Dauer pro Sitzung; danach letzter Reminder so streng wie möglich, kurze Reaktionszeit, dann nicht erfüllt – mit erledigten Teilschritten zählt das Minimum |
| 2026-10-25 | Nachfragen in Sitzungen zeitbasiert (Standard alle 10 min), Strenge der Nachfragen einstellbar |
| 2026-10-25 | Restpunkte verschieben zählt nicht gegen das Limit, wenn am Tag mindestens ein Punkt erledigt wurde |
| 2026-10-25 | Checkliste = Block: Intervall ab dem ersten erledigten Punkt, nächste Runde mit allen Punkten |
| 2026-10-25 | Checklisten mit einzeln verschiebbaren Punkten |
| 2026-10-25 | Durchführung mit Timer ("Ich starte jetzt") und Nachfrage-Muster; Abläufe für geregeltes Training, begrenzt aufs Wichtigste – keine Trainings-App |
| 2026-10-25 | Galaxy Watch vorgezogen auf V1.3, zuerst über Benachrichtigungen |
| 2026-10-25 | Alles bis einschließlich V1.3 kommt vor V2 |
| 2026-10-24 | Strenge hochgestuft: *locker* = Banner + Vibration alle 10 min, *normal* = Vollbild wie Wecker ohne Ton |
| 2026-10-18 | V1 in Etappen V1.0 / V1.1 / V1.2 → erweitert um V1.3 und V1.4 (2026-10-25), Stand in 10.1 |
| 2026-10-18 | Wochen Mo–So; Rettungs-Erinnerung am Wochenende; Joker 1/Woche (täglich) bzw. 1/Monat (wöchentlich) |
| 2026-10-18 | Besprechungen: Schnellknopf statt Kalenderabgleich |
| 2026-10-18 | Widget für Ein-Klick-Erstellung; Strenge-Vorlagen in V1 (Startwerte siehe 7.9) |
| 2026-10-18 | Dokumentation per Freitext + Audio; Zahlenwerte später |
| 2026-10-18 | Auslassen zählt neutral; Serie hält bei Minimum; ein Aussetzer pro Woche/Monat erlaubt |
| 2026-10-18 | Reminder kennen Ausführungsfenster; Fristen erinnern häufig, "auf morgen" möglich |
| 2026-10-18 | Wiederkehrendes standardmäßig ab letzter Erledigung, kalendarisch optional |
| 2026-10-18 | Training: Tagesangebot (Ankunft / 19 Uhr / sonst entfällt) + Wochenziel 3–4×, nie eine Woche ohne → Zeitraum ab 2026-11-04 (7.29) |
| 2026-10-18 | Eskalation = Vollbild oder wiederholte Benachrichtigung bis Reaktion; **7–21 Uhr sichtbar** (Vollbild + Vibration), **18–21 Uhr laut** → ersetzt durch „Nicht stören“ und Tagesgrenze (2026-10-26) und *laut ab* (7.3) |
| 2026-10-18 | Übersicht morgens und abends (einstellbar), inkl. Rückblick |
| 2026-10-18 | Motivations-Audio nur auf Klick |
| 2026-10-18 | Motivation über Statistik, Serien, Fortschritt (Woche/Monat/Jahr/gesamt); keine Belohnungssysteme |
| 2026-10-18 | Nachweisart pro Reminder; fehlender Nachweis = "nicht erfolgreich" → präzisiert 2026-11-05 (7.5) |
| 2026-10-18 | Minimum entscheidet der Nutzer; Limits für Minimum und Verschieben pro Reminder |
| 2026-10-18 | Feste, flexible und ortsbasierte Reminder in V1 |
| 2026-10-18 | KI später: teils lokal, teils über Backend |
| 2026-10-18 | Positive Verstärkung vor negativer; negative nur taktisch und begrenzt |
| 2026-10-18 | Nur Android 17 (API 37), Zielgerät S24 Ultra, nur Privatgeräte |
| 2026-10-18 | V1 lokal, nur ein Nutzer, Sideload |
