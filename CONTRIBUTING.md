# Contributing to Hercules

Thank you for helping build **Hercules**, an incremental functional programming library for Java 17+.

## Before coding

- Look through the [public issues](https://github.com/black-belt-badger/Hercules/issues) and [project board](https://github.com/orgs/black-belt-badger/projects/11/views/2).
- Open or link an issue before changing a public API; discuss semantics and algebraic laws.
- Aim for one focused, reviewable pull request per issue.
- Prefer standard JDK interfaces, independent utilities, and explicit immutable inputs/outputs.
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
- Use JSpecify non-null-by-default conventions. Java annotations alone do not constitute static nullness verification unless an analyzer is configured.
- Do not introduce a general FP hierarchy or require clients to wrap their applications in Hercules types to use one abstraction.

## Pull requests

Complete the [PR template](.github/PULL_REQUEST_TEMPLATE.md), link an issue, and wait for CI. Use squash merging. Never commit secrets or unrelated private data.

Security issues should be reported privately rather than in a public issue; see [SECURITY.md](SECURITY.md).
