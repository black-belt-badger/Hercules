# Contributing to Hercules

Thank you for helping build **Hercules**, an incremental functional programming library for Java 17+.

## Before coding

- Look through the [public issues](https://github.com/black-belt-badger/hercules/issues) and [project board](https://github.com/orgs/black-belt-badger/projects/11/views/2).
- Open or link an issue before changing a public API; discuss semantics and algebraic laws.
- Aim for one focused, reviewable pull request per issue.
- Prefer standard JDK interfaces, independent utilities, and explicit immutable inputs/outputs.
- Use `bbb.hercules` as the canonical Java package root. BBB projects follow `bbb.<project>`; Maven group coordinates are a separate publication concern.
- Use lowercase GitHub repository names for BBB projects; use kebab-case when a name has multiple words (for example, `personal-mentat`). Project names in prose and branding can retain capitalization.
- Keep the functional core pure. Describe effects (including diagnostic logging) as data; execute I/O only at explicit boundaries.
- Only Guava, Lombok and JSpecify are currently approved production dependencies; changes to dependency policy require review.

## Build and test

Use JDK 17 or newer. Maven is provided through the wrapper:

```sh
./mvnw -B -ntp clean verify
```

Java 17 is the language compatibility baseline even if contributors run the build on Java 21 or 25. GitHub Actions verifies all three JDKs. No credentials, accounts or network services should be required by unit tests.

## Tests and API review

- Include deterministic JUnit 5 tests with examples of expected behavior.
- Add executable algebraic-law tests for abstractions that claim laws; distinguish observational equivalence from identity of Java function objects.
- Cover failure and boundary cases, null contracts, immutability, allocation and stack safety where relevant.
- Follow the [nullness policy](docs/nullness.md): JSpecify `@NullMarked` per package, explicit `@Nullable` type uses for intentionally nullable generic payloads, and Lombok `@NonNull` for required structural references (callbacks, combiners, suppliers, and container inputs). Do not put blanket runtime null checks on payload records or generic factories.
- Preserve explicit runtime validation of null **structural callback results**, without triggering deferred functions prematurely. The Maven build now enforces JSpecify contracts via NullAway (#41); run `./scripts/verify-nullness.sh` to prove that intentional nullable payloads compile and illegal null uses fail. Keep generic payload type parameters bounded by `@Nullable Object` so explicit nullable specializations remain valid.
- For `F3`–`F10` update `scripts/generate_functions.py` and regenerate the source files; never hand-edit generated Java.
- Do not introduce a general FP hierarchy or require clients to wrap their applications in Hercules types to use one abstraction.

## Pull requests

Complete the [PR template](.github/PULL_REQUEST_TEMPLATE.md), link an issue, and wait for CI. Use squash merging. Never commit secrets or unrelated private data.

Security issues should be reported privately rather than in a public issue; see [SECURITY.md](SECURITY.md).
