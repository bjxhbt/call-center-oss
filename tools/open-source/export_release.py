"""Export a reviewable source archive using explicit inputs, public configs and file hashes."""
import argparse
import hashlib
import ipaddress
import io
import json
import math
import re
import shutil
import struct
import wave
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


TEXT_SUFFIXES = {".java", ".xml", ".yml", ".yaml", ".conf", ".properties", ".json", ".js", ".ts",
                 ".vue", ".css", ".less", ".html", ".md", ".txt", ".py", ".ps1", ".sql", ".example",
                 ".sh", ".bat", ".cmd", ".cjs", ".mjs", ".csv", ".svg"}
PRIVATE_IP = re.compile(r"(?<![\w.])(?:10\.\d{1,3}|192\.168|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}(?![\w.])")
CREDENTIAL = re.compile(r'''(?ix)\b(password|secret[-_]?key|api[-_]?key|access[-_]?key|auth[-_]?key)\b
                           ["']?\s*[:=]\s*["']([^"'\r\n]+)["']''')
SCALAR_CREDENTIAL = re.compile(r'''(?ix)^\s*(?:[a-z0-9_.-]+\.)?
    (password|secret[-_]?key|api[-_]?key|access[-_]?key|auth[-_]?key)\s*[:=]\s*
    (?!["'])([^\s\#][^\#\r\n]*?)\s*(?:\#.*)?$''')
KNOWN_SAMPLE = re.compile(r"(?i)^(?:\*|\$\{.*\}|test.*|mock.*|fake.*|dummy.*|local-example.*|not-configured|disabled-no-freeswitch|secret|password|123456|change-me)$")
BACKUP_SUFFIXES = {".bak", ".old", ".orig", ".rej", ".tmp", ".class"}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def scan_text(text, scalar_config=True):
    findings = []
    for number, line in enumerate(text.splitlines(), 1):
        for match in CREDENTIAL.finditer(line):
            if not KNOWN_SAMPLE.fullmatch(match.group(2)):
                findings.append({"line": number, "kind": "literal-credential", "key": match.group(1)})
        scalar = SCALAR_CREDENTIAL.match(line) if scalar_config else None
        if scalar and not KNOWN_SAMPLE.fullmatch(scalar.group(2)):
            findings.append({"line": number, "kind": "literal-credential", "key": scalar.group(1)})
        if re.search(r"\b(?:AKIA[A-Z0-9]{16}|AKID[A-Za-z0-9]{28,}|LTAI[A-Za-z0-9]{16,}|sk-[A-Za-z0-9]{24,})\b", line):
            findings.append({"line": number, "kind": "credential-format"})
        if re.match(r"\s*-----BEGIN [A-Z ]*PRIVATE KEY-----", line):
            findings.append({"line": number, "kind": "private-key"})
    return findings


def public_text(text):
    def replace_ip(match):
        try:
            ipaddress.ip_address(match.group())
        except ValueError:
            return match.group()
        return "127.0.0.1"
    text = PRIVATE_IP.sub(replace_ip, text)
    return re.sub(r"[CD]:[/\\]workspace[/\\][A-Za-z0-9_.-]+", "./runtime/public", text)


def synthetic_ring():
    """Generate a short prompt without copying recordings of unknown provenance."""
    buffer = io.BytesIO()
    rate = 8000
    with wave.open(buffer, "wb") as output:
        output.setparams((1, 2, rate, 0, "NONE", "not compressed"))
        for index in range(rate * 2):
            time = index / rate
            active = time < .8
            envelope = max(0, min(1, time / .02, (.8 - time) / .02)) if active else 0
            sample = round(5000 * envelope * (math.sin(2 * math.pi * 440 * time)
                                             + math.sin(2 * math.pi * 480 * time)))
            output.writeframesraw(struct.pack("<h", sample))
    return buffer.getvalue()


def public_test_credentials(relative, text):
    if "__tests__" not in Path(relative).parts and "/src/test/" not in relative:
        return text
    values = {match.group(2) for match in CREDENTIAL.finditer(text)
              if not KNOWN_SAMPLE.fullmatch(match.group(2))}
    for value in sorted(values, key=len, reverse=True):
        text = text.replace(value, "test-public-credential")
    return text


