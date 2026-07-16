#!/usr/bin/env python3
import argparse
import json
import os
import shlex
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


MODRINTH_PROJECT = "cc-smartcard"
MODRINTH_API = "https://api.modrinth.com/v2"


def parse_dotenv_value(value):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def load_dotenv(path):
    if not path.is_file():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        key = key.strip()
        if key and key not in os.environ:
            os.environ[key] = parse_dotenv_value(value)


def run(command, cwd):
    print("+", shlex.join(command))
    subprocess.run(command, cwd=cwd, check=True)


def read_summary(path):
    summary = path.read_text(encoding="utf-8").strip()
    if not summary or "\n" in summary:
        raise RuntimeError(f"Expected one non-empty line in {path}")
    return summary


def sync_summary(summary, token):
    request = urllib.request.Request(
        f"{MODRINTH_API}/project/{MODRINTH_PROJECT}",
        data=json.dumps({"description": summary}).encode("utf-8"),
        headers={
            "Authorization": token,
            "Content-Type": "application/json",
            "User-Agent": "wxdao/cc-smartcard (https://github.com/wxdao/cc-smartcard)",
        },
        method="PATCH",
    )
    try:
        with urllib.request.urlopen(request) as response:
            if response.status not in {200, 204}:
                raise RuntimeError(f"Modrinth summary sync returned HTTP {response.status}")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Modrinth summary sync failed with HTTP {error.code}: {detail}") from error


def main():
    parser = argparse.ArgumentParser(description="Sync the Modrinth project summary and body from docs.")
    parser.add_argument("--dry-run", action="store_true", help="Show what would run without updating Modrinth.")
    parser.add_argument(
        "--include-summary",
        action="store_true",
        help="Also update the public project Summary; use this when the matching release is published.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    load_dotenv(repo_root / ".env")
    summary = read_summary(repo_root / "docs" / "modrinth-summary.txt") if args.include_summary else None

    if not os.environ.get("MODRINTH_TOKEN"):
        print("MODRINTH_TOKEN is required. Set it in the environment or in .env.", file=sys.stderr)
        return 1

    command = ["./gradlew", "--no-configuration-cache", "modrinthSyncBody"]
    if args.dry_run:
        print("MODRINTH_TOKEN is configured.")
        print("+", shlex.join(command))
        if summary is not None:
            print(f"+ PATCH {MODRINTH_API}/project/{MODRINTH_PROJECT}")
            print("Summary:", summary)
        return 0

    run(command, cwd=repo_root)
    if summary is not None:
        sync_summary(summary, os.environ["MODRINTH_TOKEN"])
        print("Modrinth summary synced:", summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
