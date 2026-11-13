"""Download the exact licensed modern front/back photo references; stop on rate limiting."""
from pathlib import Path
import hashlib
import json
import shutil
import time
import requests

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / "data/raw/commons"

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    (DEST / "modern-pairs").mkdir(exist_ok=True)
    manifest = json.loads((ROOT / "data/fixtures/modern-clothing/manifest.json").read_text(encoding="utf-8"))
    expected = {photo["downloadUrl"]: photo["originalSha256"] for item in manifest["items"]
                if item["sourceGroup"] == "Wikimedia-Commons" for photo in (item["front"], item["back"])}
    for name in ("commons-candidates.json", "commons-more.json"):
        source = ROOT / "data/sources" / name
        metadata = json.loads(source.read_text(encoding="utf-8"))
        for page in metadata["query"]["pages"].values():
            if not page.get("imageinfo"):
                continue
            info = page["imageinfo"][0]
            target = DEST / "modern-pairs" / page["title"].removeprefix("File:")
            url = info["url"]
            if not target.exists():
                time.sleep(20)
                response = requests.get(url, headers={"User-Agent": "FitCheckAI-DatasetCuration/1.0 (public licensed garment fixtures)"}, timeout=90)
                if response.status_code == 429:
                    raise SystemExit("Source rate limit. Wait at least Retry-After=" + response.headers.get("Retry-After", "600") + " seconds before retrying.")
                response.raise_for_status()
                if hashlib.sha256(response.content).hexdigest() != expected[url]:
                    raise SystemExit("Source bytes changed; re-review the photo before accepting: " + page["title"])
                target.write_bytes(response.content)
            assert hashlib.sha256(target.read_bytes()).hexdigest() == expected[url]
            print("Verified", target.name, flush=True)
        shutil.copy2(source, DEST / name)
    for review in (ROOT / "data/sources").glob("pair-reviewed-*.json"):
        shutil.copy2(review, DEST / review.name)

if __name__ == "__main__":
    main()