def export(root, output, frontend=None, database="kingbase"):
    root, output = root.resolve(), output.resolve()
    if output.exists():
        raise ValueError("Output must be a new directory; existing files are never replaced")
    modules = [item.text for item in ET.parse(root / "pom.xml").findall(
        "{http://maven.apache.org/POM/4.0.0}modules/{http://maven.apache.org/POM/4.0.0}module")]
    inputs = {}
    for name in ("pom.xml", "LICENSE", "NOTICE.md", ".gitignore"):
        inputs[name] = root / name
    for module in modules:
        inputs[f"{module}/pom.xml"] = root / module / "pom.xml"
        for path in (root / module / "src").rglob("*"):
            if path.is_file():
                inputs[path.relative_to(root).as_posix()] = path
    for directory in ("docs/open-source", "deploy/public", "deploy/demo", "examples/public-demo", "tools/open-source"):
        for path in (root / directory).rglob("*"):
            if (path.is_file() and "__pycache__" not in path.parts and "local" not in path.relative_to(root / directory).parts
                    and not ("freeswitch/upstream/" in path.as_posix() and path.name.endswith('.tar.gz'))):
                inputs[path.relative_to(root).as_posix()] = path
    inputs["README.md"] = root / "docs/open-source/RELEASE_README.md"
    replacements = {
        "och-api/src/main/resources/application.yml": "application.yml",
        "och-file-client/src/main/resources/application.yml": "file-client.yml",
        "och-mrcp/src/main/resources/engine.conf": "mrcp-engine.conf",
        "och-mrcp/src/main/resources/sip.conf": "mrcp-sip.conf",
        "och-mrcp/src/main/resources/mrcp.conf": "mrcp.conf",
    }
    for target, template in replacements.items():
        inputs[target] = root / "deploy/public" / template
    if database == "mysql":
        inputs["och-api/src/main/resources/application.yml"] = root / "deploy/demo/config/application.yml"
        replacements["och-api/src/main/resources/application.yml"] = "../demo/config/application.yml"
    if frontend:
        frontend = frontend.resolve()
        for directory in ("src", "public"):
            for path in (frontend / directory).rglob("*"):
                if path.is_file():
                    inputs["frontend/" + path.relative_to(frontend).as_posix()] = path
        for path in frontend.iterdir():
            if path.is_file() and (path.name in {"LICENSE", "README.md", "index.html", "package.json", "package-lock.json",
                                                "pnpm-lock.yaml", "env.d.ts", ".gitignore", ".prettierrc.json", ".eslintrc.cjs"}
                                   or path.name.startswith("tsconfig") or path.name == "vite.config.ts"):
                inputs["frontend/" + path.name] = path
        inputs["frontend/.env.development"] = root / "deploy/public/frontend.env.example"
        inputs["frontend/.env.production"] = root / "deploy/public/frontend.env.example"
    stage = output / "source"
    records, transformed, findings, generated, test_credentials, excluded = [], [], [], [], [], []
    for relative, source in sorted(inputs.items()):
        if source.suffix.lower() in BACKUP_SUFFIXES or relative == "docs/open-source/RELEASE_README.md":
            excluded.append(relative)
            continue
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"Missing input or symlink: {relative}")
        if any(part in {"node_modules", "target", ".svn", ".git", "__pycache__"} for part in Path(relative).parts):
            raise ValueError(f"Unexpected generated input: {relative}")
        is_generated_ring = relative == "frontend/src/assets/ringing.wav"
        if not is_generated_ring and source.suffix.lower() in {".dump", ".rdb", ".log", ".wav", ".mp3", ".mp4", ".zip", ".jar", ".war", ".pyc"}:
            raise ValueError(f"Unexpected binary or runtime input: {relative}")
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if is_generated_ring:
            target.write_bytes(synthetic_ring())
            generated.append(relative)
        elif source.suffix.lower() in TEXT_SUFFIXES or source.name.startswith(".env"):
            text = source.read_text(encoding="utf-8-sig")
            sanitized = public_text(text)
            if text != sanitized:
                transformed.append(relative)
            public_test = public_test_credentials(relative, sanitized)
            if public_test != sanitized:
                test_credentials.append(relative)
                sanitized = public_test
            target.write_text(sanitized, encoding="utf-8", newline="\n")
            scalar_config = source.suffix.lower() in {".yml", ".yaml", ".properties", ".conf", ".md", ".example"} or source.name.startswith(".env")
            for item in scan_text(sanitized, scalar_config=scalar_config):
                findings.append({"file": relative, **item})
        else:
            shutil.copyfile(source, target)
        records.append({"path": relative, "source_sha256": sha256(source), "export_sha256": sha256(target)})
    manifest = {"format": 1, "files": records, "public_config_replacements": replacements,
                "normalized_local_addresses": transformed, "generated_assets": generated,
                "synthetic_test_credentials": test_credentials,
                "excluded_backups_and_templates": excluded,
                "frontend_included": bool(frontend)}
    (stage / "source-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    report = {"files": len(records), "findings": findings, "archive_created": not findings,
              "scope": "Allowlisted source and public examples; no runtime backups or vendor trees",
              "limits": "Pattern scan does not establish copyright ownership or complete live-call acceptance"}
    (output / "release-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if findings:
        raise ValueError(f"Archive withheld: {len(findings)} findings; inspect release-report.json (values are redacted)")
    sums = "".join(f"{sha256(path)}  {path.relative_to(stage).as_posix()}\n"
                   for path in sorted(stage.rglob("*")) if path.is_file())
    (stage / "SHA256SUMS").write_text(sums, encoding="utf-8")
    archive = output / "OpenCallHub-public-source.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for path in sorted(stage.rglob("*")):
            if path.is_file():
                bundle.write(path, path.relative_to(stage).as_posix())
    (output / "SHA256SUMS").write_text(f"{sha256(archive)}  {archive.name}\n", encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--frontend", type=Path)
    parser.add_argument("--database", choices=("kingbase", "mysql"), default="kingbase")
    args = parser.parse_args()
    print(json.dumps(export(args.root, args.output, args.frontend, args.database)))


if __name__ == "__main__":
    main()
