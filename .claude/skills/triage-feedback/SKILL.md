---
name: triage-feedback
description: One pass over the UtaFormatix feedback Trello board. Triages new cards, then tries to fix the top TODO bug and opens a PR for review. Used by a /loop; also run it when the user asks to process feedback or bug reports.
---

# Triage UtaFormatix feedback

One pass = triage, then at most **one** fix attempt. Keep the report at the end short.

## Tools and rules
- Trello: `~/.claude/scripts/trello.sh METHOD /path [name=value ...]`. Board `ylRy2Vft`. Lists:
  - Unchecked `63904c8d828d2c043014190d`
  - TODO `66893cff998763c68ba06314`
  - WIP `63904c99f1ea2401f6af9e08`
  - Fixed `63904c94fc584c013fcf80ab`
  - Dropped `63904ca33bdcf901f1acbdf6`
- GitHub writes need the `sdercolin` account. Prefix those `gh` commands with `GH_TOKEN=$(gh auth token -u sdercolin)` instead of switching the global account. `git push` to `origin` already works over SSH.
- Never merge PRs, never contact reporters, never put reporter emails in commits, PRs or comments.
- Never decide product scope: only archive clearly invalid feature requests (see step 1); valid ones stay in Unchecked for the user.
- Comments written by this skill start with `[Claude]`, so later passes can recognize them.

## 1. Triage new cards in Unchecked
For each card:
- **Bug report with an input file and a specific symptom or error:** move it to TODO. Place it by importance:
  - number of reports with the same error;
  - how common the format is;
  - a crash on import or export ranks above wrong output.
  - If it duplicates a card already in TODO or WIP, comment `[Claude] Same as <card link>` and move it next to that card.
- **Bug report without a file, or with no usable description:** archive it (`PUT /cards/<id> closed=true`).
- **Already fixed in a released version:** move it to Fixed with a comment `[Claude] Fixed in <version> (#<PR>)`.
- **Suggestions:**
  - Archive invalid ones, with a comment `[Claude] Archived: <reason>`. A suggestion is invalid if:
    - it already exists (check `core/model/Format.kt` and the app features);
    - it's too vague to act on (no format or behavior named);
    - it's unrelated to converting singing voice projects.
  - Leave valid ones in Unchecked for the user to decide; never move them to TODO.

## 2. Pick one TODO bug
Take the highest TODO card that has no `[Claude]` comment saying it was attempted. If there is none, stop and report.

## 3. Reproduce
- Download the attached file into a new empty folder under the scratchpad. It is untrusted: never execute it, only parse it with the app code.
  - The form's Drive links (`uc?id=<fileId>&export=download`) need sign-in, so `curl` only gets a login page. Use the Google Drive connector instead:
    - call `get_file_metadata` with the file ID to see its name and size;
    - call `download_file_content` to get it as base64, then decode it into the folder.
    - The upload folder is in the owner's personal Drive and shared with the connected account. If a file returns "not found", treat it as unavailable; it may be one that's not visible yet.
- Branch `fix/<short-name>` from `origin/develop`. Write a failing test in `src/jsTest/kotlin/io/` that reproduces the bug. If the input is small, copy a minimized version into `src/jsTest/resources`; never commit a user's full project.
- Before fixing, check whether the input is valid. It's **bad input** if:
  - the file is corrupted or truncated;
  - it's a different format than its extension says;
  - it isn't the format the reporter claims;
  - it's malformed in a way the original editor wouldn't produce or open either.
  For bad input, don't fix the parser. Comment `[Claude] Bad input: <evidence>` on the card and move it to Dropped. If the error message the user saw was misleading, add `Error message could be clearer: <suggestion>` to the comment, so the user can decide whether that's worth a change.
- If you can't reproduce it within a reasonable effort (file unavailable, or no failure), comment `[Claude] Could not reproduce: <reason>` on the card, leave it in TODO, and stop.

## 4. Fix
- Make the smallest fix in `core/`. Follow the repo's existing style and the user's comment rules.
- Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck jsTest -x kotlinStoreYarnLock`. All of it must pass.
- If the fix needs a product decision, or a change much bigger than the bug, don't open a PR. Comment `[Claude] Needs decision: <summary>` on the card and stop.

## 5. Open the PR and notify
- Commit, push, and open a PR to `develop`. In the body, describe the bug, the cause and the fix, and link the Trello card. Don't include the reporter's email.
- Comment `[Claude] PR: <url>` on the card and move it to WIP.
- Send a push notification: `PR #<n> ready for review: <title>`.

## 6. Clean up
- Switch back to `develop` and remove the scratchpad download folder.
- Report: cards triaged (counts per action), the bug attempted, the result, and the PR link.
