#!/usr/bin/env python3
import os
import shlex
import subprocess
import sys
import argparse
from pathlib import Path


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


def main():
    parser = argparse.ArgumentParser(description="Sync docs/modrinth.md to the Modrinth project body.")
    parser.add_argument("--dry-run", action="store_true", help="Show what would run without calling Gradle.")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    load_dotenv(repo_root / ".env")

    if not os.environ.get("MODRINTH_TOKEN"):
        print("MODRINTH_TOKEN is required. Set it in the environment or in .env.", file=sys.stderr)
        return 1

    command = ["./gradlew", "--no-configuration-cache", "modrinthSyncBody"]
    if args.dry_run:
        print("MODRINTH_TOKEN is configured.")
        print("+", shlex.join(command))
        return 0

    run(command, cwd=repo_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
