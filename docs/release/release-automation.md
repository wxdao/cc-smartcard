# Release Automation

This project publishes to Modrinth through the official Minotaur Gradle plugin.

## Modrinth Project

- Project URL: https://modrinth.com/mod/cc-smartcard
- Project slug: `cc-smartcard`
- Gradle project id default: `cc-smartcard`

## GitHub Secret

Create a repository secret named `MODRINTH_TOKEN` in:

`wxdao/cc-smartcard` -> Settings -> Secrets and variables -> Actions -> New repository secret

The GitHub secret used by the publish workflow needs this Modrinth scope:

- `CREATE_VERSION`, to publish versions.

The workflow also uses the built-in `GITHUB_TOKEN` to create GitHub Releases and upload release assets for tag builds.

If the GitHub CLI is logged in locally, this command can set the secret without printing the token:

```console
read -s MODRINTH_TOKEN
gh secret set MODRINTH_TOKEN --repo wxdao/cc-smartcard --body "$MODRINTH_TOKEN"
unset MODRINTH_TOKEN
```

## Publishing From A Tag

Before pushing a release tag:

1. Update `mod_version` in `gradle.properties`.
2. Add the matching top section to `CHANGELOG.md`, such as `## 0.2.0 - Fingerprint Scanner`.
3. Push a tag named `vX.Y.Z`, for example `v0.2.0`.

The workflow runs `./gradlew build runGameTestServer` on `main` pushes, release tags, and manual runs. Release tags publish a Modrinth version, run `scripts/publish-github-release.py`, create or update the matching GitHub Release, and upload `build/libs/cc_smartcard-${mod_version}.jar` as a release asset. The GitHub Release title and body come from the matching `CHANGELOG.md` section, using the same version section as the Gradle Modrinth changelog. If the tag workflow is rerun, the existing GitHub Release is updated and the jar asset is replaced.

Manual runs publish only a Modrinth version and do not create a GitHub Release. `main` pushes do not publish anything; they validate the default branch and keep caches warm. The workflow does not run `clean` so the restored NeoForm cache under `build/neoForm` remains available. GitHub runners start from a fresh checkout, and `build/libs` is not cached, so the published jar is still produced from the current source. For a fully cold build, clear the relevant GitHub Actions caches or run `./gradlew clean` locally.

GitHub Actions uses two cache layers for release builds. `gradle/actions/setup-gradle` caches Gradle User Home; tag builds read that cache, while `main` and manual runs can write it so the default branch stays warm. A separate `actions/cache` entry stores NeoGradle/UserDev workspace state from `.gradle/repositories`, `.gradle/caches/minecraft`, and `build/neoForm`, keyed by runner OS, MC/NeoForge/CC:T/Parchment versions, and Gradle build configuration files. Tag builds restore this cache but do not save tag-scoped entries. The key is intentionally not based on `mod_version`, because release version bumps do not invalidate the NeoGradle workspace. If these caches miss or expire, the next `main` or manual run will rebuild and prewarm them.

## Syncing The Modrinth Body

Sync the Modrinth project body from `README.md` locally instead of through GitHub Actions:

```console
python3 scripts/sync-modrinth-body.py
```

The script loads `.env` from the repository root when it exists, so local tokens can be stored there. This local token needs `PROJECT_WRITE`:

```console
MODRINTH_TOKEN=...
```

Use `--dry-run` to check the command without syncing:

```console
python3 scripts/sync-modrinth-body.py --dry-run
```

## Manual Publishing

The `Publish to Modrinth` workflow can also be run manually with `workflow_dispatch`.

Manual runs can choose version type: `release`, `beta`, or `alpha`. Manual runs publish to Modrinth only; they intentionally do not create or update GitHub Releases, to avoid accidental GitHub release publication from ad hoc builds.

## Local Checks

Use this to verify the Minotaur tasks are registered:

```console
./gradlew tasks --all | rg modrinth
```

Use this to preview the GitHub Release metadata without creating a release:

```console
python3 scripts/publish-github-release.py --tag v0.3.0 --repo wxdao/cc-smartcard --dry-run
```

Do not run `./gradlew modrinth` locally unless `MODRINTH_TOKEN` is intentionally set for publishing.
