"""Feste Mutations-Suite (design.md 14, V1.3c14 U7).

Jeder Mutant baut einen plausiblen Fehler in eine Stelle ein, die schon einmal kaputt war oder
die nur über Android-Verdrahtung erreichbar ist, und nennt die Tests, die dann rot werden müssen.
Bleiben sie grün, sichert kein Test die Stelle ab: ein Fund.

Aufruf (Git Bash, aus dem Repo, mit Netz wie der Build):
    python -I -X utf8 tools/mutate.py            # Gegenprobe und alle Mutanten
    python -I -X utf8 tools/mutate.py M3 M7      # nur diese
    python -I -X utf8 tools/mutate.py --list

Ablauf: zuerst die Gegenprobe (alle betroffenen Tests unverändert grün, mit echter Testzahl),
dann je Mutant: Datei ändern, nur die genannten Tests laufen lassen, Datei aus dem Speicher
zurückschreiben. Dateien mit uncommitteten Änderungen fasst das Skript nicht an.
Vor jedem PR nach `main` laufen lassen; jede Etappe bringt Mutanten für ihre neuen Wege mit.
"""
from __future__ import annotations

import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOGS = ROOT / "build" / "mutate"
APP = "app/src/main/java/com/avlreminder/"
CORE = "core/src/main/kotlin/com/avlreminder/core/"


@dataclass(frozen=True)
class Mutant:
    id: str
    why: str
    file: str
    old: str
    new: str
    tests: tuple[str, ...]  # "app:<Klasse>" oder "core:<Klasse>"


