"""Compare application source contents to a pinned public GitHub reference."""
import argparse
import hashlib
import json
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


def blob_sha(path):
    content = path.read_bytes()
    # Local SVN checkouts may convert newlines; Git compares normalized source here.
    if path.suffix in {".java", ".xml", ".yml", ".conf", ".properties"}:
        content = content.replace(b"\r\n", b"\n")
    return hashlib.sha1(b"blob " + str(len(content)).encode() + b"\0" + content).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--ref", default="3f09a8a324ebde47cc5e147ef357a2418fad3b1c")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    request = urllib.request.Request(
        f"https://api.github.com/repos/sxwdmjy/openCallHub/git/trees/{args.ref}?recursive=1",
        headers={"User-Agent": "OpenCallHub-source-inventory"})
    with urllib.request.urlopen(request, timeout=30) as response:
        tree = json.load(response)
    if tree.get("truncated"):
        raise ValueError("GitHub returned an incomplete tree")
    modules = [item.text for item in ET.parse(args.root / "pom.xml").findall(
        "{http://maven.apache.org/POM/4.0.0}modules/{http://maven.apache.org/POM/4.0.0}module")]
    def in_scope(path):
        return path == "pom.xml" or any(path == f"{m}/pom.xml" or path.startswith(f"{m}/src/") for m in modules)
    remote = {entry["path"]: entry["sha"] for entry in tree["tree"]
              if entry["type"] == "blob" and in_scope(entry["path"])}
    local = {"pom.xml": args.root / "pom.xml"}
    for module in modules:
        for path in (args.root / module).rglob("*"):
            if path.is_file() and not path.is_symlink():
                relative = path.relative_to(args.root).as_posix()
                if in_scope(relative):
                    local[relative] = path
    statuses = {"added": [], "modified": [], "unchanged": [], "absent_locally": []}
    for relative in sorted(set(local) | set(remote)):
        if relative not in remote:
            key = "added"
        elif relative not in local:
            key = "absent_locally"
        else:
            key = "unchanged" if blob_sha(local[relative]) == remote[relative] else "modified"
        statuses[key].append(relative)
    result = {"repository": "https://github.com/sxwdmjy/openCallHub", "reference": args.ref,
              "meaning": "Content comparison reference; does not establish Git ancestry or authorship",
              "normalization": "CRLF to LF for textual source; upstream Git blob hashes unchanged",
              "scope": "Root POM, module POMs and src trees", "counts": {k: len(v) for k, v in statuses.items()},
              "files": statuses}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result["counts"]))


if __name__ == "__main__":
    main()
