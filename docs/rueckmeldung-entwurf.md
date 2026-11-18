# Entwurf: Rückmeldung aus der App – Diagnose und Vorschläge senden

Stand 2026-11-18, Anforderung 7.36. Noch keine Etappe: Wird eine Stufe davon gebaut, kommt
ihr Teil ans Ende von [design.md](design.md) und dieser Entwurf wird entsprechend gekürzt.
Das Backend hat seinen eigenen Entwurf zum Diagnose-Log
(`../avlreminder-backend/docs/diagnose-entwurf.md`, E-D-1 bis E-D-8); hier steht die Sicht
der App und was sich am Backend ändern müsste.

## Ziel
Nutzer schicken aus der App **Vorschläge** und **Diagnosedaten**, nur auf Einladung, mit
aktiver Zustimmung je Sendung und einer genauen Vorschau dessen, was rausgeht. Dabei soll
klar sein: Diese Daten sind **nicht Ende-zu-Ende verschlüsselt** wie das Cloud-Backup, der
Entwickler kann sie lesen, und eine KI wertet sie teilweise aus.

## Grundsätze
- **Aktiv wählen.** Kein Haken ist vorab gesetzt. Es gibt einen festen Mindestumfang, den
  die Sendung braucht, und wählbare Kategorien, jede mit einem Satz, was sie enthält.
- **Vorschau = Nutzlast.** Die Vorschau wird aus demselben Objekt erzeugt, das gesendet
  wird (kein zweiter Weg, der abweichen kann). Ein Test hält das fest.
- **Fester Hinweis auf jedem Sende-Bildschirm** (nicht wegklickbar, nicht kürzbar):
  > Nicht Ende-zu-Ende verschlüsselt wie das Cloud-Backup: Der Entwickler kann lesen, was
  > du schickst. Die Daten werden teilweise von einer KI ausgewertet (Claude, betrieben
  > von Google Cloud in der EU), der Datenschutz ist deshalb nur begrenzt. Die
  > Verschlüsselung ist
  > nur ein einfacher Schutz. Schick keine privaten oder sensiblen Angaben (Gesundheit,
  > Namen, Adressen, Passwörter).
- **Gesendet-Liste.** Jede Sendung mit Zeit, Art und Inhalt der Vorschau bleibt lokal
  sichtbar; *Löschen* löscht sie auch auf dem Server (solange sie dort noch liegt).
- **Auf Einladung.** Senden dürfen nur freigegebene Konten (wie das Cloud-Backup, 7.28
  PS16), aber getrennt davon: Für die Rückmeldung reicht die Google-Anmeldung, ohne
  Wiederherstellungsschlüssel und ohne Backup einzurichten.
- **Diagnose bleibt ohne Inhalte** (Backend E-D-1), auch wenn sie verschlüsselt ist. Die
  Verschlüsselung ersetzt das nicht.

## Inhalt einer Sendung

**Diagnose.** Mindestumfang: App-Version, Variante, Android-Version, Gerätemodell,
Schema-Version, Zeitraum. Wählbar (jede Kategorie aus):
- Alarm-Ereignisse (geplant, ausgelöst, angezeigt, Reaktion; nur Zeiten, IDs, Zustände)
- Berechtigungen und Systemeinstellungen (exakte Alarme, Vollbild, Benachrichtigungen,
  Akku-Optimierung)
- Energiesparen und Hersteller (Doze, Standby-Bucket, bekannte Hersteller-Eigenheiten)
- Absturz mit Stacktrace, ohne Meldungstext

**Vorschlag.** Mindestumfang: Text, App-Version. Wählbar: Bildschirm, von dem er kam;
*wichtig*; **Titel des Reminders** (aus, und mit eigenem Satz, dass Titel privat sein
können). Mehrere Vorschläge in einer Sendung gehen, jeder einzeln ab- oder anwählbar.

## Stufen
1. **Vorschläge per E-Mail mit Vorschau** (ohne Backend): *Vorschläge teilen* (VV5) bekommt
   die Auswahl, die Vorschau und den Hinweis, dann öffnet sich die Mail-App an die
   Kontaktadresse (`Contact.mail`). Unverschlüsselt; der Hinweis nennt dazu, dass E-Mail
   über den eigenen Mail-Anbieter läuft. Kein Server, keine Gesendet-Liste nötig (die
   Mail liegt im Postausgang).
