"""Calculate reproducible ASR metrics from a UTF-8 CSV; uses only the standard library."""
import argparse
import csv
import json
import math
import statistics
import unicodedata
from collections import defaultdict
from pathlib import Path


def normalize(text):
    text = unicodedata.normalize("NFKC", text).casefold()
    return "".join(c for c in text if not c.isspace() and not unicodedata.category(c).startswith("P"))


def distance(reference, hypothesis):
    previous = list(range(len(hypothesis) + 1))
    for i, char in enumerate(reference, 1):
        current = [i]
        for j, other in enumerate(hypothesis, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (char != other)))
        previous = current
    return previous[-1]


def evaluate(rows):
    groups = defaultdict(list)
    identities = set()
    for number, row in enumerate(rows, 2):
        provider = row["provider"].strip().upper()
        source = row["source"].strip()
        if not provider or not row["file"].strip():
            raise ValueError(f"Row {number}: file and provider are required")
        if source not in {"synthetic", "recorded", "real_phone", "mock"}:
            raise ValueError(f"Row {number}: unsupported audio source")
        if provider == "MOCK" and source != "mock":
            raise ValueError(f"Row {number}: MOCK results must use source=mock")
        if row["result"] not in {"success", "partial", "failed"}:
            raise ValueError(f"Row {number}: invalid result")
        identity = (row["file"], provider, source)
        if identity in identities:
            raise ValueError(f"Row {number}: duplicate file/provider/source")
        identities.add(identity)
        reference = normalize(row["reference_text"])
        hypothesis = normalize(row["hypothesis_text"])
        if not reference:
            raise ValueError(f"Row {number}: reference_text is required")
        if row["result"] == "failed":
            hypothesis = ""
        keywords = sorted({normalized for keyword in row.get("keywords", "").split("|")
                           if (normalized := normalize(keyword))})
        latencies = {}
        for field in ("first_text_latency_ms", "final_text_latency_ms"):
            value = row.get(field, "").strip()
            parsed = float(value) if value else None
            if parsed is not None and (not math.isfinite(parsed) or parsed < 0):
                raise ValueError(f"Row {number}: {field} must be a finite nonnegative number")
            latencies[field] = parsed
        groups[(provider, source)].append({
            "errors": distance(reference, hypothesis), "chars": len(reference),
            "hits": sum(k in hypothesis for k in keywords), "keywords": len(keywords),
            "result": row["result"], **latencies,
        })
    summaries = []
    for (provider, source), items in sorted(groups.items()):
        total_chars = sum(item["chars"] for item in items)
        keywords = sum(item["keywords"] for item in items)
        summary = {
            "provider": provider, "source": source, "samples": len(items),
            "successes": sum(item["result"] == "success" for item in items),
            "partials": sum(item["result"] == "partial" for item in items),
            "failures": sum(item["result"] == "failed" for item in items),
            "cer": sum(item["errors"] for item in items) / total_chars,
            "keyword_hit_rate": sum(item["hits"] for item in items) / keywords if keywords else None,
        }
        for field in ("first_text_latency_ms", "final_text_latency_ms"):
            values = sorted(item[field] for item in items if item[field] is not None and item["result"] != "failed")
            summary[field] = {"measured": len(values),
                              "median": statistics.median(values) if values else None,
                              "p95": values[math.ceil(len(values) * .95) - 1] if values else None}
        summaries.append(summary)
    if not summaries:
        raise ValueError("No measured results in input CSV")
    return {"normalization": "NFKC + casefold; remove whitespace and punctuation",
            "cer_method": "sum edit distances / sum reference characters, including failed samples",
            "final_latency_definition": "end of utterance audio to final result",
            "groups": summaries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with args.csv.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(line for line in stream if not line.startswith("#"))
        result = evaluate(reader)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(result['groups'])} separate provider/source groups to {args.output}")


if __name__ == "__main__":
    main()
