"""Reproducible local audit of Alexey Grigorev's CC0 clothing photos.

pip install Pillow requests
python tools/data/prepare_modern_clothing.py --download
No model training or accuracy claim is made by this script.
"""
from __future__ import annotations
import argparse
import collections
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
COMMIT = "7ed86a9cc56763b6acb35e41cb35184e43a5d218"
SOURCE = "https://github.com/alexeygrigorev/clothing-dataset"
RAW = ROOT / "data/raw/clothing-dataset"
OUT = ROOT / "data/processed"
CATEGORIES = {
    "T-Shirt": "TOP", "Longsleeve": "TOP", "Shirt": "TOP", "Polo": "TOP",
    "Undershirt": "TOP", "Hoodie": "TOP", "Body": "TOP", "Top": "TOP", "Blouse": "TOP",
    "Pants": "BOTTOM", "Shorts": "BOTTOM", "Skirt": "BOTTOM", "Dress": "DRESS",
    "Outwear": "OUTERWEAR", "Blazer": "OUTERWEAR", "Shoes": "SHOES", "Hat": "ACCESSORY",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def download():
    import requests
    archive = ROOT / f"data/raw/clothing-dataset-{COMMIT[:12]}.zip"
    if not archive.exists():
        archive.parent.mkdir(parents=True, exist_ok=True)
        with requests.get(f"https://codeload.github.com/alexeygrigorev/clothing-dataset/zip/{COMMIT}",
                          stream=True, timeout=(15, 120)) as response:
            response.raise_for_status()
            temporary = archive.with_suffix(".partial")
            with temporary.open("wb") as target:
                for chunk in response.iter_content(1024 * 1024):
                    target.write(chunk)
            temporary.replace(archive)
    if not (RAW / "images.csv").exists():
        with zipfile.ZipFile(archive) as zipped:
            for entry in zipped.infolist():
                relative = Path(*Path(entry.filename).parts[1:])
                destination = (RAW / relative).resolve()
                if not destination.is_relative_to(RAW.resolve()):
                    raise ValueError("Archive path outside dataset")
                if entry.is_dir():
                    destination.mkdir(parents=True, exist_ok=True)
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(zipped.read(entry))
    write_json(ROOT / "data/raw/source.json", {"repo": SOURCE, "commit": COMMIT, "license": "CC0-1.0"})


def audit():
    rows = list(csv.DictReader((RAW / "images.csv").open(encoding="utf-8")))
    files = {p.stem: p for p in (RAW / "images").iterdir() if p.is_file()}
    accepted, excluded, hashes = [], [], collections.defaultdict(list)
    image_ids = set()
    for row in rows:
        identity = row["image"]
        image_ids.add(identity)
        path = files.get(identity)
        item = {"sourceId": identity, "sourceLabel": row["label"], "category": CATEGORIES.get(row["label"]),
                "license": "CC0-1.0", "sourceCommit": COMMIT}
        if not path:
            excluded.append({**item, "reason": "missing_image"}); continue
        try:
            original = path.read_bytes()
            with Image.open(io.BytesIO(original)) as image:
                image.load()
                item.update(width=image.width, height=image.height, format=image.format)
            item.update(sha256=hashlib.sha256(original).hexdigest(), path=path.relative_to(ROOT).as_posix())
        except (OSError, ValueError):
            excluded.append({**item, "reason": "invalid_image"}); continue
        if not item["category"]:
            excluded.append({**item, "reason": "ambiguous_source_label"}); continue
        item["group"] = hashlib.sha256(("contributor:" + (row.get("sender_id") or identity)).encode()).hexdigest()
        # Source only supplies a coarse class, not colors/material/style/front-back identity.
        item["referenceFields"] = ["category"]
        item["groundTruthFields"] = []
        item["independentReviewStatus"] = "PENDING"
        item["annotationStatus"] = "source_label_unreviewed"
        item["back"] = None
        hashes[item["sha256"]].append(item)
    duplicate_count = 0
    # A contributor is kept entirely in one split. Exact duplicate photos link contributors.
    parents = {}
    def find(value):
        parents.setdefault(value, value)
        if parents[value] != value:
            parents[value] = find(parents[value])
        return parents[value]
    def union(a, b):
        a, b = find(a), find(b)
        parents[max(a, b)] = min(a, b)
    for group in hashes.values():
        for item in group[1:]:
            union(group[0]["group"], item["group"])
    for group in hashes.values():
        group.sort(key=lambda item: item["sourceId"])
        if len({item["category"] for item in group}) > 1:
            excluded.extend({**item, "reason": "duplicate_label_conflict"} for item in group)
            continue
        accepted.append(group[0])
        excluded.extend({**item, "reason": "exact_duplicate", "duplicateOf": group[0]["sourceId"]} for item in group[1:])
        duplicate_count += len(group) - 1
    for item in accepted:
        item["group"] = find(item["group"])
        bucket = int(hashlib.sha256(("fitcheck-v2:" + item["group"]).encode()).hexdigest()[:8], 16) % 100
        item["split"] = "train" if bucket < 70 else "validation" if bucket < 85 else "test"
    accepted.sort(key=lambda item: item["sourceId"])
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "items.jsonl").write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in accepted), encoding="utf-8")
    write_json(OUT / "excluded.json", excluded)
    write_json(OUT / "unlabelled-files.json", sorted(p.relative_to(ROOT).as_posix() for k, p in files.items() if k not in image_ids))
    report = {
        "source": SOURCE, "commit": COMMIT, "license": "CC0-1.0", "labelledRows": len(rows),
        "imageFiles": len(files), "unlabelledFiles": len(set(files) - image_ids),
        "usableUniqueLabelledImages": len(accepted), "excludedRows": len(excluded), "exactDuplicatesRemoved": duplicate_count,
        "exclusions": dict(collections.Counter(item["reason"] for item in excluded)),
        "sourceLabels": dict(collections.Counter(row["label"] for row in rows)),
        "categories": dict(collections.Counter(item["category"] for item in accepted)),
        "splits": {split: dict(collections.Counter(item["category"] for item in accepted if item["split"] == split))
                   for split in ("train", "validation", "test")},
        "splitPolicy": "Deterministic contributor groups, linked by exact duplicates. Small/imbalanced holdouts are reported as-is.",
        "limitations": ["No measured AI accuracy; source class labels have not all been manually verified.",
                       "Low resolution product/household photos; not a VTO ground-truth dataset.",
                       "No verified same-garment front/back pairing in the bulk source.",
                       "Near duplicates and identical garments across contributors may remain.",
                       "Contributor grouping limits leakage but does not prove unseen-garment generalization."],
    }
    # Verify both exact photos and linked contributors cannot leak across splits.
    for key in ("sha256", "group"):
        assignments = collections.defaultdict(set)
        for item in accepted:
            assignments[item[key]].add(item["split"])
        assert all(len(values) == 1 for values in assignments.values())
    write_json(OUT / "audit.json", report)
    write_json(ROOT / "data/reports/modern-clothing-audit.json", report)
    print(json.dumps({k: report[k] for k in ("imageFiles", "labelledRows", "usableUniqueLabelledImages", "unlabelledFiles", "exclusions", "categories", "splits")}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    if args.download:
        download()
    audit()
