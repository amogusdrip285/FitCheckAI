"""Validate the shipped fixture bytes, rights, IDs, view roles and negative-case manifest."""
from pathlib import Path
import hashlib
import json
import uuid
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "data/fixtures/modern-clothing"

def main():
    data = json.loads((BASE / "manifest.json").read_text(encoding="utf-8"))
    ids, hashes, categories, pair_count, photos = set(), set(), set(), 0, 0
    for item in data["items"]:
        assert str(uuid.UUID(item["id"])) == item["id"] and item["id"] not in ids
        ids.add(item["id"]); categories.add(item["category"])
        assert item["material"] is None and item["formality"] is None
        assert item["seasonTags"] == [] and item["styleTags"] == []
        assert 0 < len(item["colors"]) <= 3
        assert item["front"] is not None
        assert item["groundTruthFields"] == [] and item["independentReviewStatus"] == "PENDING"
        assert {"category", "colors", "pattern"}.issubset(item["referenceFields"])
        assert item["fieldProvenance"]["colors"]["method"] == "assistant_visual_review"
        assert item["fieldProvenance"]["pattern"]["method"] == "assistant_visual_review"
        assert "name" in item["fieldProvenance"]
        if item["sourceGroup"] == "CC0-clothing-dataset":
            assert item["fieldProvenance"]["category"]["sourceLabel"]
        if item["pairVerified"]:
            assert item["back"] is not None and item["sourceGroup"] == "Wikimedia-Commons"
            assert item["front"]["sourceUrl"] != item["back"]["sourceUrl"]
            pair_count += 1
        for view in ("front", "back"):
            photo = item[view]
            if photo is None:
                continue
            path = (BASE / photo["file"]).resolve()
            assert path.is_relative_to(BASE.resolve())
            assert photo["license"] in ("CC0-1.0", "CC BY-SA 3.0", "CC BY-SA 4.0")
            assert photo["attribution"] and photo["sourceUrl"].startswith("https://")
            assert photo["licenseUrl"].startswith("https://creativecommons.org/")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            assert digest == photo["sha256"] and digest not in hashes
            hashes.add(digest)
            with Image.open(path) as image:
                image.load()
                assert image.size == (photo["width"], photo["height"])
                assert not image.getexif() and max(image.size) <= 1536
            assert path.stat().st_size == photo["byteCount"] <= 4 * 1024 * 1024
            photos += 1
    assert categories == {"TOP", "BOTTOM", "DRESS", "OUTERWEAR", "SHOES", "ACCESSORY"}
    assert len(ids) >= 6 and pair_count >= 3
    cases = json.loads((BASE / "cases.json").read_text(encoding="utf-8"))
    assert {"missing_front", "mismatched_pair", "blurred_real_photo"}.issubset({c["id"] for c in cases})
    print(f"PASS: {len(ids)} unique garments, {photos} real photos, {pair_count} verified pairs, 6 categories; hashes/rights/caps/negative cases valid.")

if __name__ == "__main__":
    main()
