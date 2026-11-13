# Hercules release process

No Hercules artifact has yet been publicly released or published to a package registry.

## Before the first release

1. Decide the stable API surface, supported Java versions and precise dependency policy.
2. Review license obligations, public examples and documentation.
3. Decide versioning and artifact publication (for example Maven Central or GitHub Packages), including credentials, signing and repository metadata.
4. Verify the intended release commit on Java 17, 21 and 25; run `./mvnw clean verify` locally under Java 17.
5. Update `CHANGELOG.md` and create accurate release notes.
6. Create a tag or GitHub release **only after explicit maintainer approval**.

No release tag, package publication, signing credentials or guaranteed API compatibility is implied by a snapshot version.
