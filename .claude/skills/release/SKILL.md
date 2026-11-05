---
name: release
description: Release a new UtaFormatix version. Bumps APP_VERSION, writes release notes, merges develop into master, and tags a GitHub release. Use when the user asks to release, ship, or publish a new version (including hotfixes).
---

# Release UtaFormatix

## Background
- `develop` deploys to `/beta`, and `master` deploys to production. Both are deployed by `.github/workflows/deploy.yml` on every push.
- The version lives in `src/jsMain/kotlin/Main.kt` (`APP_VERSION`).
- Release notes live in `src/jsMain/resources/release-notes.md`. The app shows that file from its own deployment, so beta shows the beta notes.
- Pushing, merging and releasing on `sdercolin/utaformatix3` needs the `sdercolin` account. If `gh auth status` shows another active account, ask before switching. Switch with `gh auth switch -u sdercolin`, and switch back to the original account at the end, even if a step fails.

## Steps

1. **Check the state.**
   - `git fetch origin`.
   - Make sure the working tree is clean.
   - List the changes in the release: `git log --oneline --merges --first-parent origin/master..origin/develop`, plus `gh pr list --state merged --base develop` for titles.
   - Check CI on develop: `gh run list --branch develop --limit 3`. Stop if the latest run failed.

2. **Decide the version and the notes.** Read the current `APP_VERSION`.
   - The convention is to bump the minor version (`3.24` → `3.25`) for features and new formats, and to add a patch number (`3.24` → `3.24.1`) for fixes only.
   - Draft user-facing notes from the merged PRs. Don't list internal changes (CI, refactors, tests) unless they affect users. Credit external contributors' PRs.
   - Show the proposed version and notes to the user, and wait for approval before changing anything.

3. **Prepare the release on develop.**
   - Create a branch `release/<version>` from `origin/develop`.
   - Set `APP_VERSION = "<version>"`.
   - Add the new section to the top of `release-notes.md`, directly under the `# UtaFormatix - Release Notes` heading:
     ```
     #### <version> (<YYYY.M.D>)
     - <note>
     ```
     Use today's date with no zero padding, matching the existing entries.
   - Run `./gradlew ktlintCheck jsTest -x kotlinStoreYarnLock`. Use JDK 21 locally: `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
   - Commit (`Release <version>`), push, and open a PR to `develop`. Wait for CI to pass, then merge it with `gh pr merge --merge`.

4. **Release to master.**
   - Open a PR from `develop` to `master` titled `Release <version>`, with the notes as the body.
   - Wait for CI. Ask the user to confirm before merging, because the merge deploys to production. Then merge with `gh pr merge --merge`. Don't squash, so that develop and master share history.

5. **Tag and publish.**
   - Create the release: `gh release create v<version> --target master --title "<version>" --notes "<notes>"`.
   - Watch the deploy: `gh run list --workflow deploy.yml --branch master --limit 1`, then `gh run watch <id>`.
   - Report the release URL and the deploy result.

6. **Update the Trello board.**
   - Board: `UtaFormatix feedback` (https://trello.com/b/ylRy2Vft). User feedback lands in **Unchecked**, and the other lists are **TODO**, **WIP**, **Fixed** and **Dropped**.
   - Use `~/.claude/scripts/trello.sh METHOD /path [name=value ...]`. It reads `TRELLO_KEY` and `TRELLO_TOKEN` from `~/.zshrc`. If the script is missing, ask the user instead of calling the API another way.
   - Cards don't link to PRs. Find the ones this release fixes by matching each fix against the cards in Unchecked, TODO and WIP (error message, format, symptom). Show the matches to the user and wait for approval.
   - Move each approved card to **Fixed** (`PUT /cards/<id> idList=<Fixed list id>`), and comment `Fixed in <version> (#<PR>): <one-line summary>` (`POST /cards/<id>/actions/comments text=...`).

## Hotfix
For an urgent fix that must skip unreleased changes on develop:
- Branch `hotfix/<version>` from `origin/master`, apply the fix, bump the version and add the notes, then open a PR to `master`.
- After it's released, merge `master` back into `develop`.
