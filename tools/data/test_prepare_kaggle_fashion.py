import csv
import hashlib
import io
import unittest
from unittest.mock import patch
from zipfile import ZipFile

from PIL import Image

import prepare_kaggle_fashion as catalog


def jpeg(color):
    buffer = io.BytesIO()
    Image.new("RGB", (60, 80), color).save(buffer, "JPEG")
    return buffer.getvalue()


class KaggleCatalogTest(unittest.TestCase):
    def test_commas_in_product_title_do_not_shift_catalog_labels(self):
        header = ",".join(catalog.COLUMNS)
        contents = (f"{header}\n"
                    "10,Men,Apparel,Topwear,Tshirts,Navy Blue,Fall,2011,Casual,Sample Tee, Pack 1, Blue\n"
                    '20,Women,Apparel,Dress,Dresses,Pink,Summer,2012,Casual,"Sample Dress, Pink"\n')
        products, repairs = catalog.read_catalog(contents.encode())

        self.assertEqual(repairs, 1)
        self.assertEqual(products["10"]["productDisplayName"], "Sample Tee, Pack 1, Blue")
        self.assertEqual(products["10"]["baseColour"], "Navy Blue")
        self.assertEqual(products["10"]["year"], "2011")
        self.assertEqual(products["20"]["productDisplayName"], "Sample Dress, Pink")

    def test_audit_joins_by_product_id_and_excludes_unusable_rows(self):
        fields = io.StringIO(newline="")
        writer = csv.writer(fields)
        writer.writerow(catalog.COLUMNS)
        # Valid six-category set, plus out-of-scope, missing, corrupt and repeated photos.
        rows = [
            (10, "Apparel", "Topwear", "Tshirts", "Navy Blue", jpeg("navy")),
            (20, "Apparel", "Bottomwear", "Jeans", "Blue", jpeg("blue")),
            (30, "Apparel", "Topwear", "Dresses", "Pink", jpeg("pink")),
            (40, "Apparel", "Topwear", "Jackets", "Multi", jpeg("gray")),
            (50, "Footwear", "Sandal", "Flip Flops", "White", jpeg("white")),
            (60, "Accessories", "Headwear", "Caps", "Black", jpeg("black")),
            (70, "Apparel", "Loungewear and Nightwear", "Shorts", "Red", jpeg("red")),
            (80, "Apparel", "Topwear", "Shirts", "White", None),
            (90, "Apparel", "Topwear", "Shirts", "White", b"invalid jpeg"),
            (100, "Apparel", "Topwear", "Tshirts", "Navy Blue", jpeg("navy")),
            (110, "Apparel", "Topwear", "Shirts", "Brown", jpeg("brown")),
            (120, "Apparel", "Bottomwear", "Trousers", "Brown", jpeg("brown")),
        ]
        archive = io.BytesIO()
        with ZipFile(archive, "w") as zip_file:
            for identity, master, sub, article, color, contents in rows:
                writer.writerow((identity, "Unisex", master, sub, article, color, "Fall", "2011", "Casual", f"Item {identity}"))
                if contents is not None:
                    zip_file.writestr(f"images/{identity}.jpg", contents)
            data = fields.getvalue().encode()
            zip_file.writestr("styles.csv", data)
            zip_file.writestr("myntradataset/styles.csv", data)
            zip_file.writestr("myntradataset/images/10.jpg", rows[0][-1])

        with ZipFile(archive) as zip_file, patch.object(catalog, "CSV_SHA256", hashlib.sha256(data).hexdigest()):
            items, excluded, report = catalog.audit(zip_file)

        self.assertEqual([(item["sourceId"], item["category"]) for item in items], [
            ("10", "TOP"), ("20", "BOTTOM"), ("30", "DRESS"), ("40", "OUTERWEAR"), ("50", "SHOES"), ("60", "ACCESSORY"),
        ])
        self.assertEqual(items[0]["colors"], ["BLUE"])
        self.assertEqual(items[0]["seasonTags"], ["AUTUMN"])
        self.assertEqual(items[3]["colors"], [])
        self.assertEqual(items[3]["referenceFields"], ["category"])
        self.assertEqual(report["rootPhotoFiles"], 11)
        self.assertEqual(report["mirroredPhotoFiles"], 1)
        self.assertEqual(report["exclusions"], {
            "out_of_scope": 1, "missing_image": 1, "invalid_image": 1,
            "exact_duplicate": 1, "conflicting_exact_duplicate_labels": 2,
        })
        self.assertEqual(next(item for item in excluded if item["sourceId"] == "100")["duplicateOf"], "10")


if __name__ == "__main__":
    unittest.main()
