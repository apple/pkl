# Guidelines for AI agents

Quick notes for automated contributors. They do not replace the human docs:
**read `CONTRIBUTING.adoc` and `DEVELOPMENT.adoc` before opening a pull
request**, and follow them.

## Pull requests

The essentials from `CONTRIBUTING.adoc`:

- Imitate the conventions of the surrounding code.
- Format with `./gradlew spotlessApply` (an unformatted build fails).
- Verify both `./gradlew build` (JVM) and `./gradlew buildNative` (native) pass.
- Write a Git commit message that follows the seven rules: a capitalized,
  imperative subject line, a blank line, then a body explaining what and why.

**Do not edit the changelog or release notes.** The files under
`docs/modules/release-notes/` are curated by maintainers at release time;
contributor PRs leave them unchanged. Put context in the pull request
description and reference the relevant issue instead.

## Tests

- Add tests beside the existing ones and run the smallest relevant task, for
  example `./gradlew :pkl-parser:test`.
- Name Kotlin (JUnit) test methods in backtick "space case", not camelCase:
  `` fun `rejects sentinel between tokens`() { ... } ``.
