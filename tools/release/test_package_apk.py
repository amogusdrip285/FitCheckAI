import tempfile
import unittest
import zipfile
from pathlib import Path

import package_apk


class PackageApkTest(unittest.TestCase):
    def _apk(self, entries: dict[str, bytes]) -> Path:
        path = Path(tempfile.mkdtemp()) / "app.apk"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return path

    def test_clean_apk_has_no_provider_key_hits(self):
        apk = self._apk({"classes.dex": b"PROXY_URL https://proxy.example PROXY_ACCESS_CODE demo-code"})
        self.assertEqual([], package_apk.scan_for_provider_keys(apk))

    def test_provider_keys_are_reported_by_entry_and_kind(self):
        apk = self._apk({
            "classes.dex": b"x sk-proj-" + b"a" * 30 + b" y",
            "res/raw/k": b"AIza" + b"B" * 35,
        })
        self.assertEqual(
            ["classes.dex:openai", "res/raw/k:google"],
            sorted(package_apk.scan_for_provider_keys(apk)),
        )

    def test_artifact_name_pins_version_commit_and_mode(self):
        self.assertEqual(
            "FitCheckAI-0.1.0-abcdef1-CORE.apk",
            package_apk.artifact_name("0.1.0", "abcdef1234567", "CORE"),
        )
        with self.assertRaises(ValueError):
            package_apk.artifact_name("0.1.0", "abcdef1", "FULL")

    def test_untracked_sources_count_as_dirty_but_other_untracked_files_do_not(self):
        untracked = "app/src/main/kotlin/New.kt\nbuild-logic/convention/bin/x.class\nnotes.txt\nfeature/x/build.gradle.kts"
        self.assertEqual(
            ["docs/a.md", "app/src/main/kotlin/New.kt", "feature/x/build.gradle.kts"],
            package_apk.dirty_build_inputs(" M docs/a.md\n", untracked),
        )
        self.assertEqual([], package_apk.dirty_build_inputs("", "build-logic/convention/bin/x.class\n"))

    def test_access_code_property_tolerates_spaces_and_colon(self):
        for line in ("fitcheck.accessCode=abc", "fitcheck.accessCode = abc ", "  fitcheck.accessCode: abc"):
            self.assertEqual("abc", package_apk.ACCESS_CODE_RE.search(line).group(1))
        self.assertEqual("", package_apk.ACCESS_CODE_RE.search("fitcheck.accessCode=").group(1))

    def test_version_is_read_from_gradle(self):
        gradle = Path(tempfile.mkdtemp()) / "build.gradle.kts"
        gradle.write_text('android { defaultConfig { versionName = "1.2.3" } }', encoding="utf-8")
        self.assertEqual("1.2.3", package_apk.read_version(gradle))


if __name__ == "__main__":
    unittest.main()
