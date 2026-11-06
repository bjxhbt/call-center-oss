"""Create public DDL from a custom-format backup without exporting table contents."""
import argparse
import hashlib
import re
import subprocess
import tempfile
from pathlib import Path


def without_transaction(text):
    return re.sub(r"(?m)^(?:BEGIN|COMMIT);\s*$", "", text).strip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restore", type=Path, required=True)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--refresh-upgrades", action="store_true",
                        help="Maintainer checkout only: rebuild upgrades/menus from docs/sql sources")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="och-schema-") as temporary:
        extracted = Path(temporary) / "schema.sql"
        subprocess.run([str(args.restore), "--schema-only", "--schema=public", "--no-owner", "--no-privileges",
                        "--no-comments", "--no-security-labels", f"--file={extracted}", str(args.backup)], check=True)
        content = extracted.read_bytes()
    encoding = re.search(rb"SET client_encoding = '([^']+)'", content)
    codec = encoding.group(1).decode("ascii") if encoding else "utf-8"
    text = content.decode(codec)
    text = re.sub(r"SET client_encoding = '[^']+';", "SET client_encoding = 'UTF8';", text)
    text = re.sub(r"(?m)^--.*\n?", "", text)
    # Original IDs were smallint; current Java entities and snowflake call IDs use Long.
    text = re.sub(r"(?m)^(\s*(?:[A-Za-z_]\w*_id|id|create_by|update_by)\s+)(?:smallint|tinyint)\b", r"\1bigint", text)
    text = re.sub(r"(?m)^(\s*AS )(?:smallint|tinyint)\b", r"\1bigint", text)
    if re.search(r"(?im)^\s*(?:COPY\s|INSERT\s|SELECT\s+pg_catalog\.setval)", text):
        raise ValueError("Unexpected data statements in schema-only export")
    destination = args.root / "deploy/public"
    destination.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256(args.backup.read_bytes()).hexdigest()
    header = ("-- KingbaseES public schema only; no rows, owners, grants or comments.\n"
              f"-- Source backup SHA-256: {digest}\n"
              "-- ID columns and sequences widened to bigint for the current Java entities.\n")
    (destination / "01-schema.sql").write_text(header + text.strip() + "\n", encoding="utf-8")
    print(f"Exported {len(re.findall(r'CREATE TABLE ', text))} base tables; no table data exported")
    if not args.refresh_upgrades:
        return
    migrations = ["release_schema_20260813.sql", "create_number_transform_rule.sql",
                  "fix_call_schedule_level_keyword.sql"]
    upgrades = []
    for migration in migrations:
        sql = (args.root / "docs/sql" / migration).read_text(encoding="utf-8-sig")
        upgrades.append(f"-- Source: docs/sql/{migration}\n" + without_transaction(sql))
    upgrades.append("ALTER TABLE call_record ADD CONSTRAINT uk_call_record_call_id UNIQUE (call_id);\n")
    (destination / "02-upgrade.sql").write_text("\n".join(upgrades), encoding="utf-8")
    menus = (args.root / "docs/sql/release_menu_20260813.sql").read_text(encoding="utf-8-sig")
    (destination / "04-menus.sql").write_text(without_transaction(menus), encoding="utf-8")


if __name__ == "__main__":
    main()
