"""INT-004 — package one fixed, checksummed APK for submission (OWNER: Track D).

Builds (or reuses) the debug APK, copies it to dist/ under a name that pins version, commit
and release mode, writes SHA-256 + a JSON manifest, and refuses to package if the APK carries
anything shaped like a provider API key. The demo access code (fitcheck.accessCode) is not a
provider key; the manifest only records whether one was compiled in, never its value.

Usage:
    python tools/release/package_apk.py --mode CORE
    python tools/release/package_apk.py --mode CORE --skip-build --allow-dirty
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APK_PATH = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
MODES = ("CORE", "FRONT", "DUAL")

# Provider credentials must live on the proxy only (B-001). Patterns cover OpenAI, Google,
# Anthropic, FASHN-style bearer tokens and PEM private keys.
PROVIDER_KEY_PATTERNS = {
    "openai": re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{20,}"),
    "google": re.compile(rb"AIza[0-9A-Za-z_-]{35}"),
    "anthropic": re.compile(rb"sk-ant-[A-Za-z0-9_-]{20,}"),
    "fashn": re.compile(rb"fa-[A-Za-z0-9]{24,}"),
    "private_key": re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
}


def scan_for_provider_keys(apk: Path) -> list[str]:
    """Returns 'entry:kind' for every APK entry containing a provider-key-shaped value."""
    hits = []
    with zipfile.ZipFile(apk) as archive:
        for info in archive.infolist():
            data = archive.read(info)
            hits += [f"{info.filename}:{kind}" for kind, rx in PROVIDER_KEY_PATTERNS.items() if rx.search(data)]
    return hits


def read_version(gradle_file: Path) -> str:
    match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_file.read_text(encoding="utf-8"))
    if not match:
        raise ValueError(f"versionName not found in {gradle_file}")
    return match.group(1)


def artifact_name(version: str, commit: str, mode: str) -> str:
    if mode not in MODES:
        raise ValueError(f"mode must be one of {MODES}")
    return f"FitCheckAI-{version}-{commit[:7]}-{mode}.apk"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout.rstrip()


BUILD_INPUT_RE = re.compile(r"(^|/)src/|\.gradle\.kts$|(^|/)gradle\.properties$")
ACCESS_CODE_RE = re.compile(r"^\s*fitcheck\.accessCode\s*[=:]\s*(.*?)\s*$", re.MULTILINE)


def dirty_build_inputs(tracked_changes: str, untracked: str) -> list[str]:
    """Paths that make the APK differ from the commit: tracked changes plus untracked build inputs."""
    changed = [line[3:] for line in tracked_changes.splitlines() if line.strip()]
    new_inputs = [path for path in untracked.splitlines() if BUILD_INPUT_RE.search(path)]
    return changed + new_inputs


def build_apk() -> None:
    gradlew = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    subprocess.run([str(gradlew), ":app:assembleDebug", "--console=plain", "-q"], cwd=ROOT, check=True)


def has_access_code(apk: Path) -> bool:
    """True if a non-empty PROXY_ACCESS_CODE was compiled in (value is never read out)."""
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
    # BuildConfig stores an empty string when unset; the field name alone proves nothing.
    # Only ~/.gradle/gradle.properties is inspected; a code passed via -P or env reports False.
    gradle_props = Path.home() / ".gradle" / "gradle.properties"
    if not gradle_props.exists():
        return False
    match = ACCESS_CODE_RE.search(gradle_props.read_text(encoding="utf-8"))
    return bool(match and match.group(1) and match.group(1).encode() in dex)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mode", choices=MODES, required=True, help="Release mode recorded by D-007.")
    parser.add_argument("--out", type=Path, default=ROOT / "dist")
    parser.add_argument("--skip-build", action="store_true", help="Package the existing app-debug.apk.")
    parser.add_argument("--allow-dirty", action="store_true", help="Allow uncommitted changes (not for submission).")
    args = parser.parse_args(argv)

    dirty_paths = dirty_build_inputs(
        git("status", "--porcelain", "--untracked-files=no"),
        git("ls-files", "--others", "--exclude-standard"),
    )
    dirty = bool(dirty_paths)
    if dirty and not args.allow_dirty:
        print("Uncommitted build inputs: " + ", ".join(dirty_paths[:10]) + "; commit first or pass --allow-dirty.",
              file=sys.stderr)
        return 2
    if not args.skip_build:
        build_apk()
    if not APK_PATH.exists():
        print(f"APK not found: {APK_PATH}", file=sys.stderr)
        return 2

    hits = scan_for_provider_keys(APK_PATH)
    if hits:
        print("Refusing to package: provider-key-shaped values found in " + ", ".join(hits), file=sys.stderr)
        return 3

    commit = git("rev-parse", "HEAD")
    version = read_version(ROOT / "app" / "build.gradle.kts")
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / artifact_name(version, commit, args.mode)
    shutil.copy2(APK_PATH, target)
    digest = sha256(target)
    manifest = {
        "artifact": target.name,
        "sha256": digest,
        "bytes": target.stat().st_size,
        "versionName": version,
        "commit": commit,
        "dirtyTree": dirty,
        "releaseMode": args.mode,
        "variant": "debug",
        "builtAt": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "providerKeyScan": "clean",
        "demoAccessCodeCompiledIn": has_access_code(target),
    }
    target.with_suffix(".sha256").write_text(f"{digest}  {target.name}\n", encoding="utf-8")
    target.with_suffix(".json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
