"""Check debug-app Room/media durability over explicit force-stops. Never uninstalls/clears data.

Private DB snapshots stay under ignored build/outputs/track-a-device. Do not publish those files.
UI onboarding/navigation and physical-device quality remain separate manual checks.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import sqlite3
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.fitcheckai"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--force-stops", type=int, default=0)
    parser.add_argument("--check-media", action="store_true")
    parser.add_argument("--label", default="check")
    args = parser.parse_args()
    if not 0 <= args.force_stops <= 10 or not args.label.replace("-", "").isalnum():
        raise SystemExit("Invalid count/label")
    output = ROOT / "build/outputs/track-a-device" / args.label
    output.mkdir(parents=True, exist_ok=True)
    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=60)
    def snapshot(index):
        destination = output / str(index)
        destination.mkdir(exist_ok=True)
        names = adb("shell", "run-as", PACKAGE, "ls", "databases").decode().split()
        for name in ("fitcheck.db", "fitcheck.db-wal", "fitcheck.db-shm"):
            if name in names:
                (destination / name).write_bytes(adb("exec-out", "run-as", PACKAGE, "cat", "databases/" + name))
        with sqlite3.connect(destination / "fitcheck.db") as db:
            rows = db.execute("SELECT id, revision, deletedAt, payload FROM wardrobe_v2 ORDER BY id").fetchall()
            legacy = db.execute("SELECT id,wearCount,lastWornAtMillis FROM clothing_items ORDER BY id").fetchall()
        return rows, legacy
    before = snapshot(0)
    for index in range(1, args.force_stops + 1):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "am", "start", "-n", PACKAGE + "/com.fitcheckai.app.MainActivity")
        time.sleep(2)
        assert snapshot(index) == before, "Persistent rows changed after force-stop"
        print("PASS force-stop", index, flush=True)
    items = [json.loads(row[3]) for row in before[0] if row[2] is None]
    media_count = 0
    if args.check_media:
        for item in items:
            for ref in item["images"].values():
                assert ref["scope"] == "GARMENT"
                relative = PurePosixPath(ref["localRelativePath"])
                assert not relative.is_absolute() and ".." not in relative.parts
                assert str(relative).startswith(("media/GARMENT/", "wardrobe/"))
                assert all(c.isalnum() or c in "-_/ ." for c in str(relative))
                data = adb("exec-out", "run-as", PACKAGE, "cat", "files/" + str(relative))
                assert len(data) == ref["byteCount"]
                assert hashlib.sha256(data).hexdigest() == ref["contentHash"]
                media_count += 1
    report = {"result": "PASS", "serial": args.serial, "forceStops": args.force_stops,
              "activeItems": len(items), "tombstones": sum(row[2] is not None for row in before[0]),
              "legacyRows": len(before[1]), "mediaHashesChecked": media_count,
              "scope": "Storage/bytes only. Does not certify physical hardware, AI quality or full B/C/D journey."}
    (output / "summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report), flush=True)

if __name__ == "__main__":
    main()
