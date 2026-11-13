# These<L,R>: inclusive outcomes

`These<L,R>` represents the **inclusive-or** of two types: left only,
right only, or both together. It differs from `Either<L,R>`, which stores
exactly one side, and from `Those<L,R,E>`, which can additionally
record a typed explanation for neither side being present.

## Constructors and variants

```java
These<String, Integer> onlyLeft  = These.left("no result");          // This
These<String, Integer> onlyRight = These.right(42);                 // That
These<String, Integer> both      = These.both("warning", 42);       // Both
```

`These` is a Java 17 sealed interface with nested record variants
`This(L)`, `That(R)` and `Both(L,R)`. Factories are named `left`,
`right`, and `both`, matching the familiar `Either` vocabulary.
The records hold their payloads by reference and do **not** deep-copy mutable
objects. Prefer immutable payload values when you need the full result to
be immutable.

## Use case: preserving useful partial results

```java
These<List<String>, List<Integer>> partial =
        These.both(List.of("Skipped invalid row 2"), List.of(1, 3));

String report = partial.fold(
        diagnostics -> "Only diagnostics: " + diagnostics.size(),
        records -> "Only records: " + records.size(),
        (diagnostics, records) -> "Records: " + records.size()
                + ", diagnostics: " + diagnostics.size());
// "Records: 2, diagnostics: 1"
```

This records both **valid results** and **diagnostics** without discarding
one to return the other. It does not, by itself, encode whether a whole
batch, import, or job completed successfully; keep that status separate.

## Operations

| Method | This(L) | That(R) | Both(L,R) |
| --- | --- | --- | --- |
| `map(f)` | This(L) | That(f(R)) | Both(L,f(R)) |
| `mapLeft(f)` | This(f(L)) | That(R) | Both(f(L),R) |
| `bimap(f,g)` | This(f(L)) | That(g(R)) | Both(f(L),g(R)) |
| `swap()` | That(L) | This(R) | Both(R,L) |
| `fold(l,r,b)` | l(L) | r(R) | b(L,R) |

All methods are **strict**. Callbacks are checked for null immediately;
only the callback(s) for the present values execute. User exceptions
propagate unchanged. At least one side is always present.

## Lossless conversion to Those

`toThose()` preserves all three variants while adding the possibility
of an absence explanation for future values:

```java
These<String, Integer> observations = These.both("sensor A", 42);
Those<String, Integer, String> evidence = observations.toThose();
// Those.Both("sensor A", 42)
```

The generic third type parameter `E` specifies the future explanation
type; the conversion never invents a `Neither(E)`. It commutes with
`map`, `mapLeft`, `bimap`, and `swap`, verified using Instancio.
Converting a general `Those` back to `These` is not inherently
lossless, because `Neither(E)` carries an explanation that `These`
cannot represent. The existing `Those.toEither()` remains a lossless
equivalence with `Either<E, These<L,R>>`.

## Right-biased flatMap with explicit left accumulation

Unlike `Either`, `These` may already contain a left value when it
proceeds to a right-biased dependent computation. Hercules therefore
requires a **caller-supplied associative combiner** for left values:

```java
BinaryOperator<List<String>> append = (a, b) ->
        Stream.concat(a.stream(), b.stream()).toList();

These<List<String>, Integer> initial =
        These.both(List.of("First warning"), 21);

var result = initial.flatMap(
        number -> These.both(List.of("Second warning"), number * 2),
        append);
// Both([First warning, Second warning], 42)
```

Detailed semantics (where `f` is the binder):

| Original | `f(right)` | Result |
| --- | --- | --- |
| This(l) | not evaluated | This(l) |
| That(r) | any variant | unchanged result of f(r) |
| Both(l,r) | This(l2) | This(combine(l,l2)) |
| Both(l,r) | That(r2) | Both(l,r2) |
| Both(l,r) | Both(l2,r2) | Both(combine(l,l2),r2) |

**Order matters:** the existing left value is combined **before** the
new one. The combiner must be associative for the Monad associativity
law to hold. Commutativity is not required. Hercules does not invent a
global `Semigroup` interface, identity or default combining policy.

## Nullness and limitations

The Hercules package is JSpecify `@NullMarked`. For intentional null
payloads, use explicitly nullable type arguments:

```java
These<@Nullable String, @Nullable Integer> example = These.both(null, null);
```

This retains the `Both` variant; null payloads are **not** interpreted
as absence. Null callbacks and null `flatMap` results are rejected.
No `toEither` operation has been added because handling `Both`
would otherwise silently discard information.

`zip`, `mapN`, `Alternative`, and general error accumulation remain
part of #9 / #5 discussions, not obligations of this type. The separate
`Those<L,R,E>` supports informative `Neither(E)`.

## Verification

`TheseLawsTest` exercises every variant using **Instancio** `@Given`
inputs and repeated property-style checks. It verifies Functor laws,
bimap coherence, swapping twice, the conditional Monad laws with an
associative left combiner, accumulation order, null handling,
exception propagation, and strict evaluation.

```bash
./mvnw -B -ntp verify
```

Java 17 is the baseline; the CI matrix also verifies Java 21 and 25.
Related work: umbrella #5, issue #24 (These), issue #9 (Applicative/Validation).