2. **Diagnose für Tester** über das Backend: Logger um die wählbaren Kategorien erweitern,
   Einladung getrennt vom Backup, Gesendet-Liste mit Löschen. Erst mit
   KI-Auswertung über Vertex AI in der EU (unten), weil Claude die Diagnosen liest.
3. **Vorschläge über das Backend**: eigener Präfix, gleiche Limits und Frist wie die
   Diagnose, Löschen je Sendung.
4. **Verschlüsselung** (unten), frühestens mit Stufe 2 bzw. 3.

## Verschlüsselung (Zukunft)
**Ziel:** Wer die gespeicherten Daten sieht (Bucket, Kopie, Fehlkonfiguration), kann ohne
Schlüssel nichts damit anfangen. Claude soll Diagnosen lesen können, ohne den Schlüssel je
zu sehen; Vorschläge soll zunächst nur Kevin lesen können.

**Verfahren: Umschlag-Verschlüsselung mit öffentlichen Schlüsseln.** Die App kennt nur
zwei **öffentliche** Schlüssel (fest im Build): einen für Diagnosen, einen für Vorschläge.
Für jede Sendung erzeugt sie einen zufälligen AES-256-GCM-Schlüssel, verschlüsselt damit
die Nutzlast und verpackt diesen Schlüssel mit dem öffentlichen Schlüssel der Art. Der
Server legt nur den Umschlag ab (er liest heute schon nichts, E-D-5). Entschlüsseln kann
nur, wer den privaten Schlüssel benutzen darf.

**Diagnose-Schlüssel in Cloud KMS.** Asymmetrischer Schlüssel (RSA-OAEP 3072, SHA-256,
Zweck `ASYMMETRIC_DECRYPT`) im GCP-Projekt. Der private Teil verlässt KMS nie; man kann
ihn nur *benutzen* (`gcloud kms asymmetric-decrypt`), nicht auslesen. `diagnose-holen.sh`
holt die Sendungen, lässt den AES-Schlüssel von KMS entpacken und entschlüsselt lokal nach
`.diagnose/`. Claude ruft das Skript auf; im Chat steht kein Schlüssel, nur das Ergebnis.
Jede Benutzung steht im Audit-Log von KMS. Recht `cloudkms.cryptoKeyDecrypter` nur für
Kevins Konto.

**Vorschlags-Schlüssel nur bei Kevin.** Er darf **nicht** in KMS unter Kevins normaler
Anmeldung liegen: Claude arbeitet in Kevins Terminal mit derselben gcloud-Anmeldung und
könnte ihn genauso benutzen. Deshalb: ein Schlüsselpaar, das Kevin offline erzeugt; der
private Teil steht nur in seinem Passwortmanager, der öffentliche kommt in den Build. Zum
Lesen startet Kevin selbst ein kleines Werkzeug und tippt oder fügt den Schlüssel dort
ein; Claude startet es nicht.

**Grenzen, ehrlich:**
- **Kein Schutz vor dem Entwickler.** Kevin (und mit dem Diagnose-Schlüssel auch Claude)
  kann alles lesen. Darum heißt es im Hinweis „einfacher Schutz“ und nicht
  „Ende-zu-Ende“.
- **Gegen Claude nur organisatorisch.** Entschlüsselt Kevin Vorschläge auf diesem PC,
  liegen Klartext und für einen Moment der Schlüssel dort, wo Claude Dateien lesen kann.
  Die Regel „Claude liest keine entschlüsselten Vorschläge“ hält das, nicht die Technik.
  Technisch sicher nur, wenn Kevin auf einem anderen Gerät entschlüsselt oder den
  Klartext nur anzeigen lässt, ohne Datei.
- **Metadaten bleiben sichtbar:** Konto, Gerät, Zeit, Größe, Art der Sendung.
- **Wer die App verändert,** kann vor dem Verschlüsseln mitlesen; das betrifft nur das
  eigene Gerät.
