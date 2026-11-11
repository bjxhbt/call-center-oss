"""Verify source archive hashes and reject runtime, backup or unlisted entries."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path, PurePosixPath


def verify(archive):
    archive = Path(archive)
    expected_zip = (archive.parent / "SHA256SUMS").read_text(encoding="utf-8").split()[0]
    if hashlib.sha256(archive.read_bytes()).hexdigest() != expected_zip:
        raise ValueError("Archive SHA-256 mismatch")
    with zipfile.ZipFile(archive) as bundle:
        names = bundle.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate archive entries")
        manifest = json.loads(bundle.read("source-manifest.json"))
        files = {item["path"]: item for item in manifest["files"]}
        if set(names) != set(files) | {"source-manifest.json", "SHA256SUMS"}:
            raise ValueError("Manifest and archive file lists differ")
        required = {"README.md", "LICENSE", "deploy/public/00-init.sql",
                    "deploy/public/application.yml"}
        if manifest["frontend_included"]:
            required |= {"frontend/LICENSE", "frontend/package-lock.json", "frontend/package.json"}
        if not required.issubset(names):
            raise ValueError("Required release inputs missing")
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Unsafe archive entry: {name}")
            if any(part in {"target", "node_modules", ".svn", ".git", "__pycache__", "vendor"} for part in path.parts):
                raise ValueError(f"Runtime tree in archive: {name}")
            if path.suffix.lower() in {".dump", ".rdb", ".log", ".bak", ".old", ".orig", ".rej", ".tmp", ".class", ".jar", ".war", ".mp3", ".mp4"}:
                raise ValueError(f"Runtime or backup file in archive: {name}")
            if path.suffix.lower() == ".wav" and name not in manifest["generated_assets"]:
                raise ValueError(f"Unreviewed audio in archive: {name}")
        for line in bundle.read("SHA256SUMS").decode("utf-8").splitlines():
            digest, name = line.split("  ", 1)
            if hashlib.sha256(bundle.read(name)).hexdigest() != digest:
                raise ValueError(f"File SHA-256 mismatch: {name}")
        for name, item in files.items():
            if hashlib.sha256(bundle.read(name)).hexdigest() != item["export_sha256"]:
                raise ValueError(f"Manifest SHA-256 mismatch: {name}")
    return {"status": "PASS", "source_files": len(files), "frontend_included": manifest["frontend_included"],
            "archive_sha256": expected_zip, "runtime_and_backup_entries": 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.archive)))


if __name__ == "__main__":
    main()
