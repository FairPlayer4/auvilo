# Backend – Überblick aus Sicht der App

> Stand: 2026-11-02. Zusammenfassung von `../avlreminder-backend` (GitHub
> `FairPlayer4/avlreminder-backend`, privat). Maßgeblich sind die Dokumente dort:
> `docs/anforderungen.md`, `docs/stand.md`, `docs/b2b-entwurf.md`, `docs/formate.md`.
> Hier steht nur, was die App betrifft. Anforderungen: [anforderungen.md](anforderungen.md) 7.28.

## Grundsätze
- **Lernprojekt**, vorerst **nicht mit der App verbunden**. Ziel: Backup, später Sync
  zwischen eigenen Geräten und mit anderen Personen.
- **Die App bleibt lokal-first.** Alarme, Eskalation und Fachlogik bleiben auf dem
  Gerät; ohne Backend funktioniert die App vollständig.
- **Der Server sieht keine Inhalte:** Ende-zu-Ende-Verschlüsselung (Tink), der Server
  speichert nur Chiffrate und nötigste Metadaten (Konto-Pseudonym, Größe, Zeitpunkt).
- **Datenminimierung:** Anmeldung mit Google, gespeichert wird nur die pseudonyme
  Konto-ID (`sub`), keine E-Mail, kein Name, keine Suche nach Personen.
- **Nur EU:** Google Cloud, Region `europe-west1`; per Test abgesichert.
- **Kosten begrenzt:** Budget 20 €/Monat mit Kill-Switch (sperrt ab 100 %, trennt ab
  150 % vom Rechnungskonto). Daraus folgt: **Kosten vor Verfügbarkeit** – das Backend
  ist kein verlässliches Backup.

## Schlüssel
- Pro Person ein **Personenschlüssel** und ein **Tresorschlüssel** (private Daten),
  pro Gerät ein Geräteschlüssel.
- **Wiederherstellungsschlüssel** zum Ausdrucken entschlüsselt das Schlüssel-Backup
  auf dem Server. Ohne ihn und ohne Gerät sind private Daten weg (gewollt).
- Neues Gerät: mit dem Wiederherstellungsschlüssel, später auch per QR-Code.

## Etappen (Stand Backend)
| Etappe | Inhalt | Stand |
|---|---|---|
| B0 | GCP-Grundlagen, Budget, Kill-Switch, EU-Logs | fertig |
| B1 | Go-Server auf Cloud Run, Google-Anmeldung, Geräte sperren | fertig, in der Cloud getestet |
| B2 | Schlüsselmodell, Schlüssel-Backup, Kotlin-Client `client/` | fertig, in der Cloud getestet (2026-11-02) |
| **B2b** | **Daten-Backup für eine Person**, Vorbereitung der App | Entwurf bestätigt 2026-11-02 |
| B3 | Synchronisation (Geräte, Personen) | später |
| B6 | Play Integrity, Datenschutzerklärung, vor echten Daten | später |

## Was B2b für die App bedeutet
- **Nutzlast = Room-Datenbank** (Kopie per `VACUUM INTO`, gzip), verschlüsselt mit dem
  Tresorschlüssel. Keine Fotos, kein Audio. Beim Wiederherstellen laufen die normalen
  Room-Migrationen. Dazu die Einstellungen als kleines JSON (`Settings.backup()`), ohne
  *Notaus* und Schnittstellen-Schalter (Anf. 7.28 PS8a).
- **Ein sicherndes Gerät** (bis zum Sync in B3); höchstens alle 15 min, nur bei
  Änderungen und mit Netz (WorkManager). Höchstens 8 MiB.
- Server hält die letzten Fassungen (3 ältere, 30 Tage); Schutz gegen ältere
  Fassungen über einen Zähler im Chiffrat.
- **Wiederherstellen nur auf ausdrücklichen Wunsch**, mit Anzeige „Sicherung vom … von
  Gerät …“, nie automatisch über lokale Daten.
- Die App zeigt, wann die letzte Sicherung erfolgreich war. Das lokale Backup vor
  Migrationen bleibt; ein Export als Datei bleibt die eigentliche Sicherung.
- **Einbindung:** Modul `verbindung` aus `client/` per Gradle Composite Build
  (`includeBuild`), Anmeldung über den Credential Manager, Schlüssel im Android
  Keystore. Dafür kommt ein eigener kurzer Entwurf in **diesem** Repo, sobald B2b
  fertig ist.
- Test zuerst nur in einer **eigenen App-Variante** über den internen Test-Track
  (Kevin und wenige Bekannte).

## Regeln für Claude in diesem Repo
- Inhalte aus Kevins App gehen nie ins Backend-Repo, auch nicht als Testdaten.
- Ab der „Sicherheitsphase“ des Backends (erste echte Daten) sieht Claude keine
  Wiederherstellungsschlüssel und Keysets mehr; das gilt dann auch für Tests der App
  gegen das Backend.
