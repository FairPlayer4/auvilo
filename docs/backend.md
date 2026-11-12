# Backend – Überblick aus Sicht der App

> Stand: 2026-11-07. Zusammenfassung von `../avlreminder-backend` (GitHub
> `FairPlayer4/avlreminder-backend`, privat). Maßgeblich sind die Dokumente dort:
> `docs/anforderungen.md`, `docs/stand.md`, `docs/b2b-entwurf.md`, `docs/formate.md`.
> Hier steht nur, was die App betrifft. Anforderungen: [anforderungen.md](anforderungen.md) 7.28.
> Die Einbindung in die App: [backup-entwurf.md](backup-entwurf.md) (bestätigt 2026-11-07).

## Grundsätze
- **Lernprojekt.** Ziel: Backup, später Sync zwischen eigenen Geräten und mit anderen
  Personen. Das Backup wird gerade in die App eingebunden (backup-entwurf.md).
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
- Neues Gerät: mit dem Wiederherstellungsschlüssel, später auch per QR-Code. Ein neues
  Gerät darf erst nach einer Freischaltung etwas ändern (E-B2b-13): durch ein
  vertrautes Gerät oder mit dem Wiederherstellungsschlüssel.

## Etappen (Stand Backend)
| Etappe | Inhalt | Stand |
|---|---|---|
| B0 | GCP-Grundlagen, Budget, Kill-Switch, EU-Logs | fertig |
| B1 | Go-Server auf Cloud Run, Google-Anmeldung, Geräte sperren | fertig, in der Cloud getestet |
| B2 | Schlüsselmodell, Schlüssel-Backup, Kotlin-Client `client/` | fertig, in der Cloud getestet (2026-11-02) |
| **B2b** | **Daten-Backup für eine Person**, Diagnose-Log, Vorbereitung der App | Backend fertig und in der Cloud getestet (2026-11-07); Einbindung in die App: [backup-entwurf.md](backup-entwurf.md) |
| B3 | Synchronisation (Geräte, Personen) | später |
| B6 | Play Integrity, Datenschutzerklärung | später |

## Was B2b für die App bedeutet
- **Nutzlast:** die Room-Datenbank (Kopie per `VACUUM INTO`) und die Einstellungen als
  kleines JSON (`Settings.backup()`), ohne *Notaus* und Schnittstellen-Schalter (Anf.
  7.28 PS8a). Beides zusammen als ZIP, verschlüsselt mit dem Tresorschlüssel. Keine
  Fotos, kein Audio. Beim Wiederherstellen laufen die normalen Room-Migrationen.
- **Ein sicherndes Gerät** (bis zum Sync in B3): Stammt die letzte Sicherung von einem
  anderen Gerät, fragt die App einmal, ob dieses Gerät übernimmt; bei „nein“ ruht das
  automatische Sichern. Gesichert wird **höchstens stündlich**, nur bei Änderungen und
  mit Netz (WorkManager), dazu von Hand. Höchstens 8 MiB, höchstens 48 Sicherungen am Tag.
- Der Server hält ältere Fassungen **7 Tage** (höchstens 200); Schutz gegen ältere
  Fassungen über einen Zähler im Chiffrat.
- **Wiederherstellen nur auf ausdrücklichen Wunsch**, mit Anzeige „Sicherung vom … von
  Gerät …“, nie automatisch über lokale Daten.
- Die App zeigt, wann die letzte Sicherung erfolgreich war. Das lokale Backup vor
  Migrationen bleibt; ein Export als Datei ist später nachrüstbar (anforderungen.md 8.4).
- **Einbindung:** Modul `verbindung` aus `client/` per Gradle Composite Build
  (`includeBuild`), Anmeldung über den Credential Manager, Schlüssel im Android
  Keystore. Einzelheiten in [backup-entwurf.md](backup-entwurf.md).
- **Keine eigene App-Variante, solange nur Kevin die App nutzt** (backup-entwurf.md
  A-1): Das Backup kommt in den normalen Build hinter den Schalter aus PS8. Eine eigene
  Variante und der interne Test-Track kommen mit dem ersten Tester.
- **Diagnose-Log ans Backend** nur mit einem eigenen Logger ohne Inhalte (keine Titel,
  keine freien Texte), nicht `diag.log` (backup-entwurf.md A-12).

## Regeln für Claude in diesem Repo
- Inhalte aus Kevins App gehen nie ins Backend-Repo, auch nicht als Testdaten.
- **Eigenbetrieb** (entschieden 2026-11-07, Backend-`CLAUDE.md`): Solange nur Kevin die
  App gegen die Cloud nutzt, darf Claude Wiederherstellungsschlüssel, Keysets und
  Sitzungs-Token sehen, wenn eine Aufgabe es braucht. Sie gelten als bekannt.
- **Sicherheitsphase:** beginnt vor dem ersten Schritt, bei dem jemand außer Kevin die
  App gegen die Cloud nutzt (Teilen, Tester, Play Store). Claude kündigt sie vorher
  ausdrücklich an. Dann wird das Backend zurückgesetzt, Kevin richtet die App mit neuen
  Schlüsseln ein, und Claude sieht diese Werte nicht mehr – auch nicht bei Tests der App
  gegen das Backend.
