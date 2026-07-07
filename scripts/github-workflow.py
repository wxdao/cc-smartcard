#!/usr/bin/env python3
import argparse
import sys
from pathlib import Path


NEOGRADLE_CACHE_INPUTS = {
    "NEOGRADLE_CACHE_MINECRAFT_VERSION": "minecraft_version",
    "NEOGRADLE_CACHE_NEO_VERSION": "neo_version",
    "NEOGRADLE_CACHE_CCT_VERSION": "cct_version",
    "NEOGRADLE_CACHE_PARCHMENT_MINECRAFT_VERSION": "neogradle.subsystems.parchment.minecraftVersion",
    "NEOGRADLE_CACHE_PARCHMENT_MAPPINGS_VERSION": "neogradle.subsystems.parchment.mappingsVersion",
}


def read_properties(path):
    properties = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()

    return properties


def append_env(path, values):
    with path.open("a", encoding="utf-8") as handle:
        for key, value in values.items():
            if "\n" in value:
                raise ValueError(f"{key} must be a single-line value")
            handle.write(f"{key}={value}\n")


def require_property(properties, name):
    value = properties.get(name, "")
    if not value:
        raise ValueError(f"gradle.properties does not define {name}")
    return value


def write_neogradle_cache_env(args):
    properties = read_properties(args.project_root / "gradle.properties")
    values = {
        env_name: require_property(properties, property_name)
        for env_name, property_name in NEOGRADLE_CACHE_INPUTS.items()
    }
    append_env(args.github_env, values)


def write_publish_env(args):
    version_type = "beta"
    if args.event_name == "workflow_dispatch" and args.version_type:
        version_type = args.version_type

    append_env(args.github_env, {"MODRINTH_VERSION_TYPE": version_type})


def verify_tag(args):
    properties = read_properties(args.project_root / "gradle.properties")
    mod_version = require_property(properties, "mod_version")
    expected_tag = f"v{mod_version}"

    if args.tag != expected_tag:
        raise ValueError(f"Tag {args.tag} does not match gradle.properties mod_version {mod_version}")


def parse_args():
    parser = argparse.ArgumentParser(description="Small helpers for the GitHub Actions release workflow.")
    parser.add_argument("--project-root", type=Path, default=Path("."), help="Repository root.")
    subcommands = parser.add_subparsers(dest="command", required=True)

    cache_env = subcommands.add_parser("write-neogradle-cache-env")
    cache_env.add_argument("--github-env", type=Path, required=True)
    cache_env.set_defaults(func=write_neogradle_cache_env)

    publish_env = subcommands.add_parser("write-publish-env")
    publish_env.add_argument("--github-env", type=Path, required=True)
    publish_env.add_argument("--event-name", required=True)
    publish_env.add_argument("--version-type", choices=["", "release", "beta", "alpha"], default="")
    publish_env.set_defaults(func=write_publish_env)

    tag_check = subcommands.add_parser("verify-tag")
    tag_check.add_argument("--tag", required=True)
    tag_check.set_defaults(func=verify_tag)

    return parser.parse_args()


def main():
    args = parse_args()
    args.project_root = args.project_root.resolve()
    args.func(args)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
