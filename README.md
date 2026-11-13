# Hercules

**Hercules** is a small, public functional programming library for modern Java.

> **Working mission — open for discussion:** Bring expressive, lawful functional abstractions to ordinary Java applications, one capability at a time. Using Hercules should never require an application to adopt a whole framework, rewrite its architecture, or become dependent on Hercules throughout its codebase.

## Design principles

- **Opt in, don't buy in.** Each abstraction should be useful independently and interoperate with ordinary Java code.
- **Standard Java at the boundary.** Prefer JDK `Function`, `BiFunction`, `List`, `Map`, `Optional`, `CompletionStage`, `Flow`, records and sealed types where they suffice.
- **Audited, deliberate dependencies.** JSpecify defines static nullness contracts; Lombok generates structural checks when Hercules is built, not when clients compile. Guava is retained for the planned morphism `Tree`/`Layer` API (#14), which uses `ImmutableList`, although current production sources have no Guava imports. See the [dependency policy](docs/dependencies.md).
- **Prefer algebraic laws to ceremony.** Add examples, tests and clear semantics. Do not manufacture a full typeclass hierarchy or duplicate JDK reduction APIs without a practical reason.
- **Functional, not dogmatic.** Explicit state, immutable data and composition are goals; existing Java programs remain first-class citizens.
- **Pure core, interpreted effects.** Describe logging and other effects as data; perform I/O only at explicit interpretation boundaries.

## Implemented

- **Higher-arity functions**: `F3`–`F10` with `apply`, `partial`, `map`, Reader-style `flatMap`, `compose1`–`composeN`, `curried`, and `uncurry`. See the [usage guide](docs/functions.md). All arities interoperate with JDK `Function` and `BiFunction` after partial application.
- **Either**: right-biased `Either<L,R>` with sealed `Left` and `Right` records, `map`, `mapLeft`, `flatMap`, `bimap`, `fold`, `swap`, recovery, `filterOrElse` with caller-defined rejection reasons, optional conversion and lossless widening to `These`. See the [Either guide](docs/either.md).
- **These**: inclusive-or `These<L,R>` with `This`, `That` and `Both`, preserving both data and diagnostics. Includes right-biased `flatMap` with an explicit associative left combiner. See the [These guide](docs/these.md).
- **Those**: four-way `Those<L,R,E>` with `This`, `That`, `Both`, and typed `Neither(E)` for informative absence. Maps all payloads and converts losslessly to `Either<E, These<A,B>>`. `ThoseShape` provides payload-free information ordering, while `ThoseAggregation` explicitly combines all three payload channels without discarding absence provenance. See the [Those guide](docs/those.md).
- **Explicit choice**: `EitherChoice<L>` supports leftmost-success fallback and ordered accumulation of failed alternatives using a caller-provided identity and combiner. This is a concrete choice monoid, not a universal higher-kinded `Alternative`. See the [choice guide](docs/choice.md).
- **Accumulating validation**: `Validation<E,A>` with `Valid`/`Invalid` and `ValidationApplicative<E>` for independent checks, lawful `pure`/`ap`/`map2`, ordered `sequence`/`traverse` for many checks, arity-aware `map3`–`map10` using `F3`–`F10`, and `CompletionStage` composition (`map2Stages`/`sequenceStages`) for independently supplied asynchronous checks. Normal validation failures accumulate; exceptional completion remains exceptional. See the [Validation guide](docs/validation.md).
- **Persistent difference lists**: `DList<A>` with constant-time `cons`, `snoc` and `append`, immutable source snapshots and stack-safe iterative materialization into an unmodifiable JDK `List`. Includes a manual allocation/timing benchmark and diagnostic-accumulation example. See the [DList guide](docs/dlist.md).

## Areas under consideration

`Reader`, `Writer`, and `State`; trampolines; general `Alternative`, broader Applicative composition, Monad and Comonad; functional lenses; persistent measured finger trees; Free Monads; type algebra, bounded and modular types. Generic choice/aggregation semantics for `Those` remain an explicit research topic.

All remaining items are **proposals** until implemented and validated. Inclusion in this list does not imply a committed public API.

## Public backlog

Track proposed abstractions, experiments and implementation work on the [Hercules Project Board](https://github.com/orgs/black-belt-badger/projects/11/views/2) and in [public issues](https://github.com/black-belt-badger/hercules/issues). The library remains opt-in: adopting one abstraction must not require adopting the rest.

## Java and Maven

**Java 17 is the minimum supported language/runtime level.** Records and sealed types are available, and we verify the library against Java 17, 21 and 25.

The Maven coordinates reserved for development are:

```text
io.github.blackbeltbadger:hercules:0.1.0-SNAPSHOT
```

**Java API namespace:** `bbb.hercules` (imports such as `bbb.hercules.Either` and `bbb.hercules.Validation`). The shorter BBB Java package name is intentionally independent of the unchanged Maven groupId. This pre-release package migration requires consumers of earlier snapshots to update their Java imports; it changes binary names and does not ship compatibility wrappers.

The project is a **thin library JAR**, not an executable application. Guava and JSpecify are compile-scope dependencies for Maven consumers; Lombok is provided/annotation-processing only. Guava is intentionally reserved for the public `ImmutableList` morphism model (#14), not used by the current production code. JUnit 5 and **Instancio** are test-only. The [dependency policy](docs/dependencies.md) documents scopes and a standalone consumer smoke test.

```bash
./mvnw -B -ntp verify
```

JSpecify `@NullMarked` is enabled for the initial package. **NullAway checks the JSpecify contracts during Maven compilation and CI** (#41), while Lombok `@NonNull` emits separate fail-fast runtime checks for required callbacks, combiners and container arguments. Explicitly nullable generic payloads remain supported; structural callback results are checked when evaluated. See the [nullness policy](docs/nullness.md). Run `./scripts/verify-nullness.sh` to compile positive and intentionally invalid consumer fixtures. Lombok is configured during the library build; downstream consumers do not need its annotation processor.

## Status

`F3`–`F10`, `Either`, `These`, `Those`, `EitherChoice`, accumulating `Validation`, and persistent `DList` implemented. No published release yet.

## License

Hercules is released under the [BSD-3-Clause License](LICENSE).
