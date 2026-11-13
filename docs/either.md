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

We use Instancio **5.x** because Hercules currently uses JUnit 5.
Instancio is test-scope only; it is not a runtime Hercules dependency.

```bash
./mvnw -B -ntp verify
```

See parent issue #5 and implementation issue #21.
