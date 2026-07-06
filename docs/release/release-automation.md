# Release Automation

This project publishes to Modrinth through the official Minotaur Gradle plugin.

## Modrinth Project

- Project URL: https://modrinth.com/mod/cc-smartcard
- Project slug: `cc-smartcard`
- Gradle project id default: `cc-smartcard`

## GitHub Secret

Create a repository secret named `MODRINTH_TOKEN` in:

`wxdao/cc-smartcard` -> Settings -> Secrets and variables -> Actions -> New repository secret

The token needs these Modrinth scopes:

- `CREATE_VERSION`, to publish versions.
- `PROJECT_WRITE`, because the workflow syncs `README.md` to the Modrinth project body by default.

If the GitHub CLI is logged in locally, this command can set the secret without printing the token:

```sh
read -s MODRINTH_TOKEN
gh secret set MODRINTH_TOKEN --repo wxdao/cc-smartcard --body "$MODRINTH_TOKEN"
unset MODRINTH_TOKEN
```

## Publishing From A Tag

Before pushing a release tag:

1. Update `mod_version` in `gradle.properties`.
2. Add the matching top section to `CHANGELOG.md`, such as `## 0.2.0 - Fingerprint Scanner`.
3. Push a tag named `vX.Y.Z`, for example `v0.2.0`.

The tag workflow runs `./gradlew clean` and then `./gradlew build runGameTestServer`, syncs the Modrinth body, then publishes the built jar with version type `beta`.

## Manual Publishing

The `Publish to Modrinth` workflow can also be run manually with `workflow_dispatch`.

Manual runs can choose:

- Version type: `release`, `beta`, or `alpha`.
- Whether to sync `README.md` to the Modrinth project body.

## Local Checks

Use this to verify the Minotaur tasks are registered:

```sh
./gradlew tasks --all | rg modrinth
```

Do not run `./gradlew modrinth` or `./gradlew modrinthSyncBody` locally unless `MODRINTH_TOKEN` is intentionally set for publishing.
