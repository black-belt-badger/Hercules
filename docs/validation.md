# Validation<E,A> — independent, accumulating checks

`Validation<E,A>` models an **invalid** result containing an error payload
`E`, or a **valid** result containing a value `A`. The public type is a
Java 17 sealed interface with nested immutable record variants
`Invalid(E)` and `Valid(A)`.

Hercules deliberately separates this type from `Either<E,A>`:

- `Either.flatMap` is **fail-fast dependent sequencing**.
- `ValidationApplicative.map2` and `ap` combine **independent validations**
  and accumulate errors in all unsuccessful inputs.
- `These<E,A>` can represent a value **alongside** diagnostics. A
  `Validation<E,A>` cannot contain both: an `Invalid` prevents a
  complete valid result, even if some individual checks succeeded.
- Neither an error combiner nor a universal error payload is invented.

## Factories, mappings and elimination

```java
Validation<List<String>, Integer> good = Validation.valid(42);
Validation<List<String>, Integer> bad =
        Validation.invalid(List.of("invalid age"));

var mapped = good.map(x -> x + 1);           // Valid(43)
var messages = bad.mapLeft(List::copyOf);     // Invalid(["invalid age"])

String description = mapped.fold(
        errors -> "Invalid: " + errors,
        value -> "Valid: " + value);
```

`map` transforms only a `Valid`; `mapLeft` transforms only an
`Invalid`. `bimap` applies the matching transformation, and `fold`
is a total eliminator. All callback references are checked for null,
including those not selected by the current variant. Callbacks execute
synchronously for their active case. Exceptions from callers propagate
unchanged.

`toEither()` and static `Validation.fromEither(...)` preserve their
variant and payload in both directions, including deliberately nullable
payloads. This is an **isomorphism of data representation**, not a claim
that `Either`'s fail-fast Monad and `Validation`'s accumulating
Applicative have the same composition behavior.

## Explicit error combination policy

```java
ValidationApplicative<List<String>> app =
        new ValidationApplicative<>((first, second) ->
                Stream.concat(first.stream(), second.stream()).toList());
```

`combineErrors` must be **associative**, because the order of
parenthesization should not change the final error payload. It need
not be commutative: error order is left to right. No identity is
required, so a semigroup is enough; Hercules does not introduce a
general `Semigroup` or higher-kinded typeclass framework.

The combiner is supplied explicitly, like the policy in `EitherChoice`,
but the semantics are different: `EitherChoice` prefers a first
success; `ValidationApplicative` accumulates **all failures** of
independent checks.

## Example 1: independent form fields

```java
record Registration(String username, int age) {}

Validation<List<String>, String> name =
        Validation.invalid(List.of("username must not be blank"));
Validation<List<String>, Integer> age =
        Validation.invalid(List.of("age must be at least 18"));

Validation<List<String>, Registration> result =
        app.map2(name, age, Registration::new);
// Invalid(["username must not be blank", "age must be at least 18"])
```

All arguments to `map2` are **already evaluated**. It combines as
follows:

| First | Second | Result |
| --- | --- | --- |
| Valid(a) | Valid(b) | Valid(mapper(a,b)) |
| Invalid(e1) | Valid(b) | Invalid(e1) |
| Valid(a) | Invalid(e2) | Invalid(e2) |
| Invalid(e1) | Invalid(e2) | Invalid(combineErrors(e1,e2)) |

The mapper callback is validated immediately and invoked **once only**
when both inputs are `Valid`. The error combiner is invoked **once only**
when both inputs are `Invalid`. Null inputs or callbacks are rejected.

## Example 2: validated, curried constructors

The same policy supports function application, with failures in the
**function validation before** failures in the value validation.

```java
record Item(String sku, int quantity) {}

var create = app.pure((Function<String, Function<Integer, Item>>)
        sku -> quantity -> new Item(sku, quantity));

Validation<List<String>, String> sku =
        Validation.invalid(List.of("missing SKU"));
Validation<List<String>, Integer> quantity =
        Validation.invalid(List.of("invalid quantity"));

Validation<List<String>, Item> item =
        app.ap(app.ap(create, sku), quantity);
// Invalid(["missing SKU", "invalid quantity"])
```

`pure(x)` creates `Valid(x)`. With a fixed associative error combiner,
`pure` and `ap` satisfy the Applicative identity, composition,
homomorphism and interchange laws. `map2` is convenient for combining
two independent results without writing a curried function.

The operations themselves are eager/strict; they **do not** automatically
schedule checks in parallel, defer side effects, catch exceptions, or
cancel work. They only combine their completed `Validation` arguments.
For parallel asynchronous checks and cancellation, see the later
`CompletionStage` work in #9.

## Nullness and mutability

The package uses JSpecify `@NullMarked`. If an application explicitly
intends nullable validation payloads, it can write:

```java
Validation<@Nullable String, @Nullable Integer> result = Validation.valid(null);
```

The variant remains `Valid`, not `Invalid` or an absent state.
Non-null callback references and combiners are validated at runtime.
Record constructors currently follow the project's existing type-use
nullness conventions; broad runtime checks on payloads are tracked in
#35 and must not silently change nullable use cases.

These are **shallowly immutable** records; mutable payload objects
are not cloned.

## Why no flatMap here?

An unrestricted right-biased `flatMap` makes a later dependent
validation impossible to evaluate if an earlier validation is
`Invalid`. That would be fail-fast sequencing (as in `Either`),
rather than the independent accumulation that distinguishes this type.
Hercules therefore leaves `flatMap` out of this increment instead of
promising incompatible algebraic laws.

Likewise, `mapN`, `sequence`, `traverse` and asynchronous
`CompletionStage` combinations are future, separately reviewed #9
subtasks. Those semantics will specify evaluation order and cancellation
where relevant.

## Verification

`ValidationLawsTest` and `ValidationApplicativeLawsTest` use Instancio
`@Given` values across both variants; they check Functor laws,
conversion and bimap coherence, all four Applicative laws, all invalid/
valid combinations, accumulation order, strict invocation, null
contracts and exception propagation.

`ValidationExamplesTest` demonstrates form fields and curried
constructors. `ValidationPublicApiTest` compiles and runs from an
independent Java package.

```bash
./mvnw -B -ntp clean verify
```

Java 17 is the baseline; CI also verifies Java 21 and Java 25.
Parent: #9. Initial subtask: #39.
