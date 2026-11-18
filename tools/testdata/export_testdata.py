"""Realistic test data from the phone, with every free text replaced (privacy) and no media.

Usage (Git Bash, phone connected, debug build installed):
    python tools/testdata/export_testdata.py testdata/real-<date>.json

What it does:
- copies databases/avlreminder.db (+ -wal, -shm) via `adb exec-out run-as` into a temporary
  directory, reads it and deletes the copy again (CLAUDE.md: the database only goes to a
  temporary directory);
- writes all tables as JSON rows with the schema version, so a test can load them into the
  schema of that version and migrate them (app: RealDataTest);
- replaces every free text (titles, why, goal, minimum, checklist items, notes, proof texts,
  "Startklar" answers, blockers, template names, place names, WLAN names) with filler of the
  same length.
  Fixed words with a meaning in the app ("Ja, sofort", skip reasons, resolutions) stay;
- drops media (photo and audio paths, motivation audio), the suggestions (Anf. 7.24) and the
  coordinates of places (moved to a made-up spot) and the BSSIDs of their WLANs (made-up
  ones, Anf. 7.29: WLAN names and BSSIDs are private);
- at the end checks that no original free text of 4 or more characters is left in the output.

Times, schedules, states and counts stay as they are: they are what makes the data realistic.
"""
import hashlib
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile

PACKAGE = "com.avlreminder"
FILES = ["avlreminder.db", "avlreminder.db-wal", "avlreminder.db-shm"]

# Texts with a meaning in the app (core: SkipReasons, Lifecycle, Proofs.READY_YES, Resolution).
KEEP = {
    "Wochenziel erreicht", "pausiert", "Zeitplan geändert", "verworfen", "Notaus",
    "Blocker gelöst", "Ja, sofort", "Ankunft", "Zuhause",
    "GOAL", "MINIMUM", "NO_PROOF", "MISSED", "SKIPPED", "DROPPED",
}

# Free text per table and column; the prefix keeps the data readable in a test.
FREE = {
    "reminder": {"title": "Reminder", "why": "Warum", "goal": "Ziel", "minimum": "Minimum"},
    "checklist_item": {"title": "Punkt"},
    "occurrence": {"note": "Notiz"},
    "proof": {"text": "Nachweis"},
    "prep_answer": {"text": "Antwort"},
    "blocker": {"text": "Blocker"},
    "template": {"name": "Vorlage", "minimum": "Minimum"},
    "place": {"name": "Ort"},
    "place_wifi": {"ssid": "WLAN"},
}
MEDIA = {"reminder": ["motivationAudio"], "proof": ["filePath"]}
DROP_TABLES = {"suggestion", "room_master_table", "android_metadata", "sqlite_sequence"}

WORDS = ("lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor "
         "incididunt ut labore et dolore magna aliqua enim ad minim veniam quis nostrud").split()


def filler(prefix, key, length):
    """Neutral text of about [length] characters, the same for the same [key]."""
    seed = int(hashlib.sha256(key.encode()).hexdigest(), 16)
    text = prefix
    while len(text) < length:
        text += " " + WORDS[seed % len(WORDS)]
        seed //= len(WORDS)
        if seed == 0:
            seed = int(hashlib.sha256((key + text).encode()).hexdigest(), 16)
    return text[:max(length, len(prefix))].rstrip()


def is_system_detail(text):
    """Event details the engine writes: minutes, instants, resolutions, fixed words."""
    if text in KEEP:
        return True
    if text.endswith(" min") and text[:-4].isdigit():
        return True
    # A "Rest auf morgen" that counted as a postponement (Lifecycle.COUNTED, Z2b).
    if text.endswith(" counted"):
        text = text[: -len(" counted")]
    # ISO instants like 2026-11-04T19:30:00Z
    return len(text) >= 20 and text[4] == "-" and text[10] == "T" and text.endswith("Z")


def pull(tmp):
    for name in FILES:
        target = os.path.join(tmp, name)
        with open(target, "wb") as out:
            result = subprocess.run(
                ["adb", "exec-out", "run-as", PACKAGE, "cat", f"databases/{name}"], stdout=out, stderr=subprocess.PIPE,
            )
        if result.returncode != 0 or os.path.getsize(target) == 0:
            os.remove(target)
            if name == FILES[0]:
                raise SystemExit(f"could not read {name}: {result.stderr.decode(errors='replace')}")


def export(db_path):
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    version = con.execute("PRAGMA user_version").fetchone()[0]
    tables = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
    originals = set()
    out = {}
    for table in tables:
        if table in DROP_TABLES:
            continue
        rows = []
        for row in con.execute(f"SELECT * FROM {table} ORDER BY id"):
            r = dict(row)
            key = f"{table}:{r.get('id')}"
            for col, prefix in FREE.get(table, {}).items():
                value = r.get(col)
                if value is None or value in KEEP:
                    continue
                originals.add(value)
                r[col] = filler(f"{prefix} {r.get('id')}", f"{key}:{col}", len(value))
            for col in MEDIA.get(table, []):
                if r.get(col) is not None:
                    r[col] = None
            if table == "event" and r.get("detail") is not None and not is_system_detail(r["detail"]):
                originals.add(r["detail"])
                r["detail"] = filler("Detail", f"{key}:detail", len(r["detail"]))
            if table == "place":
                r["lat"] = round(52.52 + 0.01 * r["id"], 4)
                r["lon"] = 13.405
            if table == "place_wifi":
                originals.add(r["bssid"])
                # Locally administered, so never a real access point; unique per row.
                r["bssid"] = f"02:00:00:00:{r['id'] // 256 % 256:02x}:{r['id'] % 256:02x}"
            rows.append(r)
        out[table] = rows
    con.close()
    return version, out, originals


def check_no_leak(text, originals):
    # An original that is itself one of our filler words (e.g. a title "Reminder") is no leak.
    ours = {p for cols in FREE.values() for p in cols.values()} | set(WORDS) | {"Detail"}
    leaks = [o for o in originals if len(o.strip()) >= 4 and o.strip() not in KEEP and o.strip() not in ours and o.strip() in text]
    if leaks:
        # Where, not what: the text itself stays private.
        for o in leaks:
            fields = sorted(set(re.findall(r'"(\w+)": "[^"]*' + re.escape(o.strip()), text)))
            print(f"leak: {len(o.strip())} chars in field(s) {fields or '?'}")
        raise SystemExit(f"{len(leaks)} original text(s) still in the output, nothing written")


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    target = sys.argv[1]
    tmp = tempfile.mkdtemp(prefix="avl-db-")
    try:
        pull(tmp)
        version, tables, originals = export(os.path.join(tmp, FILES[0]))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    data = {
        "about": "Kevins Daten, pseudonymisiert: alle freien Texte ersetzt, keine Medien, keine Vorschläge, Orte verschoben",
        "schemaVersion": version,
        "tables": tables,
    }
    text = json.dumps(data, ensure_ascii=False, indent=1)
    check_no_leak(text, originals)
    os.makedirs(os.path.dirname(target) or ".", exist_ok=True)
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write(text + "\n")
    counts = ", ".join(f"{t} {len(r)}" for t, r in tables.items())
    print(f"schema {version}: {counts} -> {target}")
    print("temporary copy deleted:", not os.path.exists(tmp))


if __name__ == "__main__":
    main()
