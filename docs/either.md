# Either<L, R>: an exclusive choice

`Either<L, R>` represents exactly one of `Left(L)` or `Right(R)`.
It is a Java 17 sealed interface with immutable nested records and no
dependency on a functional-programming framework.

## Constructors and right-biased composition

```java
Either<String, Integer> parsePositive(String input) {
    final int number;
    try {
        number = Integer.parseInt(input);
    } catch (NumberFormatException ex) {
        return Either.left("Not a number: " + input);
    }

    return number > 0
            ? Either.right(number)
            : Either.left("Must be positive");
}

Either<String, String> result = parsePositive("21")
        .flatMap(n -> Either.right(n * 2))
        .map(n -> "Answer: " + n);

String message = result.fold(
        error -> "Invalid input: " + error,
        Function.identity()
); // "Answer: 42"
```

`map` transforms only `Right`; `flatMap` chains dependent computations
on `Right` and propagates `Left` unchanged. They are **strict**: unlike
Hercules' lazy `F3`–`F10` function adapters, the callback is executed as
soon as the operation is called on a `Right`. User exceptions are not caught.

## Lossless conversion to These

`toThese()` preserves the left/right choice without inventing a Both case:

```java
Either<String, Integer> source = Either.left("warning");
These<String, Integer> evidence = source.toThese();
// These.This("warning"), not an error or a Neither case
```

This is a **one-way widening**: converting an arbitrary `These`
back to `Either` would require a policy for `Both` and could discard
information. Mapping and swapping either before or after this conversion
produce equivalent values; Instancio law tests check the coherence.

## Other operations

```java
Either<String, Integer> missing = Either.left("unavailable");
Either<String, Integer> present = Either.right(7);

int defaulted = missing.getOrElse(0);       // 0
var recovered = missing.recover(String::length); // Right(11)
var optional = present.toOptional();        // Optional[7]

var flipped = present.swap();                // Left(7)
var mapped = present.mapLeft(String::length); // Still Right(7)
var both = present.bimap(String::length, n -> n * 10); // Right(70)
```

- `fold(onLeft, onRight)` is the fundamental total elimination operation.
  Both callbacks must be non-null, even when only one branch is present.
- `mapLeft` transforms the left payload; `bimap` transforms either side
  using the appropriate callback; `swap` exchanges the two variants.
- `getOrElse(fallback)` returns the right value or a supplied fallback.
- `recover(f)` turns `Left(L)` into `Right(f(L))`, retaining any right value.
- `toOptional()` **deliberately discards left information**. A `Right(null)`
  with an explicitly nullable payload also becomes an empty optional.

## Filtering and why rejection needs a left value

`filterOrElse(predicate, onFailure)` is the one convenience adopted
from the #23 review. It keeps the original `Left(L)` unchanged by
value and does not invoke either callback on that branch. On
`Right(R)`, the predicate runs **once**: a pass keeps the right
payload; a failure evaluates `onFailure(R)` **once**, constructing
`Left(L)`.

```java
Either<String, Integer> quantity = Either.right(-2);

Either<String, Integer> checked = quantity.filterOrElse(
        n -> n > 0,
        n -> "quantity must be positive: " + n);
// Left("quantity must be positive: -2")

// An existing Left stays unchanged without evaluating either callback:
Either<String, Integer> missing = Either.left("not supplied");
Either<String, Integer> same = missing.filterOrElse(
        n -> n > 0, n -> "not positive");
// Left("not supplied")
```

The rejected value becomes a **caller-provided** left payload, never an
invented empty or exception. Both callback references are checked
eagerly (including for an existing `Left`), even though neither is
executed on that branch. A passing `Right` never evaluates
`onFailure`. Callback exceptions propagate unchanged; there is no
implicit exception-to-`Left` conversion. For an explicitly nullable
`Right`, predicates may receive null; an explicitly nullable `Left`
can also be returned by the rejection function.

