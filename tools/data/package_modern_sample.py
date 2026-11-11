"""Build or validate the 100-photo CC0 development sample; no model evaluation."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict, deque
import hashlib
import json
from pathlib import Path
import uuid

from PIL import Image

from prepare_modern_clothing import CATEGORIES, COMMIT, OUT, RAW, ROOT, SOURCE, write_json

BASE = ROOT / "data/samples/modern-clothing-100"
QUOTAS = {"TOP": 25, "BOTTOM": 25, "DRESS": 15, "OUTERWEAR": 15, "SHOES": 10, "ACCESSORY": 10}
LICENSE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"


def fixture_source_ids():
    fixtures = json.loads((ROOT / "data/fixtures/modern-clothing/manifest.json").read_text(encoding="utf-8"))
    return {photo["sourceId"] for item in fixtures["items"] for view in ("front", "back")
            if (photo := item[view]) and "sourceId" in photo}


def select_items():
    items = [json.loads(line) for line in (OUT / "items.jsonl").read_text(encoding="utf-8").splitlines()]
    excluded = fixture_source_ids()
    selected = []
    for category, quota in QUOTAS.items():
        labels = defaultdict(list)
        for item in items:
            if item["category"] == category and item["split"] == "train" and item["sourceId"] not in excluded:
                labels[item["sourceLabel"]].append(item)
        # Stable selection with source-label diversity; no peeking into validation/test splits.
        queues = [deque(sorted(labels[label], key=lambda item: hashlib.sha256(
            ("fitcheck-sample-v1:" + item["sourceId"]).encode()).hexdigest())) for label in sorted(labels)]
        chosen = []
        while len(chosen) < quota:
            if not any(queues):
                raise ValueError(f"Not enough train photos for {category}")
            for queue in queues:
                if queue and len(chosen) < quota:
                    chosen.append(queue.popleft())
        selected.extend(chosen)
    return selected


def build():
    selected = select_items()
    expected_paths = {BASE / "images" / item["category"] / (item["sourceId"] + ".jpg") for item in selected}
    existing = {path for path in (BASE / "images").rglob("*") if path.is_file()}
    if existing - expected_paths:
        raise ValueError("Unexpected existing images: review the sample before rebuilding")
    records = []
    for item in selected:
        source = (ROOT / item["path"]).resolve()
        assert source.is_relative_to(RAW.resolve())
        data = source.read_bytes()
        assert hashlib.sha256(data).hexdigest() == item["sha256"]
        target = BASE / "images" / item["category"] / (item["sourceId"] + ".jpg")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        records.append({
            **{key: item[key] for key in ("sourceId", "sourceLabel", "category", "width", "height", "sha256", "group")},
            "file": target.relative_to(BASE).as_posix(), "byteCount": len(data), "sourceSplit": "train",
            "sourceUrl": f"{SOURCE}/blob/{COMMIT}/images/{source.name}", "license": "CC0-1.0",
            "annotationStatus": "source_label_unreviewed", "referenceFields": ["category"],
            "groundTruthFields": [], "independentReviewStatus": "PENDING", "view": "UNKNOWN", "back": None,
        })
    license_bytes = (RAW / "LICENSE").read_bytes()
    (BASE / "LICENSE").write_bytes(license_bytes)
    write_json(BASE / "manifest.json", {
        "schemaVersion": 1, "purpose": "development_smoke_test", "source": SOURCE, "sourceCommit": COMMIT,
        "license": "CC0-1.0", "licenseUrl": LICENSE_URL,
        "licenseSha256": hashlib.sha256(license_bytes).hexdigest(),
        "attribution": "Alexey Grigorev clothing-dataset contributors / Tagias",
        "selectionPolicy": "Train split only; exclude existing fixtures; category quotas; round-robin source labels, ordered by SHA256(fitcheck-sample-v1:sourceId).",
        "imageChanges": "None; original source bytes retained.", "categoryCounts": QUOTAS,
        "items": records,
    })


def check():
    data = json.loads((BASE / "manifest.json").read_text(encoding="utf-8"))
    assert data["purpose"] == "development_smoke_test" and data["sourceCommit"] == COMMIT
    assert data["source"] == SOURCE and data["license"] == "CC0-1.0" and data["licenseUrl"] == LICENSE_URL
    assert hashlib.sha256((BASE / "LICENSE").read_bytes()).hexdigest() == data["licenseSha256"]
    assert data["categoryCounts"] == QUOTAS and len(data["items"]) == 100
    assert Counter(item["category"] for item in data["items"]) == QUOTAS
    ids, hashes, paths = set(), set(), set()
    for item in data["items"]:
        identity = item["sourceId"]
        assert str(uuid.UUID(identity)) == identity and identity not in ids
        ids.add(identity)
        assert CATEGORIES[item["sourceLabel"]] == item["category"] and item["sourceSplit"] == "train"
        assert item["groundTruthFields"] == [] and item["referenceFields"] == ["category"]
        assert item["annotationStatus"] == "source_label_unreviewed" and item["independentReviewStatus"] == "PENDING"
        assert item["view"] == "UNKNOWN" and item["back"] is None and item["license"] == "CC0-1.0"
        assert item["sourceUrl"] == f"{SOURCE}/blob/{COMMIT}/images/{identity}.jpg"
        path = (BASE / item["file"]).resolve()
        assert path.is_relative_to((BASE / "images").resolve()) and path not in paths
        paths.add(path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        assert digest == item["sha256"] and digest not in hashes
        hashes.add(digest)
        assert path.stat().st_size == item["byteCount"] < 4 * 1024 * 1024
        with Image.open(path) as image:
            image.load()
            assert image.format == "JPEG" and image.size == (item["width"], item["height"])
            assert not image.getexif(), "Review metadata before publishing source bytes"
    assert not ids.intersection(fixture_source_ids())
    assert paths == {path.resolve() for path in (BASE / "images").rglob("*") if path.is_file()}
    print(f"PASS: 100 CC0 photos, 6 categories, source labels, hashes and license verified; {sum(i['byteCount'] for i in data['items']) / 1024**2:.2f} MiB.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", action="store_true", help="Requires the audited bulk dataset locally")
    args = parser.parse_args()
    if args.build:
        build()
    check()
