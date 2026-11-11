"""Prepare the reviewed contemporary-garment fixtures, rights manifest and negative cases.

Raw CC0 source must be audited first. --commons-dir optionally adds downloaded, licensed pairs.
The selected pixels are real photographs; only the explicitly marked blur test is degraded.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import html
import json
from pathlib import Path
import re
import uuid

from PIL import Image, ImageFilter, ImageOps
from prepare_modern_clothing import ROOT, RAW, COMMIT, SOURCE, CATEGORIES, write_json

# Visual review of source photos: category, visible colors, pattern only. No material/season guesses.
SELECTION = [
    ("b0c03127", "Áo sơ mi kẻ đỏ đen", "TOP", ["RED", "BLACK"], "CHECKED"),
    ("09f4ffb6", "Áo sơ mi trắng", "TOP", ["WHITE"], "SOLID"),
    ("ea7b6656", "Áo thun tím hồng", "TOP", ["PURPLE"], "SOLID"),
    ("97074b7f", "Quần jeans xanh", "BOTTOM", ["BLUE"], "SOLID"),
    ("c4f11a12", "Quần xám", "BOTTOM", ["GRAY"], "SOLID"),
    ("c995c900", "Quần thể thao tối màu", "BOTTOM", ["BLUE", "WHITE"], "OTHER"),
    ("e3c8e575", "Đầm hoa nền trắng", "DRESS", ["WHITE", "PINK", "GREEN"], "PRINTED"),
    ("e6a8b33f", "Đầm xanh đậm", "DRESS", ["BLUE"], "SOLID"),
    ("21059bce", "Đầm xanh lá", "DRESS", ["GREEN"], "OTHER"),
    ("ff231d91", "Áo khoác xanh ô liu", "OUTERWEAR", ["GREEN"], "SOLID"),
    ("44bf7760", "Áo khoác xám sáng", "OUTERWEAR", ["GRAY"], "SOLID"),
    ("43ac2647", "Áo khoác đen", "OUTERWEAR", ["BLACK"], "SOLID"),
    ("bf78527f", "Giày đen đế trắng", "SHOES", ["BLACK", "WHITE"], "SOLID"),
    ("61bbb3a2", "Giày xanh đậm", "SHOES", ["BLUE"], "SOLID"),
    ("b7935371", "Giày bệt trắng", "SHOES", ["WHITE"], "OTHER"),
    ("b6899eaa", "Mũ len đen", "ACCESSORY", ["BLACK"], "SOLID"),
    ("70731fd8", "Mũ lưỡi trai xanh", "ACCESSORY", ["BLUE", "YELLOW"], "PRINTED"),
    ("2d6afa47", "Mũ len có logo", "ACCESSORY", ["BLACK"], "OTHER"),
]
PAIRS = [
    ("ddc", "Áo thun DDC trắng", "DDC International T-shirt front.jpg", "DDC International T-shirt back.jpg", "Jonathan Schilling"),
    ("wlm", "Áo thun Wiki Loves Monuments", "WLM shirt - Female (front).jpg", "WLM shirt - Female (back).jpg", "Béria L. Rodríguez @ Wikimedia Commons"),
    ("wikipedia10", "Áo thun Wikipedia trắng", "10 years of polish Wikipedia, event T-shirt front side.JPG", "10 years of polish Wikipedia, event T-shirt back side.JPG", "Juandev"),
]
DEST = ROOT / "data/fixtures/modern-clothing"


def annotation_metadata(raw_label=None, pair=False):
    """Reference annotations are disclosed per field; none has independent adjudication yet."""
    provenance = {
        "name": {"method": "assistant_authored_display_name"},
        "category": {"method": "source_label_mapped_and_assistant_visual_review" if raw_label else "assistant_visual_review"},
        "colors": {"method": "assistant_visual_review"},
        "pattern": {"method": "assistant_visual_review"},
    }
    if raw_label:
        provenance["category"]["sourceLabel"] = raw_label
    if pair:
        provenance["pairIdentity"] = {"method": "source_view_descriptions_plus_assistant_visual_comparison"}
    return {"groundTruthFields": [], "referenceFields": ["category", "colors", "pattern"] + (["pairIdentity"] if pair else []),
            "independentReviewStatus": "PENDING", "fieldProvenance": provenance}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def photo(source, filename, metadata):
    target = DEST / "images" / filename
    target.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source) as image:
        converted = ImageOps.exif_transpose(image).convert("RGB")
        converted.thumbnail((1536, 1536), Image.Resampling.LANCZOS)
        converted.save(target, "JPEG", quality=92, optimize=True)
        width, height = converted.size
    return {"file": "images/" + filename, "sha256": sha(target), "originalSha256": sha(source),
            "mimeType": "image/jpeg", "width": width, "height": height, "byteCount": target.stat().st_size,
            "changes": "EXIF orientation applied; resized to max 1536 px; JPEG re-encoded; metadata stripped.", **metadata}


def main(commons_dir):
    rows = list(csv.DictReader((RAW / "images.csv").open(encoding="utf-8")))
    items = []
    for prefix, name, category, colors, pattern in SELECTION:
        row = next(row for row in rows if row["image"].startswith(prefix))
        assert CATEGORIES.get(row["label"]) == category
        source = next((RAW / "images").glob(prefix + "*"))
        identity = str(uuid.uuid5(uuid.NAMESPACE_URL, SOURCE + "/" + row["image"]))
        front = photo(source, identity + "-front.jpg", {
            "sourceUrl": f"{SOURCE}/blob/{COMMIT}/images/{source.name}", "sourceId": row["image"],
            "license": "CC0-1.0", "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/",
            "attribution": "Alexey Grigorev clothing-dataset contributors / Tagias",
            "viewEvidence": "Visual reference/product view; the source has no explicit front/back annotation."})
        items.append({"id": identity, "name": name, "category": category, "colors": colors, "pattern": pattern,
                      "material": None, "formality": None, "seasonTags": [], "styleTags": [],
                      "front": front, "back": None, "pairVerified": False,
                      "annotationStatus": "assistant_visual_review_not_independently_human_verified",
                      **annotation_metadata(row["label"]), "sourceGroup": "CC0-clothing-dataset"})
    pending = []
    records = {}
    if commons_dir:
        for name in ("commons-candidates.json", "commons-more.json"):
            path = commons_dir / name
            if path.exists():
                for page in json.loads(path.read_text(encoding="utf-8")).get("query", {}).get("pages", {}).values():
                    if page.get("imageinfo"):
                        records[page["title"].removeprefix("File:")] = page["imageinfo"][0]
    for key, name, front_name, back_name, credit in PAIRS:
        if not commons_dir or not all((commons_dir / "modern-pairs" / f).is_file() and f in records for f in (front_name, back_name)):
            pending.append(key); continue
        # Pair admission also requires explicit human/assistant visual check of both local files.
        checked = commons_dir / ("pair-reviewed-" + key + ".json")
        if not checked.exists():
            pending.append(key + ":visual_review_pending"); continue
        review = json.loads(checked.read_text(encoding="utf-8"))
        identity = str(uuid.uuid5(uuid.NAMESPACE_URL, "fitcheck-commons:" + key))
        views = {}
        for view, filename in (("front", front_name), ("back", back_name)):
            info = records[filename]; meta = info["extmetadata"]
            views[view] = photo(commons_dir / "modern-pairs" / filename, identity + "-" + view + ".jpg", {
                "sourceUrl": info["descriptionurl"], "downloadUrl": info["url"],
                "license": meta["LicenseShortName"]["value"], "licenseUrl": meta["LicenseUrl"]["value"],
                "attribution": credit, "attributionUrl": "https://commons.wikimedia.org/wiki/User:Beria" if key == "wlm" else info["descriptionurl"],
                "restrictions": html.unescape(re.sub("<[^>]+>", "", meta.get("Restrictions", {}).get("value", ""))),
                "viewEvidence": "Source description explicitly identifies view; paired files visually checked."})
        items.append({"id": identity, "name": name, "category": "TOP", "colors": review["colors"], "pattern": "PRINTED",
                      "material": None, "formality": None, "seasonTags": [], "styleTags": [], **views, "pairVerified": True,
                      "annotationStatus": "source_pair_plus_assistant_visual_review_not_independently_human_verified",
                      **annotation_metadata(pair=True), "sourceGroup": "Wikimedia-Commons"})
    for item in items:
        for view in ("front", "back"):
            if item.get(view):
                assert (DEST / item[view]["file"]).is_file()
                assert item[view]["sha256"] == sha(DEST / item[view]["file"])
    variants = DEST / "variants"
    variants.mkdir(exist_ok=True)
    with Image.open(DEST / items[0]["front"]["file"]) as image:
        image.filter(ImageFilter.GaussianBlur(9)).save(variants / "blurry.jpg", quality=75)
    cases = [
        {"id": "front_only", "itemId": items[0]["id"], "expected": "Save allowed; back absent, never invented."},
        {"id": "missing_front", "front": None, "back": items[1]["front"]["file"], "expected": "INVALID_REQUEST"},
        {"id": "mismatched_pair", "front": items[0]["front"]["file"], "back": items[3]["front"]["file"],
         "expected": "Manual/provider review required; distinct-file validation alone cannot prove garment identity."},
        {"id": "same_photo_both_views", "front": items[0]["front"]["file"], "back": items[0]["front"]["file"], "expected": "INVALID_REQUEST"},
        {"id": "blurred_real_photo", "file": "variants/blurry.jpg", "derivedFrom": items[0]["id"],
         "syntheticDegradation": True, "expected": "Allow manual entry; never claim reliable AI labels from blur."},
        {"id": "missing_file", "file": "images/does-not-exist.jpg", "expected": "IO; preserve existing data and allow replacement."},
    ]
    write_json(DEST / "manifest.json", {"schemaVersion": 2, "collection": "Modern clothing reference fixtures",
        "bulkSourceRevision": COMMIT, "items": items, "pendingVerifiedPairs": pending,
        "limits": "Functional test fixtures, not a statistically validated AI benchmark; photo dates/styles are not 2026 trend claims."})
    write_json(DEST / "cases.json", cases)
    print(json.dumps({"items": len(items), "photos": sum(1 + bool(i["back"]) for i in items),
                      "verifiedPairs": sum(i["pairVerified"] for i in items), "pendingPairs": pending}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--commons-dir", type=Path)
    main(parser.parse_args().commons_dir)