The method is defined in terms of `fold` and preserves existing
Functor/Monad semantics: it does **not** introduce an alternative
interpretation of `Left`.

## #23 convenience-method decisions

Hercules deliberately implements a small API rather than mirroring
every method of a full functional framework. The following decisions
were reviewed against [Vavr's Either API](https://www.javadoc.io/static/io.vavr/vavr/0.11.0/io/vavr/control/Either.html),
Java's standard `Optional` and this library's explicit `fold`.

| Candidate | Decision | Reason |
| --- | --- | --- |
| `filterOrElse(Predicate<R>, Function<R,L>)` | **Implement** | Useful right-validation pipeline; the caller defines the meaningful Left on rejection, preserving the existing Left and its payload |
| `filter(Predicate<R>)` | **Drop** | It cannot produce `Either<L,R>` when a Right is rejected without making up an L. Vavr uses `Option<Either<L,R>>`, which cannot explain why rejection occurred; this differs from our typed-error objective |
| `get()` | **Drop** | Partial extraction of R would throw on Left; no justified generic exception policy. Use `fold`, `getOrElse`, `recover`, or a checked variant pattern |
| `getLeft()` | **Drop** | Symmetric partial extraction with the same problem for Right. Use `fold` or an explicit `Either.Left` pattern |
| `peek(Consumer<R>)` | **Defer** | Useful for metrics/logging but introduces observable side effects into a fluent chain. Use explicit observation at an effect boundary and avoid implying that a pure mapping is effectful |
| `peekLeft(Consumer<L>)` | **Defer** | Same tradeoff; neither a Left nor a Right is necessarily an exception or logging event |

Unlike Vavr, Hercules does not need a `Value` or collection-style
interface to express filtering. The presence of these names in another
library is not itself justification for adding them here. Any future
`peek` addition must clearly specify strict callback validation,
active-branch execution and unchanged return value, plus propagate
user exceptions without reinterpretation.

No new runtime dependencies, higher-kinded interfaces or generic
exception protocols are introduced. These are #23's
**implement/defer/drop** decisions. The older outcome umbrella #5
is already closed.

## Nullability, value semantics and safety

`@NullMarked` applies to the package. Use `@Nullable` for intentional
nullable type arguments:

```java
Either<@Nullable String, @Nullable Integer> absent = Either.left(null);
```

The records preserve the chosen variant, including a null payload when
explicitly permitted. We never turn a null payload into a different variant.
Callback objects are rejected immediately if null, including callbacks for
the unused branch. A `flatMap` callback returning a null `Either` throws.
No exceptions are silently converted into `Left`.

Record equality is structural, so the Functor/Monad laws are tested by value.
The usual laws apply to **pure** supplied functions; Java cannot enforce
purity. `Either` is right-biased but a `Left` does **not** have to mean an
exception or error. Error accumulation and `Alternative` choice require
explicit policies and are not part of this API.

## Instancio law tests

`EitherLawsTest` uses Instancio's JUnit 5 extension and `@Given`
randomly generated values, including repeated trials for both variants.
A failing run prints a reproducible seed. It verifies:

- Functor identity and composition
- Monad left/right identity and associativity
- `bimap` and `map` / `mapLeft` coherence
- `swap().swap()` involution and branch preservation
- Callback order and evaluation count; exceptions and null handling
- Explicit information loss in `toOptional`

`EitherFilterLawsTest` adds Instancio-backed branch-exhaustive checks
for `filterOrElse`: predicate/failure callback counts, preservation
of existing Left or passing Right, typed rejection reasons, eager
null-reference checks, callback exceptions and explicitly nullable
payloads. `EitherFilterPublicApiTest` checks the same API from a
separate consumer package.

We use Instancio **5.x** because Hercules currently uses JUnit 5.
Instancio is test-scope only; it is not a runtime Hercules dependency.

```bash
./mvnw -B -ntp verify
```

See parent issue #5 and implementation issue #21.
