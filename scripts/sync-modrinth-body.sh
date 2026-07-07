#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [ -z "${MODRINTH_TOKEN:-}" ]; then
  echo "MODRINTH_TOKEN is required. Set it in the environment or in .env." >&2
  exit 1
fi

./gradlew --no-configuration-cache modrinthSyncBody