MUTANTS = [
    Mutant("M1", "Abbrechen nach der Dateiwahl löscht die angelegte Datei nicht (Lauf 7, Frage 2)",
           APP + "restore/LocalRestore.kt",
           "return if (file is ChosenFile.Export) discard(file) else Job().apply { complete() }",
           "return Job().apply { complete() }",
           ("app:com.avlreminder.restore.LocalRestoreTest",)),
    Mutant("M2", "Schreibfehler beim Export lässt die halbe Datei liegen",
           APP + "restore/LocalRestore.kt",
           "withContext(NonCancellable + Dispatchers.IO) { runCatching { delete() } }",
           "withContext(NonCancellable + Dispatchers.IO) { }",
           ("app:com.avlreminder.restore.LocalRestoreTest",)),
    Mutant("M3", "Lautstärketasten lösen auch bei weggescrollter Vorschau aus (Lauf 7, Frage 5)",
           APP + "alarm/ProofCamera.kt",
           "layout.boundsInWindow().height * 2 >= height",
           "layout.boundsInWindow().height >= 0",
           ("app:com.avlreminder.alarm.ProofCameraVisibilityTest",)),
    Mutant("M4", "Nach dem Standort fragt die App nicht gleich nach dem im Hintergrund (Lauf 7, Frage 4)",
           APP + "ui/MainActivity.kt",
           "val next = thenBackground && result[",
           "val next = false && result[",
           ("app:com.avlreminder.ui.MainActivityTest",)),
    Mutant("M5", "Doppeltipp auf Knöpfe mit Engine-Aufruf (E31)",
           APP + "ui/Components.kt",
           "        if (state.value) return\n",
           "",
           ("app:com.avlreminder.ui.IntroUiTest", "app:com.avlreminder.ui.PlaceSettingUiTest")),
    Mutant("M6", "Nach dem Neustart werden die Alarme nicht neu geplant",
           APP + "system/SystemReceiver.kt",
           "        Intent.ACTION_BOOT_COMPLETED,\n",
           "",
           ("app:com.avlreminder.alarm.ReceiverGlueTest",)),
    Mutant("M7", "Teilschritt aus der Benachrichtigung zählt als ganz erledigt",
           APP + "alarm/AndroidAlarmPort.kt",
           "ACTION_MINIMUM -> Action.Complete(minimumOnly = true)",
           "ACTION_MINIMUM -> Action.Complete(minimumOnly = false)",
           ("app:com.avlreminder.alarm.SessionGlueTest",)),
    Mutant("M8", "Erledigt in der Benachrichtigung auch bei Foto-Nachweis (Review-Lauf 6)",
           APP + "alarm/Notifications.kt",
           "?: Button.Done.takeIf { info.quickDone }",
           "?: Button.Done",
           ("app:com.avlreminder.alarm.AlarmGlueTest", "app:com.avlreminder.alarm.ReceiverGlueTest")),
    Mutant("M9", "Teilschritt der laufenden Sitzung trotz Nachweis (Review-Lauf 6, Antwort 2)",
           APP + "alarm/SessionService.kt",
           "minimum = view.quickMinimum,",
           "minimum = view.minimumAllowed,",
           ("app:com.avlreminder.alarm.SessionGlueTest",)),
    Mutant("M10", "Room zählt fürs Verschiebe-Limit Schlummern statt Verschieben (E32)",
           APP + "data/Dao.kt",
           "\"AND (event.type = 'POSTPONED' OR",
           "\"AND (event.type = 'SNOOZED' OR",
           ("app:com.avlreminder.data.RoomRepositoryCasesTest",)),
    Mutant("M11", "Probealarm eine Minute später als Doku und Text sagen (Lauf 7, Frage 6)",
           CORE + "rules/Presets.kt",
           "const val PROBE_MINUTES = 2L",
           "const val PROBE_MINUTES = 3L",
           ("core:com.avlreminder.core.usecases.TimeRulesFromAppTest",)),
    Mutant("M12", "Notaus „bis morgen früh“ um 06:00 selbst endet erst am Tag danach nicht",
           CORE + "rules/Escalation.kt",
           "if (local.toLocalTime() < MORNING_AT)",
           "if (local.toLocalTime() <= MORNING_AT)",
           ("core:com.avlreminder.core.usecases.TimeRulesFromAppTest",)),
    Mutant("M13", "„Heute Abend“ auch, wenn es gleich so weit ist",
           CORE + "usecases/AlarmView.kt",
           "if (evening > now.plus(Duration.ofMinutes(30)))",
           "if (evening > now)",
           ("core:com.avlreminder.core.usecases.TimeRulesFromAppTest",)),
    Mutant("M14", "Vorab verschieben über die Verpasst-Grenze hinaus",
           CORE + "usecases/AlarmView.kt",
           "if (missedAt == null || at < missedAt)",
           "if (true)",
           ("core:com.avlreminder.core.usecases.TimeRulesFromAppTest",)),
    Mutant("M15", "Standort-Hinweis geändert, Play-Erklärung nicht (U4)",
           APP + "ui/screens/LocationDisclosure.kt",
           "\"Standort und WLAN bleiben auf dem Handy und werden nicht weitergegeben.",
           "\"Standort und WLAN bleiben auf dem Handy.",
           ("app:com.avlreminder.DocQuoteTest",)),
]


def gradle_cmd() -> list[str]:
    if os.name == "nt":
        return [str(ROOT / "gradlew.bat")]
    return [str(ROOT / "gradlew")]


def results_dir(module: str) -> Path:
    return ROOT / module / "build" / "test-results" / ("testDebugUnitTest" if module == "app" else "test")


def run_tests(tests: tuple[str, ...], log: Path) -> tuple[int, dict[str, tuple[int, int]]]:
    """Runs [tests] once; returns Gradle's exit code and per class (tests, failures+errors)."""
    args = gradle_cmd() + ["-q", "--continue"]
    for module in ("core", "app"):
        classes = [t.split(":", 1)[1] for t in tests if t.startswith(module + ":")]
        if not classes:
            continue
        args.append(":app:testDebugUnitTest" if module == "app" else ":core:test")
        for c in classes:
            args += ["--tests", c]
        args.append("--rerun")
    started = time.time()
    with open(log, "w", encoding="utf-8", newline="\n") as out:
        code = subprocess.run(args, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT).returncode
    found: dict[str, tuple[int, int]] = {}
    for module in ("core", "app"):
        folder = results_dir(module)
        if not folder.is_dir():
            continue
        for xml in folder.glob("TEST-*.xml"):
            if xml.stat().st_mtime < started - 1:
                continue
            suite = ET.parse(xml).getroot()
            name = suite.get("name", "")
            n = int(suite.get("tests", 0))
            bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            t, b = found.get(name, (0, 0))
            found[name] = (t + n, b + bad)
    return code, found


