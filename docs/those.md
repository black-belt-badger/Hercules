# Those<L,R,E>: evidence and informative absence

`Those<L,R,E>` extends the semantics of Hercules `These<L,R>` with a
**fourth case**, `Neither(E)`. Its type parameters are:

- `L`: left evidence or observation
- `R`: right evidence or observation
- `E`: the explanation why **neither** observation is available

Neither does not automatically mean an exception, error, false, or monoidal
identity. Like `These`, the structure is independent of a business-domain
interpretation of its payloads.

## Four variants

```java
Those<String, Integer, String> a = Those.left("manual observation");      // This
Those<String, Integer, String> b = Those.right(42);                       // That
Those<String, Integer, String> both = Those.both("manual observation", 42); // Both
Those<String, Integer, String> none = Those.neither("not observed yet");  // Neither
```

All four are nested immutable Java records in a sealed Java 17 interface:
`This(L)`, `That(R)`, `Both(L,R)`, and `Neither(E)`. Like other
records, they are shallowly immutable; referenced mutable payloads are
not deep-copied.

## Consistent with These

| Method | This(L) | That(R) | Both(L,R) | Neither(E) |
| --- | --- | --- | --- | --- |
| `map(f)` | This(L) | That(f(R)) | Both(L,f(R)) | Neither(E) |
| `mapLeft(f)` | This(f(L)) | That(R) | Both(f(L),R) | Neither(E) |
| `mapNeither(f)` | This(L) | That(R) | Both(L,R) | Neither(f(E)) |
| `bimap(f,g)` | This(f(L)) | That(g(R)) | Both(f(L),g(R)) | Neither(E) |
| `trimap(f,g,h)` | This(f(L)) | That(g(R)) | Both(f(L),g(R)) | Neither(h(E)) |
| `swap()` | That(L) | This(R) | Both(R,L) | Neither(E) |

`map` is right-biased for naming consistency with `Either` and
`These`, but that does **not** classify `This` or `Neither` as errors.
`fold(onLeft, onRight, onBoth, onNeither)` is a total eliminator that
handles exactly one constructor:

```java
String report = Those.<String, Integer, String>both("sensor A", 42).fold(
        left -> "Only A: " + left,
        right -> "Only B: " + right,
        (left, right) -> "Both: " + left + " and " + right,
        explanation -> "Neither: " + explanation);
```

Operations are **strict**, just like `Either` and `These`. Every
callback reference is checked for null immediately, even if its case
will not run. Only active callbacks execute, and user exceptions propagate
unchanged. Nullable payloads are supported with explicit JSpecify
`@Nullable` type arguments: a null payload is **never interpreted as
a missing variant**.

## Lossless equivalence to our existing types

The constructors form the following structural equivalence:

```text
Those<L,R,E>  ≅  Either<E, These<L,R>>

Neither(e)    ↔ Left(e)
This(a)       ↔ Right(This(a))
That(b)       ↔ Right(That(b))
Both(a,b)     ↔ Right(Both(a,b))
```

Use `toEither()` and the static `Those.fromEither(nested)`:

```java
Those<String, Integer, String> observed =
        Those.both("sensor A", 42);

Either<String, These<String, Integer>> nested = observed.toEither();
Those<String, Integer, String> restored = Those.fromEither(nested);
// restored.equals(observed)
```

No information is lost in either direction, including for explicitly
nullable payloads and the `Neither` explanation. You can also compose
`Either` and `These` directly if they express your domain more clearly;
you do not need to adopt a dedicated `Those` type.

## Composing the information-preserving embeddings

There is a consistent widening chain:

```java
Either<String, Integer> source = Either.left("observation A");
Those<String, Integer, String> evidence = source.toThese().toThose();
// Those.This("observation A"), NOT Those.Neither("observation A")
```

An `Either.Left` becomes left evidence in `These` and `Those`.
It does **not** become `Neither(E)`: these cases have different
meanings. Both widening steps preserve values and variants without
inventing missing data, and their mapping/swapping coherence laws are
checked with Instancio.

## Why there is deliberately no combine or flatMap

Consider `Neither("A timed out")` and `That(dataFromB)`.

One valid policy says the explanation was only about *total absence*;
it may become obsolete when B supplies evidence. Another says the timeout
is *provenance* and should survive alongside B's observation.

The four constructors cannot represent both arbitrary `E` and `B`
simultaneously. Providing a generic `combine` or `flatMap` would
therefore require a **domain-specific information-loss policy**,
additional payload structure, or a different type. It would be
incorrect to assume `Neither(E)` is an identity for aggregation.

We intentionally add **no** `Alternative`, `empty`, `flatMap`,
`combine`, `zip`, or `toOptional` method in this subtask.
Aggregation and optional/lossy conversions remain subjects for
issues #5, #9 and #27. For applications needing E information alongside
evidence, consider a richer record or `These<E, These<L,R>>`,
rather than discarding E.

## Validation

`ThoseLawsTest` uses the Instancio JUnit extension (`@Given`) for
repeatable randomized inputs across **all four variants**, including
identity and composition for mapping, `bimap`/`trimap` coherence,
swap involution, conversion in both directions, fold exhaustiveness,
callback counts, nullness and exception propagation.

```bash
./mvnw -B -ntp verify
```

Java 17 is the baseline. CI runs Java 17, 21, and 25.
Parent: #5. Implementation: #26. Deferred aggregation: #27. Deferred `Either` conveniences: #23.
