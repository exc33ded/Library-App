"""Score batch-import results against ground-truth labels.

Usage:
  python -I tools/benchmark.py <library.db> <labels.csv> <media_ids.txt>

Inputs (from the emulator/phone):
  adb shell am force-stop com.exc33ded.library
  adb exec-out run-as com.exc33ded.library cat databases/library.db > library.db
  adb shell content query --uri content://media/external/images/media --projection _id:_display_name > media_ids.txt

Picker URIs end in the MediaStore _id; media_ids.txt maps that id to the original file name.
A photo counts as matched when >= 60% of the label's title words appear in the found title.
"""
import csv
import json
import re
import sqlite3
import sys


def words(s):
    return re.sub(r"[^a-z0-9 ]", " ", (s or "").lower()).split()


def title_ok(found, truth):
    f, t = set(words(found)), words(truth)
    return bool(t) and sum(w in f for w in t) / len(t) >= 0.6


def media_names(path):
    names = {}
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.search(r"_id=(\d+), _display_name=(\S+)", line)
        if m:
            names[m.group(1)] = m.group(2)
    return names


def main(db_path, labels_path, media_path):
    labels = {r["file"]: r for r in csv.DictReader(open(labels_path, encoding="utf-8"))}
    names = media_names(media_path)
    rows = sqlite3.connect(db_path).execute(
        "SELECT source, state, query, resultJson, error FROM import_queue ORDER BY createdAt"
    ).fetchall()

    ok, wrong, failed, unknown = [], [], [], []
    for source, state, query, result, error in rows:
        if source.startswith("/"):  # file path (camera / debug ImportDirReceiver)
            name = source.rsplit("/", 1)[-1]
        else:  # picker content:// URI ending in the MediaStore _id
            media_id = re.search(r"(\d+)$", source)
            name = names.get(media_id.group(1)) if media_id else None
        truth = labels.get(name)
        if truth is None:
            unknown.append(source)
            continue
        if state == "FAILED":
            failed.append((name, truth["title"], error, query))
        elif state in ("REVIEW", "DONE"):
            found = json.loads(result).get("title") if result else None
            (ok if title_ok(found, truth["title"]) else wrong).append((name, truth["title"], found, query))
        else:
            unknown.append(f"{source} still {state}")

    scored = len(ok) + len(wrong) + len(failed)
    print(f"photos={len(rows)} scored={scored} matched={len(ok)} ({len(ok) / max(scored, 1):.0%}) "
          f"wrong={len(wrong)} failed={len(failed)} unmapped={len(unknown)}")
    for name, truth, found, query in wrong:
        print(f"  WRONG  {truth!r} -> got {found!r}   (query: {query!r})")
    for name, truth, error, query in failed:
        print(f"  FAILED {truth!r}: {error}   (query: {query!r})")
    for u in unknown:
        print(f"  ?      {u}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # Windows console default is cp1252
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:4])
