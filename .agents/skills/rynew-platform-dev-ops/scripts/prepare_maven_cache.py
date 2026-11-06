#!/usr/bin/env python3
"""Make a dedicated Maven cache copy portable without copying personal settings."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def prepare(repository, receipt):
    root = repository.resolve(strict=True)
    if any(part.lower() == ".m2" for part in root.parts) or root.name.lower() != "repository" or root.parent.name.lower() != "maven":
        raise ValueError("Use only the bundle's maven/repository copy, never the user's .m2 repository")
    rows = []
    for path in sorted(root.rglob("_remote.repositories")):
        original = path.read_bytes()
        text = original.decode("utf-8")
        candidates, local = set(), set()
        for line in text.splitlines():
            if line.startswith("#") or ">" not in line or "=" not in line:
                continue
            key = line.split("=", 1)[0]
            filename, origin = key.rsplit(">", 1)
            if "/" in filename or "\\" in filename:
                raise ValueError("Invalid Maven tracking entry")
            if not origin:
                local.add(filename)
            if (path.parent / filename).is_file():
                candidates.add(filename)
        additions = sorted(candidates - local)
        if not additions:
            continue
        # Empty origin denotes an installed/local artifact in Maven's enhanced LRM.
        # Keep the original remote-origin lines, and do not modify any artifact bytes.
        updated = text.rstrip("\n") + "\n# RYNEW: trusted portable offline copy; original origins retained\n"
        updated += "".join(name + ">=\n" for name in additions)
        path.write_bytes(updated.encode("utf-8"))
        rows.append({"path": str(path.relative_to(root)).replace("\\", "/"),
                     "before_sha256": hashlib.sha256(original).hexdigest(),
                     "after_sha256": hashlib.sha256(updated.encode()).hexdigest(),
                     "locally_available_artifacts_added": len(additions)})
    # Unversioned lifecycle plugins (notably NAR resources) also need a portable
    # local version catalog; remote-id-specific metadata alone is insufficient.
    versions = {}
    for pom in root.rglob("*.pom"):
        directory = pom.parent.parent
        artifact, version = directory.name, pom.parent.name
        if pom.name == artifact + "-" + version + ".pom" and (pom.parent / (artifact + "-" + version + ".jar")).is_file():
            versions.setdefault(directory, set()).add(version)
    catalogs = []
    natural = lambda value: [(0, int(part)) if part.isdigit() else (1, part.lower()) for part in re.findall(r"\d+|[^\d]+", value)]
    for directory, available in sorted(versions.items()):
        parts = directory.relative_to(root).parts
        ordered = sorted(available, key=natural)
        metadata = ET.Element("metadata")
        ET.SubElement(metadata, "groupId").text = ".".join(parts[:-1])
        ET.SubElement(metadata, "artifactId").text = parts[-1]
        versioning = ET.SubElement(metadata, "versioning")
        # Leave latest/release unset: Maven applies its own ComparableVersion
        # ordering to the complete frozen list, including qualified versions.
        elements = ET.SubElement(versioning, "versions")
        for version in ordered:
            ET.SubElement(elements, "version").text = version
        ET.SubElement(versioning, "lastUpdated").text = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        path = directory / "maven-metadata-local.xml"
        data = ET.tostring(metadata, encoding="utf-8", xml_declaration=True)
        path.write_bytes(data)
        catalogs.append({"path": str(path.relative_to(root)).replace("\\", "/"), "versions": ordered, "sha256": hashlib.sha256(data).hexdigest()})
    report = {"scope": "dedicated offline cache copy only; original origins retained, no artifact bytes changed",
              "tracking_files_updated": len(rows), "artifacts_marked_local": sum(row["locally_available_artifacts_added"] for row in rows), "files": rows,
              "local_version_catalogs": catalogs}
    if rows or catalogs or not receipt.exists():
        if receipt.exists():
            report["previous_receipt_sha256"] = hashlib.sha256(receipt.read_bytes()).hexdigest()
        receipt.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in {"files", "local_version_catalogs"}}, ensure_ascii=False))
    print(json.dumps({"local_version_catalog_count": len(catalogs)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.repository, args.receipt)