- **KMS-Rechte** hat, wer Eigentümer des GCP-Projekts ist (Kevin). Ein Fremder mit Zugriff
  auf den Bucket allein liest nichts.
- **Schlüsselverlust:** Ist Kevins Vorschlags-Schlüssel weg, sind die Vorschläge auf dem
  Server unlesbar; sie laufen ohnehin nach der Frist ab. Neue Schlüssel brauchen einen
  neuen Build.

## KI-Auswertung von Tester-Daten (Vertex AI, EU)
**Warum nicht direkt bei Anthropic:** Der Auftragsverarbeitungsvertrag von Anthropic gilt
für API-Konten (kommerzielle Bedingungen), nicht für Claude Code mit Pro- oder Max-Abo
(Verbraucherbedingungen). Zero Data Retention gibt es bei Anthropic nur auf Antrag beim
Vertrieb. Über Vertex AI fällt Claude unter Googles Bedingungen und Googles
Auftragsverarbeitungsvertrag, den das GCP-Projekt des Backends schon hat; mit der Region
`eu` bleibt die Verarbeitung in der EU.

**Ablauf:** `werkzeuge/rueckmeldung-auswerten` (Name offen) holt die Sendungen von Testern,
entschlüsselt sie mit KMS und schickt sie an Claude über Vertex AI (`AnthropicVertex`,
Region `eu`, Projekt des Backends). Ausgabe ist nur die Auswertung ohne Personenbezug
(z. B. „3 Geräte: Alarm kam 2–6 Minuten zu spät, alle mit Akku-Optimierung an“). Mit dieser
Auswertung arbeitet Claude Code weiter; die Rohdaten kommen nie in eine
Claude-Code-Sitzung. Kevins eigene Diagnosen liest Claude wie bisher direkt (E-D-7).

**Einstellungen:** kein Prompt-Logging für Missbrauchs-Monitoring (bei Partner-Modellen laut
Google standardmäßig aus, sonst Ausnahme beantragen), kein Caching, kein Grounding mit
Google Search. Die von Anthropic betriebene Fassung auf Google Cloud passt nicht: Sie läuft
nur `global`, und ihr ZDR braucht wieder den Antrag bei Anthropic.

**Vor dem Einrichten prüfen:** Googles aktuelle Seite zur Datenverwaltung für das gewählte
Modell (Prompt-Logging, ob Daten an Anthropic gehen; laut einer unbestätigten Quelle
verlangen manche neue Modelle das), Verfügbarkeit in `eu`. Kosten: pro Token, bei
einzelnen Sendungen Cent-Beträge.

## Was das Backend bräuchte
- Erlaubnis zum Senden je Konto in Firestore statt `DIAGNOSE_KONTEN` (Einladung ohne
  Neustart), getrennt nach Diagnose und Vorschlag.
- `DiagnoseSenden` um die Art (Diagnose/Vorschlag) und *Löschen je Sendung* erweitern;
  Antwort mit einer Sendungs-ID für die Gesendet-Liste.
- Vertex AI im Projekt einschalten (Region `eu`), Rechte nur für Kevin.
- Für die Verschlüsselung: KMS-Schlüssel per Terraform, Recht nur für Kevin;
  `diagnose-holen.sh` entschlüsselt; ein eigenes Werkzeug für Vorschläge, das Claude nicht
  aufruft. Das Backend bleibt dabei blind für den Inhalt.

## Rechtliches (offen)
- KI-Auswertung von Tester-Daten nur über Vertex AI in der EU (oben, Anf. 7.36 RM8),
  statt eines Auftragsverarbeitungsvertrags mit Anthropic (Backend P9). Auch Vorschläge,
  die Kevin entschlüsselt, gibt er nicht an Claude Code, sondern nur über diesen Weg.
- Datensicherheit in der Play Console: *App-Aktivität / Diagnose* und *Nutzerinhalte /
  Sonstiges* (Vorschläge) als erhoben, optional, verschlüsselt übertragen, löschbar
  (playstore.md S5).
- Datenschutzerklärung und Testerinformation: was, wozu, Frist, KI-Auswertung über
  Google Cloud in der EU (Google als Auftragsverarbeiter).