def dirty(file: str) -> bool:
    out = subprocess.run(["git", "status", "--porcelain", "--", file], cwd=ROOT, capture_output=True, text=True).stdout
    return bool(out.strip())


def main(argv: list[str]) -> int:
    if "--list" in argv:
        for m in MUTANTS:
            print(f"{m.id:4} {m.why}\n     {m.file}\n     Tests: {', '.join(t.split('.')[-1] for t in m.tests)}")
        return 0
    chosen = [m for m in MUTANTS if not argv or m.id in argv]
    unknown = set(argv) - {m.id for m in MUTANTS}
    if unknown:
        print("Unbekannt:", ", ".join(sorted(unknown)))
        return 2
    for m in chosen:
        if dirty(m.file):
            print(f"{m.id}: {m.file} hat uncommittete Änderungen – erst committen.")
            return 2
        text = (ROOT / m.file).read_text(encoding="utf-8")
        if text.count(m.old) != 1:
            print(f"{m.id}: Stelle nicht genau einmal gefunden in {m.file} – Mutant an den Code anpassen.")
            return 2
    LOGS.mkdir(parents=True, exist_ok=True)

    all_tests = tuple(sorted({t for m in chosen for t in m.tests}))
    print(f"Gegenprobe: {len(all_tests)} Testklassen unverändert …", flush=True)
    code, found = run_tests(all_tests, LOGS / "gegenprobe.log")
    total = sum(n for n, _ in found.values())
    bad = sum(b for _, b in found.values())
    missing = [t for t in all_tests if t.split(":", 1)[1] not in found]
    if code != 0 or bad or total == 0 or missing:
        print(f"Gegenprobe NICHT grün: exit {code}, {total} Tests, {bad} rot, ohne Ergebnis: {missing}. Log: {LOGS / 'gegenprobe.log'}")
        return 1
    print(f"Gegenprobe grün: {total} Tests.")

    rows = []
    for m in chosen:
        path = ROOT / m.file
        original = path.read_bytes()
        text = original.decode("utf-8")
        try:
            path.write_bytes(text.replace(m.old, m.new).encode("utf-8"))
            log = LOGS / f"{m.id}.log"
            print(f"{m.id}: {m.why} …", flush=True)
            code, found = run_tests(m.tests, log)
        finally:
            path.write_bytes(original)
        targets = {t.split(":", 1)[1] for t in m.tests}
        ran = sum(found.get(t, (0, 0))[0] for t in targets)
        red = sum(found.get(t, (0, 0))[1] for t in targets)
        if red > 0:
            verdict = "entdeckt"
        elif code != 0 and ran == 0:
            verdict = "UNGÜLTIG (kompiliert nicht?)"
        else:
            verdict = "UNENTDECKT"
        rows.append((m, verdict, ran, red))
        print(f"   {verdict}: {red} von {ran} Tests rot", flush=True)

    if any(dirty(m.file) for m in chosen):
        print("WARNUNG: eine Datei ist nach dem Lauf nicht sauber – git diff prüfen!")
        return 1
    caught = sum(1 for _, v, _, _ in rows if v == "entdeckt")
    print(f"\nErgebnis: {caught}/{len(rows)} Mutanten entdeckt (Gegenprobe {total} Tests grün).")
    for m, v, ran, red in rows:
        if v != "entdeckt":
            print(f"  {m.id} {v}: {m.why} ({m.file}); Log {LOGS / (m.id + '.log')}")
    return 0 if caught == len(rows) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
