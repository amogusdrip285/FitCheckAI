"""Prepare the labeled Fashion Product Images (Small) catalog for Track A."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import tempfile
from zipfile import BadZipFile, ZipFile

from PIL import Image

from prepare_modern_clothing import ROOT, write_json

DATASET = "paramaggarwal/fashion-product-images-small"
VERSION = 1
SOURCE = {
    "dataset": DATASET,
    "version": VERSION,
    "url": f"https://www.kaggle.com/datasets/{DATASET}",
    "publisher": "Param Aggarwal",
    "declaredLicense": "MIT",
}
CSV_SHA256 = "cd86bd2d71b4065ed91786a52aa6ccc0eed343949791c663be9af35f105a128c"
ARCHIVE = ROOT / f"data/raw/kaggle/fashion-product-images-small-v{VERSION}.zip"
OUT = ROOT / "data/processed/kaggle-fashion-small"
REPORTS = ROOT / "data/reports"
COLUMNS = ("id", "gender", "masterCategory", "subCategory", "articleType", "baseColour",
           "season", "year", "usage", "productDisplayName")
METADATA_FACETS = COLUMNS[1:-1]
NORMALIZED_COLUMNS = ("wardrobeCategory", "garmentType", "garmentTypeBasis")
PRODUCT_ID = re.compile(r"[1-9][0-9]*")
PHOTO_MEMBER = re.compile(r"images/([1-9][0-9]*)\.jpg")

TYPE_CATALOG = {
    row["id"]: row for row in json.loads((ROOT / "data/sources/garment-types.json").read_text(encoding="utf-8"))
}
TYPE_RULES = {
    ("Apparel", "Topwear"): {
        "Tshirts": "T_SHIRT", "Shirts": "SHIRT", "Tops": "FASHION_TOP", "Sweatshirts": "SWEATSHIRT",
        "Sweaters": "SWEATER", "Tunics": "TUNIC", "Kurtas": "KURTA", "Kurtis": "KURTA", "Dresses": "DRESS",
        "Jackets": "JACKET", "Rain Jacket": "JACKET", "Shrug": "JACKET", "Blazers": "BLAZER", "Waistcoat": "VEST",
    },
    ("Apparel", "Bottomwear"): {
        "Jeans": "JEANS", "Track Pants": "TRACK_PANTS", "Shorts": "SHORTS", "Trousers": "TROUSERS",
        "Skirts": "SKIRT", "Capris": "CAPRIS", "Leggings": "LEGGINGS", "Jeggings": "LEGGINGS",
        "Rain Trousers": "TROUSERS",
    },
    ("Apparel", "Dress"): {"Dresses": "DRESS", "Jumpsuit": "JUMPSUIT"},
    ("Footwear", "Shoes"): {
        "Casual Shoes": "CASUAL_SHOES", "Formal Shoes": "FORMAL_SHOES", "Sports Shoes": "SNEAKERS",
        "Flats": "FLATS", "Heels": "HEELS", "Sandals": "SANDALS",
    },
    ("Footwear", "Sandal"): {"Sandals": "SANDALS", "Sports Sandals": "SANDALS", "Flip Flops": "FLIP_FLOPS"},
    ("Footwear", "Flip Flops"): {"Flip Flops": "FLIP_FLOPS"},
    ("Accessories", "Headwear"): {"Caps": "CAP", "Hat": "HAT", "Headband": "HEADBAND"},
    ("Accessories", "Bags"): {"Handbags": "HANDBAG", "Clutches": "HANDBAG", "Backpacks": "BACKPACK",
                             "Rucksacks": "BACKPACK", "Wallets": "WALLET"},
    ("Accessories", "Belts"): {"Belts": "BELT"},
    ("Accessories", "Wallets"): {"Wallets": "WALLET"},
    ("Accessories", "Watches"): {"Watches": "WATCH"},
    ("Accessories", "Eyewear"): {"Sunglasses": "SUNGLASSES"},
    ("Accessories", "Socks"): {"Socks": "SOCKS"},
    ("Accessories", "Ties"): {"Ties": "TIE"},
    ("Accessories", "Scarves"): {"Scarves": "SCARF"},
    ("Accessories", "Mufflers"): {"Mufflers": "SCARF"},
    ("Accessories", "Jewellery"): {
        "Earrings": "EARRINGS", "Necklace and Chains": "NECKLACE", "Ring": "RING",
        "Bangle": "BRACELET", "Bracelet": "BRACELET",
    },
}
TITLE_TYPE_RULES = {
    "T_SHIRT": ((r"\bpolo\b", "POLO"), (r"\b(?:tank|sleeveless)\b", "TANK_TOP")),
    "FASHION_TOP": ((r"\b(?:tank|sleeveless|camisole)\b", "TANK_TOP"),),
    "SWEATSHIRT": ((r"\bhood(?:ed|ie|ies)?\b", "HOODIE"),),
    "SWEATER": ((r"\bcardigans?\b", "CARDIGAN"),),
    "TROUSERS": ((r"\bchinos?\b", "CHINOS"), (r"\bformal\b", "DRESS_PANTS")),
    "JACKET": ((r"\bcoats?\b", "COAT"),),
    "CASUAL_SHOES": ((r"\bboots?\b", "BOOTS"), (r"\bsneakers?\b", "SNEAKERS")),
    "FORMAL_SHOES": ((r"\bboots?\b", "BOOTS"), (r"\bleather\b", "LEATHER_SHOES")),
}
POLO_BRANDS = re.compile(
    r"^(?:u\.?\s*s\.?\s+polo\s+assn\.?|classic polo|polo ralph lauren|ralph lauren polo)\b\.?\s*",
    re.IGNORECASE,
)
COLOR_ALIASES = {
    "BLACK": ("Black",),
    "WHITE": ("White", "Off White"),
    "GRAY": ("Grey", "Grey Melange", "Charcoal", "Steel"),
    "BEIGE": ("Beige", "Cream", "Skin", "Nude", "Khaki"),
    "BROWN": ("Brown", "Coffee Brown", "Tan", "Taupe", "Mushroom Brown"),
    "BLUE": ("Blue", "Navy Blue", "Turquoise Blue", "Teal"),
    "GREEN": ("Green", "Olive", "Sea Green", "Lime Green", "Fluorescent Green"),
    "RED": ("Red", "Maroon", "Burgundy"),
    "YELLOW": ("Yellow", "Mustard"),
    "PINK": ("Pink", "Magenta", "Rose"),
    "PURPLE": ("Purple", "Lavender", "Mauve"),
    "OTHER": ("Orange", "Peach", "Rust", "Silver", "Gold", "Bronze", "Copper", "Metallic"),
}
COLOR_MAP = {alias: color for color, aliases in COLOR_ALIASES.items() for alias in aliases}
SEASONS = {"Spring": "SPRING", "Summer": "SUMMER", "Fall": "AUTUMN", "Winter": "WINTER"}


def classify_type(labels):
    group = (labels["masterCategory"], labels["subCategory"])
    kind = TYPE_RULES.get(group, {}).get(labels["articleType"])
    if kind is None:
        return None, None
    title = labels["productDisplayName"]
    if kind == "T_SHIRT":
        title = POLO_BRANDS.sub("", title, count=1)
    for expression, detailed_kind in TITLE_TYPE_RULES.get(kind, ()):
        if re.search(expression, title, flags=re.IGNORECASE):
            return detailed_kind, "articleType+productDisplayName"
    if labels["articleType"] == "Trousers" and labels["usage"] == "Formal":
        return "DRESS_PANTS", "articleType+usage"
    return kind, "articleType"


def metadata_rankings(products):
    total = len(products)
    fields = {}
    for field in METADATA_FACETS:
        counts = Counter(labels[field] for labels in products.values())
        missing = sum(counts.pop(value, 0) for value in ("", "NA"))
        ranked = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))
        fields[field] = {
            "distinctValues": len(ranked),
            "missingRows": missing,
            "topValues": [
                {"value": value, "count": count, "percentOfCatalog": round(100 * count / total, 4)}
                for value, count in ranked[:10]
            ],
        }
    return {"catalogRows": total, "fields": fields}


def download(archive):
    import requests

    archive.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    url = f"https://www.kaggle.com/api/v1/datasets/download/{DATASET}"
    try:
        with requests.get(url, params={"datasetVersionNumber": VERSION}, stream=True, timeout=(15, 90)) as response:
            if response.status_code != 200 or "text/html" in response.headers.get("Content-Type", ""):
                raise ValueError(f"Kaggle did not return a ZIP (HTTP {response.status_code}).")
            with tempfile.NamedTemporaryFile(dir=archive.parent, suffix=".zip.part", delete=False) as target:
                temporary = Path(target.name)
                for chunk in response.iter_content(1024 * 1024):
                    target.write(chunk)
        with ZipFile(temporary) as zip_file:
            read_versioned_catalog(zip_file)
        temporary.replace(archive)
    except requests.RequestException as error:
        raise ValueError(f"Kaggle download failed ({type(error).__name__}).") from None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def read_versioned_catalog(zip_file):
    catalog_bytes = zip_file.read("styles.csv")
    if hashlib.sha256(catalog_bytes).hexdigest() != CSV_SHA256:
        raise ValueError("styles.csv does not match Kaggle dataset version 1.")
    return catalog_bytes


def read_catalog(data):
    rows = csv.reader(io.StringIO(data.decode("utf-8-sig"), newline=""), strict=True)
    if tuple(next(rows, [])) != COLUMNS:
        raise ValueError("Unexpected Kaggle styles.csv header.")
    products = {}
    repaired_titles = 0
    for row in rows:
        if (len(row) < len(COLUMNS) or not PRODUCT_ID.fullmatch(row[0]) or row[0] in products
                or (row[7] and not re.fullmatch(r"[0-9]{4}", row[7]))):
            raise ValueError(f"Invalid catalog row ending on line {rows.line_num}.")
        # Version 1 has 22 titles with bare commas in the last column.
        repaired_titles += len(row) > len(COLUMNS)
        products[row[0]] = dict(zip(COLUMNS[1:], row[1:9] + [",".join(row[9:])]))
    return products, repaired_titles


def audit(zip_file):
    names = zip_file.namelist()
    if len(names) != len(set(names)):
        raise ValueError("ZIP contains duplicate entry names.")
    products, repaired_titles = read_catalog(read_versioned_catalog(zip_file))
    photos = {match[1]: name for name in names if (match := PHOTO_MEMBER.fullmatch(name))}
    excluded = []
    by_hash = defaultdict(list)
    for identity, labels in sorted(products.items(), key=lambda item: int(item[0])):
        garment_type, type_basis = classify_type(labels)
        member = photos.get(identity)
        if member is None:
            excluded.append({"sourceId": identity, "reason": "missing_image"})
            continue
        if garment_type is None:
            excluded.append({"sourceId": identity, "reason": "out_of_scope", "articleType": labels["articleType"]})
            continue
        try:
            if zip_file.getinfo(member).file_size > 4 * 1024 * 1024:
                raise ValueError("Photo exceeds the Track A size limit.")
            contents = zip_file.read(member)
            with Image.open(io.BytesIO(contents)) as image:
                if image.format != "JPEG" or max(image.size) > 2048:
                    raise ValueError("Invalid JPEG format or dimensions.")
                image.load()
                width, height = image.size
        except (OSError, ValueError, BadZipFile, Image.DecompressionBombError):
            excluded.append({"sourceId": identity, "reason": "invalid_image"})
            continue
        digest = hashlib.sha256(contents).hexdigest()
        category = TYPE_CATALOG[garment_type]["category"]
        color = COLOR_MAP.get(labels["baseColour"])
        season = SEASONS.get(labels["season"])
        by_hash[digest].append({
            "sourceId": identity, "archiveMember": member, "sha256": digest,
            "width": width, "height": height, "byteCount": len(contents), "sourceLabels": labels,
            "category": category, "garmentType": garment_type, "garmentTypeBasis": type_basis,
            "colors": [color] if color else [], "pattern": "UNKNOWN",
            "material": None, "formality": None, "seasonTags": [season] if season else [], "styleTags": [],
            "view": "UNKNOWN", "back": None, "referenceFields": ["category", "garmentType"] + (["colors"] if color else []),
            "groundTruthFields": [], "annotationStatus": "source_catalog_unreviewed", "independentReviewStatus": "PENDING",
        })

    accepted = []
    for group in by_hash.values():
        if len({(item["garmentType"], tuple(item["colors"])) for item in group}) > 1:
            excluded.extend({"sourceId": item["sourceId"], "reason": "conflicting_exact_duplicate_labels",
                             "sha256": item["sha256"]} for item in group)
            continue
        first, *duplicates = group
        accepted.append(first)
        excluded.extend({"sourceId": item["sourceId"], "reason": "exact_duplicate",
                         "duplicateOf": first["sourceId"]} for item in duplicates)

    accepted.sort(key=lambda item: int(item["sourceId"]))
    excluded.sort(key=lambda item: int(item["sourceId"]))
    counts = Counter(item["category"] for item in accepted)
    type_counts = Counter(item["garmentType"] for item in accepted)
    if counts.keys() != {row["category"] for row in TYPE_CATALOG.values()}:
        raise ValueError("Audited data is missing a Track A category.")
    if len(accepted) + len(excluded) != len(products):
        raise ValueError("Audit did not account for every catalog row.")
    years = [int(item["sourceLabels"]["year"]) for item in accepted if item["sourceLabels"]["year"]]
    report = {
        "schemaVersion": 2, "source": SOURCE, "stylesCsvSha256": CSV_SHA256, "catalogRows": len(products),
        "rootPhotoFiles": len(photos), "mirroredPhotoFiles": sum(name.startswith("myntradataset/") and
            bool(PHOTO_MEMBER.fullmatch(name.removeprefix("myntradataset/"))) for name in names),
        "photosWithoutCatalogRow": len(photos.keys() - products.keys()), "csvTitlesRepaired": repaired_titles,
        "usableUniquePhotos": len(accepted), "categories": dict(sorted(counts.items())),
        "articleTypes": dict(sorted(Counter(item["sourceLabels"]["articleType"] for item in accepted).items())),
        "garmentTypes": {
            tag: {"label": row["label"], "category": row["category"], "count": type_counts[tag]}
            for tag, row in TYPE_CATALOG.items()
        },
        "exclusions": dict(sorted(Counter(item["reason"] for item in excluded).items())),
        "imageSizes": dict(sorted(Counter(f"{item['width']}x{item['height']}" for item in accepted).items())),
        "catalogYearRange": [min(years), max(years)] if years else [],
        "rowsWithMappedColor": sum(bool(item["colors"]) for item in accepted),
        "rowsWithCatalogSeason": sum(bool(item["seasonTags"]) for item in accepted),
    }
    return products, accepted, excluded, report


def export_sample(zip_file, items, count):
    folder = OUT / f"sample-{count}-per-category"
    buckets = defaultdict(list)
    for item in items:
        buckets[item["category"]].append(item)
    if any(len(bucket) < count for bucket in buckets.values()):
        raise ValueError("Sample size exceeds the available photos in a category.")
    exported = []
    for category, bucket in sorted(buckets.items()):
        # Start with distinct product types where available, then use the remaining product IDs.
        first_by_type = {}
        for item in bucket:
            first_by_type.setdefault(item["sourceLabels"]["articleType"], item)
        first_ids = {item["sourceId"] for item in first_by_type.values()}
        ordered = list(first_by_type.values()) + [item for item in bucket if item["sourceId"] not in first_ids]
        for item in ordered[:count]:
            relative = f"images/{category}/{item['sourceId']}.jpg"
            path = folder / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(zip_file.read(item["archiveMember"]))
            exported.append({**item, "file": relative})
    write_json(folder / "manifest.json", {"schemaVersion": 1, "source": SOURCE, "items": exported})
    print(f"Exported {len(exported)} photos and labels to {folder}")


def export_type_sample(zip_file, items, count):
    folder = OUT / f"sample-{count}-per-type"
    buckets = defaultdict(list)
    for item in items:
        buckets[item["garmentType"]].append(item)
    exported = []
    for kind, bucket in sorted(buckets.items()):
        # Prefer more recent catalog entries and deterministic ordering across colors.
        ordered = sorted(bucket, key=lambda row: (-(int(row["sourceLabels"]["year"] or 0)), int(row["sourceId"])))
        by_color = {}
        for item in ordered:
            by_color.setdefault(item["sourceLabels"]["baseColour"], item)
        first_ids = {item["sourceId"] for item in by_color.values()}
        sample = list(by_color.values()) + [item for item in ordered if item["sourceId"] not in first_ids]
        for item in sample[:count]:
            relative = f"images/{kind}/{item['sourceId']}.jpg"
            path = folder / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(zip_file.read(item["archiveMember"]))
            exported.append({**item, "file": relative})
    counts = Counter(item["garmentType"] for item in exported)
    write_json(folder / "manifest.json", {
        "schemaVersion": 2, "source": SOURCE, "requestedPerType": count,
        "counts": {kind: counts[kind] for kind in TYPE_CATALOG}, "items": exported,
    })
    print(f"Exported {len(exported)} local reference photos to {folder}")
    return {
        "requestedPerType": count, "exportedPhotos": len(exported),
        "counts": {kind: counts[kind] for kind in TYPE_CATALOG},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=ARCHIVE, help="Path to the official Kaggle v1 ZIP")
    parser.add_argument("--download", action="store_true", help="Download version 1 if the ZIP is missing")
    parser.add_argument("--sample-per-category", type=int, default=0, help="Export this many photos per category for testing")
    parser.add_argument("--sample-per-type", type=int, default=0, help="Optional reference JPEGs per detailed garment type")
    parser.add_argument("--publish-metadata", action="store_true", help="Update the tracked metadata CSV and JSON reports")
    args = parser.parse_args()
    if min(args.sample_per_category, args.sample_per_type) < 0:
        parser.error("Sample counts must be nonnegative")
    try:
        if args.download and not args.archive.is_file():
            download(args.archive)
        if not args.archive.is_file():
            raise ValueError("Dataset ZIP missing. Use --download or pass --archive PATH to the official Kaggle v1 ZIP.")
        with ZipFile(args.archive) as zip_file:
            products, items, excluded, report = audit(zip_file)
            if args.sample_per_category:
                export_sample(zip_file, items, args.sample_per_category)
            if args.sample_per_type:
                report["referenceSample"] = export_type_sample(zip_file, items, args.sample_per_type)
        digest = hashlib.sha256()
        with args.archive.open("rb") as archive_file:
            for chunk in iter(lambda: archive_file.read(1024 * 1024), b""):
                digest.update(chunk)
        report["archiveSha256"] = digest.hexdigest()
        report["archiveBytes"] = args.archive.stat().st_size
        OUT.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=OUT) as directory:
            staged = Path(directory)
            accepted_by_id = {item["sourceId"]: item for item in items}
            with (staged / "catalog.csv").open("w", encoding="utf-8-sig", newline="") as target:
                writer = csv.writer(target, lineterminator="\n")
                writer.writerow(COLUMNS + NORMALIZED_COLUMNS)
                for identity, labels in products.items():
                    item = accepted_by_id.get(identity, {})
                    writer.writerow([identity, *(labels[column] for column in COLUMNS[1:]),
                                     item.get("category", ""), item.get("garmentType", ""), item.get("garmentTypeBasis", "")])
            with (staged / "items.jsonl").open("w", encoding="utf-8", newline="\n") as target:
                for item in items:
                    target.write(json.dumps(item, ensure_ascii=False) + "\n")
            write_json(staged / "excluded.json", excluded)
            write_json(staged / "audit.json", report)
            write_json(staged / "metadata-top10.json", {
                "source": SOURCE, "stylesCsvSha256": CSV_SHA256, **metadata_rankings(products),
            })
            for filename in ("catalog.csv", "items.jsonl", "excluded.json", "audit.json", "metadata-top10.json"):
                (staged / filename).replace(OUT / filename)
        if args.publish_metadata:
            for generated, tracked in (
                ("catalog.csv", "kaggle-fashion-small-catalog.csv"),
                ("audit.json", "kaggle-fashion-small-audit.json"),
                ("metadata-top10.json", "kaggle-fashion-small-top10.json"),
            ):
                (REPORTS / tracked).write_bytes((OUT / generated).read_bytes())
    except (OSError, ValueError, KeyError, BadZipFile, csv.Error) as error:
        parser.exit(1, f"Kaggle dataset preparation failed: {error}\n")
    print(f"PASS: {report['usableUniquePhotos']:,} usable unique photos from {report['catalogRows']:,} catalog rows.")
    print(json.dumps({key: report[key] for key in ("categories", "exclusions")}, indent=2))


if __name__ == "__main__":
    main()
