## Summary

Explain the public API or maintenance problem addressed and the chosen design.

## Related issue

<!-- Example: Closes #14. -->

## Verification

- [ ] `./mvnw -B -ntp clean verify` passes locally using JDK 17 (or a clear explanation is provided).
- [ ] Appropriate deterministic tests, including algebraic laws where applicable, are added or updated.
- [ ] Public Java 17 API compatibility, JSpecify contracts and dependency impact are reviewed.
- [ ] Pure computations remain free of hidden logging, I/O and shared mutable state.
- [ ] No secrets or private application details appear in code, fixtures, logs, or documentation.

## Notes for reviewers

Call out behavioral compatibility, stack safety, immutability, error semantics, performance and public API trade-offs.
