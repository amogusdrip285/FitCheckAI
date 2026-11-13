"""Check debug-app Room/media/profile durability over explicit force-stops. Never uninstalls/clears data.

Private DB snapshots stay under ignored build/outputs/track-a-device. Do not publish those files.
UI onboarding/navigation and physical-device quality remain separate manual checks.
Run with the app idle and do not edit data during collection. Each label creates a new evidence directory.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sqlite3
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.fitcheckai"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--force-stops", type=int, default=0)
    parser.add_argument("--check-media", action="store_true")
    parser.add_argument("--check-profile", action="store_true",
                        help="Verify saved profile and PERSON hashes before and after each restart; keep only aggregate evidence")
    parser.add_argument("--label", default="check")
    args = parser.parse_args()
    if not 0 <= args.force_stops <= 10 or not args.label.replace("-", "").isalnum():
        raise SystemExit("Invalid count/label")
    if not re.fullmatch(r"[A-Za-z]\w*(?:\.[A-Za-z]\w*)+", args.package, re.ASCII):
        parser.error("Invalid app package")
    output = ROOT / "build/outputs/track-a-device" / args.label
    if output.exists():
        parser.error("Evidence label already exists; choose a new --label to avoid mixing old WAL/snapshots")
    output.mkdir(parents=True)
    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=60)
    def read_media(ref, scope):
        assert ref["scope"] == scope
        relative = PurePosixPath(ref["localRelativePath"])
        assert not relative.is_absolute() and ".." not in relative.parts
        prefixes = ("media/GARMENT/", "wardrobe/") if scope == "GARMENT" else ("media/PERSON/",)
        assert str(relative).startswith(prefixes)
        assert all(c.isalnum() or c in "-_/ ." for c in str(relative))
        base = "files/" if scope == "GARMENT" else "no_backup/"
        data = adb("exec-out", "run-as", args.package, "cat", base + str(relative))
        assert len(data) == ref["byteCount"]
        assert hashlib.sha256(data).hexdigest() == ref["contentHash"]
    def profile_snapshot():
        raw = adb("exec-out", "run-as", args.package, "cat", "no_backup/profile/profile.json")
        profile = json.loads(raw)
        images = profile["bodyImages"]
        for view, ref in images.items():
            assert ref["ownerEntityId"] == profile["id"] and ref["view"] == view.upper()
            read_media(ref, "PERSON")
        return hashlib.sha256(raw).digest(), len(images)
    def snapshot(index):
        destination = output / str(index)
        destination.mkdir(exist_ok=True)
        names = adb("shell", "run-as", args.package, "ls", "databases").decode().split()
        for name in ("fitcheck.db", "fitcheck.db-wal", "fitcheck.db-shm"):
            if name in names:
                (destination / name).write_bytes(adb("exec-out", "run-as", args.package, "cat", "databases/" + name))
        with sqlite3.connect(destination / "fitcheck.db") as db:
            assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",), "Snapshot is inconsistent; rerun with a new label while app is idle"
            db.execute("BEGIN")
            rows = db.execute("SELECT id, revision, deletedAt, payload FROM wardrobe_v2 ORDER BY id").fetchall()
            legacy = db.execute("SELECT id,wearCount,lastWornAtMillis FROM clothing_items ORDER BY id").fetchall()
        return rows, legacy
    before = snapshot(0)
    before_profile = profile_snapshot() if args.check_profile else None
    for index in range(1, args.force_stops + 1):
        adb("shell", "am", "force-stop", args.package)
        adb("shell", "am", "start", "-W", "-n", args.package + "/com.fitcheckai.app.MainActivity")
        time.sleep(2)
        assert snapshot(index) == before, "Persistent rows changed after force-stop"
        if before_profile is not None:
            assert profile_snapshot() == before_profile, "Persistent profile or photos changed after force-stop"
        print("PASS force-stop", index, flush=True)
    items = [json.loads(row[3]) for row in before[0] if row[2] is None]
    media_count = 0
    if args.check_media:
        for item in items:
            for ref in item["images"].values():
                read_media(ref, "GARMENT")
                media_count += 1
    report = {"result": "PASS", "serial": args.serial, "forceStops": args.force_stops,
              "activeItems": len(items), "tombstones": sum(row[2] is not None for row in before[0]),
              "legacyRows": len(before[1]), "mediaHashesChecked": media_count,
              "scope": "Storage/bytes only. Does not certify physical hardware, AI quality or full B/C/D journey."}
    if before_profile is not None:
        report.update(profileStable=True, personMediaHashesChecked=before_profile[1])
    (output / "summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report), flush=True)

if __name__ == "__main__":
    main()
