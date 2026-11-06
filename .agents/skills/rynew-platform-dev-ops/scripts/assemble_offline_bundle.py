#!/usr/bin/env python3
"""Assemble an audited local Windows bundle; do not publish dependency binaries to Git."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def assemble(root, prepared, output):
    root, prepared, output = root.resolve(), prepared.resolve(strict=True), output.resolve()
    if git(root, "status", "--porcelain"):
        raise ValueError("Commit the requested source changes first; bundle sources must be clean")
    if output.exists() or output.with_suffix(".zip").exists():
        raise ValueError("Choose a new output directory; existing deliveries are never overwritten")
    head, branch, baseline = git(root, "rev-parse", "HEAD"), git(root, "branch", "--show-current"), git(root, "rev-parse", "origin/main")
    subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", "origin/main", "HEAD"], check=True)
    assets = root / ".agents/skills/rynew-platform-dev-ops/assets/offline"
    output.mkdir(parents=True)
    for directory in ["maven/repository", "official-docs", "knowledge", "locks", "logs"]:
        shutil.copytree(prepared / directory, output / directory)
    cache = output / "npm-cache"
    shutil.copytree(prepared / "npm-cache-locked/_cacache", cache / "_cacache")
    for name in ["empty-user.npmrc", "empty-global.npmrc"]:
        shutil.copy2(prepared / "npm-cache-locked" / name, cache / name)
    shutil.copy2(assets / "settings-offline.xml", output / "maven/settings-offline.xml")
    shutil.copy2(prepared / "offline-audit.json", output / "offline-audit.json")
    (output / "toolchains").mkdir()
    tools = json.loads((prepared / "toolchains/verified-toolchains.json").read_text())
    for item in tools:
        source = prepared / "toolchains" / item["archive"]
        algorithm = "sha512" if "sha512" in item else "sha256"
        with source.open("rb") as stream:
            digest = hashlib.file_digest(stream, algorithm).hexdigest()
        if digest != item[algorithm]:
            raise ValueError("Toolchain integrity mismatch: " + item["name"])
        shutil.copy2(source, output / "toolchains" / item["archive"])
    shutil.copy2(prepared / "toolchains/verified-toolchains.json", output / "toolchains/verified-toolchains.json")
    for name in ["Initialize-Offline.ps1", "Verify-Offline.ps1", "Verify-Manifest.ps1"]:
        shutil.copy2(assets / name, output / name)
    for name in ["npm.cmd", "npx.cmd"]:
        (output / "windows-shims").mkdir(exist_ok=True)
        shutil.copy2(assets / name, output / "windows-shims" / name)
    (output / "scripts").mkdir()
    shutil.copy2(assets / "compare-packages.cjs", output / "scripts/compare-packages.cjs")
    shutil.copy2(root / ".agents/skills/rynew-platform-dev-ops/references/offline-development.md", output / "README-离线开发.md")
    depth = int(git(root, "rev-list", "--count", "origin/main..HEAD")) + 1
    subprocess.run(["git", "clone", "--bare", "--depth=" + str(depth), "--single-branch", "--branch", branch,
                    root.as_uri(), str(output / "source.git")], check=True)
    source = output / "source.git"
    subprocess.run(["git", "--git-dir=" + str(source), "update-ref", "refs/heads/main", baseline], check=True)
    # Preserve original commits, not the preparation machine's private filesystem URL.
    subprocess.run(["git", "--git-dir=" + str(source), "config", "remote.origin.url", "https://github.com/krizen97-sz/wjdatafusion.git"], check=True)
    source_info = {"commit": head, "branch": branch, "origin_main": baseline, "shallow_depth": depth,
                   "created_at": datetime.now(timezone.utc).isoformat(), "source_clean": True,
                   "history_scope": "feature commits and main baseline; large unrelated historical objects excluded"}
    (output / "SOURCE.json").write_text(json.dumps(source_info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    entries = []
    reserved = {"CON", "PRN", "AUX", "NUL", *["COM" + str(i) for i in range(1, 10)], *["LPT" + str(i) for i in range(1, 10)]}
    for path in sorted(output.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(output)
        for part in relative.parts:
            if re.search(r'[<>:"|?*]', part) or part.endswith((".", " ")) or part.split(".")[0].upper() in reserved:
                raise ValueError("Not a portable Windows filename: " + str(relative))
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        entries.append({"path": relative.as_posix(), "bytes": path.stat().st_size, "sha256": digest})
    (output / "FILES.sha256.json").write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text("".join(entry["sha256"] + "  " + entry["path"] + "\n" for entry in entries), encoding="utf-8")
    archive = output.with_suffix(".zip")
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=1, allowZip64=True) as target:
        for path in sorted(output.rglob("*")):
            if path.is_file():
                target.write(path, path.relative_to(output).as_posix())
    with archive.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    archive.with_suffix(".zip.sha256").write_text(digest + "  " + archive.name + "\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "zip": str(archive), "bytes": archive.stat().st_size, "sha256": digest, "payload_files": len(entries), "source_commit": head}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--prepared", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assemble(args.root, args.prepared, args.output)
