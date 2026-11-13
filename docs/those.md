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

## Shape only: a four-element information lattice

`ThoseShape` deliberately forgets all payloads and tracks only which
evidence kinds are present. It has four values:

```text
               BOTH
             /      \
           LEFT     RIGHT
             \      /
              NEITHER
```

`join` is set-union of present evidence kinds; `meet` is their
intersection. Both are associative, commutative and idempotent;
absorption holds. `NEITHER` is the identity for **shape join only**,
not an identity for combining `Those.Neither(e)` payloads.

```java
ThoseShape shape = ThoseShape.of(Those.<String, Long, String>neither(
        "provider A timed out"));
assert shape == ThoseShape.NEITHER;
assert shape.join(ThoseShape.RIGHT) == ThoseShape.RIGHT;
```

Shape projection cannot distinguish `Neither("A timed out")` from
`Neither("B disconnected")`: both become `ThoseShape.NEITHER`.
This is intentional, explicit information loss, suitable for
presence-only queries or planning. It is not a payload-bearing Belnap
algebra; the analogy is limited to the information order.

## Aggregating all three payload channels without discarding provenance

`ThoseAggregation<L,R,E>` is an **explicit, associative** semigroup
policy requiring a `BinaryOperator<L>`, a `BinaryOperator<R>`, and a
`BinaryOperator<E>`. The result does **not** attempt to squeeze
provenance back into the four variants. It uses a seven-state type:

```text
These<E, These<L,R>>

E only                  These.This(e)
L only                  These.That(These.This(l))
R only                  These.That(These.That(r))
L and R                 These.That(These.Both(l,r))
E and L                 These.Both(e,These.This(l))
E and R                 These.Both(e,These.That(r))
E and L and R           These.Both(e,These.Both(l,r))
```

For example, independently downloaded sources may both produce evidence
and fail to respond:

```java
ThoseAggregation<List<String>, List<String>, List<String>> aggregation =
        new ThoseAggregation<>(
                (a, b) -> Stream.concat(a.stream(), b.stream()).toList(),
                (a, b) -> Stream.concat(a.stream(), b.stream()).toList(),
                (a, b) -> Stream.concat(a.stream(), b.stream()).toList());

Those<List<String>, List<String>, List<String>> sourceA =
        Those.neither(List.of("source A timed out"));
Those<List<String>, List<String>, List<String>> sourceB =
        Those.right(List.of("BTC price from source B"));

These<List<String>, These<List<String>, List<String>>> result =
        aggregation.combine(sourceA, sourceB);

// These.Both(["source A timed out"],
//            These.That(["BTC price from source B"]))
```

This preserves the timeout explanation alongside the successful
observation. With **associative, non-mutating** payload combiners, the
aggregation is associative because each presence channel is combined
independently in encounter order. It need not be commutative:
concatenating source observations may yield different order under
reversal. The result has **no generic identity**; `combineAll` therefore
requires a *nonempty, finite* iterable. Single-element `combineAll`
is equivalent to `lift`. No work is scheduled; all inputs are visited
once, and iterator/combiner exceptions propagate.

Methods:

- `lift(Those)`: exact embedding of one original value into the richer
  representation
- `combine(Those,Those)`: two originals, producing the richer result
- `combine(These,These)`: **closed** aggregation of intermediate
  seven-state results
- `combineAll(Iterable<Those>)`: ordered aggregation of nonempty
  finite input, rejecting an empty iterable with `NoSuchElementException`

The supplied binary operators determine how individual payload details
are combined. If retaining every explanation matters, choose an
information-preserving `E` policy such as immutable-list concatenation.
A lossy `combineExplanation` (for example, "keep latest") does not
magically become lossless just because the representation has an E
channel. Payload values may be explicitly nullable, subject to their
type-use contracts.

## What we deliberately do not add to Those

Consider `Neither("A timed out")` and `That(dataFromB)`. A
four-state `Those` result cannot carry both the explanation and data.
This rules out a **universally provenance-preserving** closed
`Those.combine`. There are domain-specific alternatives, but they
must be explicit:

| Candidate | Decision | Law or information-loss consequence |
| --- | --- | --- |
| Shape-only `join`/`meet` | **Implement**: `ThoseShape` | Bounded distributive lattice on presence bits, but all payloads and E are discarded |
| Provenance-aware aggregation | **Implement**: `ThoseAggregation` | Associative when L/R/E operators are associative; combines in the richer seven-state result, retaining E separately |
| "Discard E when evidence appears" merge | **Defer** | May be associative under a specified semigroup policy, but is intentionally *lossy*; must not be called universally lossless |
| Treat every `Neither(e)` as an identity | **Drop** | Two arbitrary distinct explanations cannot both be neutral under an operation that also retains both reasons; `Neither(e1) + Neither(e2)` needs an explicit E policy |
| Generic right-biased `flatMap` or `zip` on `Those` | **Drop for core** | When later evidence appears, an earlier E has nowhere to go; dependent fail-fast choices require a domain-specific interpretation |
| `Alternative` / `empty` | **Drop for core** | There is no canonical E payload representing empty or a lawful universal choice among all four variants |
| `toOptional` / partial projection | **Defer** | Cannot recover E or both evidence slots from `Optional<R>`; callers should explicitly `fold` if intentional information loss is acceptable |
| `Either<E,These<L,R>>` | **Retain exact four-state conversion** | `toEither` / `fromEither` is lossless for the original four constructors, but cannot express E together with evidence |
| A richer record instead of nested `These` | **Defer** | Possible if real users need a named domain representation; no extra abstraction needed for seven present cases |

A counterexample to treating absence as an ordinary identity is the
attempt to make *both* `Neither("timeout A")` and
`Neither("timeout B")` empty: retaining both independent reasons
requires a merger of E, while an arbitrary identity policy would erase
one. Similarly, `Optional<These<L,R>>` maps distinct absence
explanations to the same empty value. Neither conversion is reversible.

The original `Those<L,R,E>` stays intentionally free of an
unqualified `flatMap`, `combine`, `zip` or `Alternative` instance.
The policy lives in named, independently adoptable types rather than
implicitly assigning semantics to all callers.

## Validation

`ThoseLawsTest` uses the Instancio JUnit extension (`@Given`) for
repeatable randomized inputs across **all four variants**, including
identity and composition for mapping, `bimap`/`trimap` coherence,
swap involution, conversion in both directions, fold exhaustiveness,
callback counts, nullness and exception propagation.

`ThoseShapeLawsTest` checks lattice associativity, commutativity,
idempotence, absorption and all four shape projections.
`ThoseAggregationLawsTest` uses Instancio values to check associativity
across all **seven rich cases** and all four original cases, encounter
order, preservation of timeout provenance, nonempty aggregation, strict
runtime contracts and explicitly nullable payloads. An independent-package
`ThoseAggregationPublicApiTest` exercises a practical market-data example.

```bash
./mvnw -B -ntp verify
```

Java 17 is the baseline. CI runs Java 17, 21, and 25.
Core implementation: #26. Aggregation and provenance research/implementation: #27.
Deferred `Either` conveniences: #23. The former umbrella #5 is already closed.
