#!/usr/bin/env python3
import argparse
import json
import os
import shlex
import subprocess
import sys
import tempfile
from pathlib import Path


def read_properties(path):
    properties = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()

    return properties


def changelog_heading(line):
    stripped = line.strip()
    if not stripped.startswith("## "):
        return None

    return stripped[3:].strip()


def heading_matches_version(heading, version):
    return heading == version or heading.startswith(f"{version} - ")


def read_current_changelog(path, version):
    lines = path.read_text(encoding="utf-8").splitlines()
    heading_index = None
    title = None

    for index, line in enumerate(lines):
        heading = changelog_heading(line)
        if heading is not None and heading_matches_version(heading, version):
            heading_index = index
            title = heading
            break

    if heading_index is None:
        raise ValueError(f"CHANGELOG.md must contain a section for ## {version}")

    body_lines = []
    for line in lines[heading_index + 1:]:
        if changelog_heading(line) is not None:
            break
        body_lines.append(line)

    body = "\n".join(body_lines).strip()
    return {
        "title": title or version,
        "body": body or f"Release {version}.",
    }


def run(command):
    print("+", shlex.join(command))
    subprocess.run(command, check=True)


def release_exists(tag, repo):
    result = subprocess.run(
        ["gh", "release", "view", tag, "--repo", repo],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return result.returncode == 0


def write_notes_file(version, body):
    runner_temp = Path(os.environ.get("RUNNER_TEMP", tempfile.gettempdir()))
    path = runner_temp / f"cc-smartcard-{version}-github-release-notes.md"
    path.write_text(body, encoding="utf-8")
    return path


def publish_release(tag, repo, title, notes_file, asset_path, prerelease):
    if release_exists(tag, repo):
        command = [
            "gh",
            "release",
            "edit",
            tag,
            "--repo",
            repo,
            "--title",
            title,
            "--notes-file",
            str(notes_file),
        ]
        if prerelease:
            command.append("--prerelease")
        run(command)
    else:
        command = [
            "gh",
            "release",
            "create",
            tag,
            "--repo",
            repo,
            "--title",
            title,
            "--notes-file",
            str(notes_file),
            "--verify-tag",
        ]
        if prerelease:
            command.append("--prerelease")
        run(command)

    run([
        "gh",
        "release",
        "upload",
        tag,
        str(asset_path),
        "--repo",
        repo,
        "--clobber",
    ])


def parse_args():
    parser = argparse.ArgumentParser(description="Create or update a GitHub Release for this mod.")
    parser.add_argument("--project-root", default=".", help="Repository root. Defaults to the current directory.")
    parser.add_argument("--tag", default=os.environ.get("GITHUB_REF_NAME"), help="Git tag to publish, such as v0.3.0.")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"), help="GitHub repository, such as wxdao/cc-smartcard.")
    parser.add_argument("--version-type", default=os.environ.get("MODRINTH_VERSION_TYPE", "beta"), choices=["release", "beta", "alpha"])
    parser.add_argument("--dry-run", action="store_true", help="Print computed release metadata without calling gh.")
    return parser.parse_args()


def main():
    args = parse_args()
    root = Path(args.project_root).resolve()

    if not args.tag:
        raise ValueError("--tag is required, or GITHUB_REF_NAME must be set")
    if not args.repo:
        raise ValueError("--repo is required, or GITHUB_REPOSITORY must be set")

    properties = read_properties(root / "gradle.properties")
    version = properties.get("mod_version")
    mod_id = properties.get("mod_id")
    if not version:
        raise ValueError("gradle.properties does not define mod_version")
    if not mod_id:
        raise ValueError("gradle.properties does not define mod_id")

    expected_tag = f"v{version}"
    if args.tag != expected_tag:
        raise ValueError(f"Tag {args.tag} does not match gradle.properties mod_version {version}")

    changelog = read_current_changelog(root / "CHANGELOG.md", version)
    asset_path = root / "build" / "libs" / f"{mod_id}-{version}.jar"
    if not asset_path.is_file():
        raise ValueError(f"Release asset does not exist: {asset_path}")

    notes_file = write_notes_file(version, changelog["body"])
    prerelease = args.version_type != "release"

    metadata = {
        "tag": args.tag,
        "repo": args.repo,
        "title": changelog["title"],
        "notes_file": str(notes_file),
        "asset_path": str(asset_path),
        "version_type": args.version_type,
        "prerelease": prerelease,
    }

    if args.dry_run:
        print(json.dumps(metadata, indent=2))
        return 0

    publish_release(
        tag=args.tag,
        repo=args.repo,
        title=changelog["title"],
        notes_file=notes_file,
        asset_path=asset_path,
        prerelease=prerelease,
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
