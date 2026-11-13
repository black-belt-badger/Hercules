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
For independently supplied asynchronous checks, use the stage
composition methods documented below. Scheduling, recovery and
cancellation remain the caller's responsibilities.

## Arity-aware map3 through map10

When combining a fixed number of independent validations, the methods
`map3`–`map10` accept Hercules' existing `F3`–`F10` functional
interfaces. `map2` continues to use the JDK `BiFunction`.

```java
record Order(String symbol, int quantity, BigDecimal price) {}

Validation<List<String>, Order> order = app.map3(
        Validation.valid("BTC"),
        Validation.invalid(List.of("quantity must be positive")),
        Validation.invalid(List.of("price must be positive")),
        Order::new);
// Invalid(["quantity must be positive", "price must be positive"])
```

Each argument is an **already computed** `Validation`. All container
and mapper references must be non-null and are checked eagerly, even
when earlier validations are invalid. The corresponding `map3`–`map10`
method never invokes its mapper unless all inputs are `Valid`; then it invokes it exactly
once with payloads in the original order. Errors accumulate left to
right, once per additional `Invalid`, using the caller-supplied
associative `combineErrors`. Error order is meaningful; an identity
element and commutativity are **not** required.

Heterogeneous payload types work naturally with constructors and
lambdas. Explicitly nullable successful payload and result types
remain supported, including a mapper that intentionally returns null.
Exceptions from the mapper or error combiner propagate without being
converted into validation errors.

Under the hood, `map3`–`map10` are generated from
`scripts/generate_validation_mapn.py` and delegate to `pure`,
`ap` and the appropriate `F3`–`F10` `curried()` method. This ensures their
accumulation semantics are the same lawful Applicative operations;
there are no unchecked casts, runtime tuple objects, or new typeclass
hierarchies. Change the **generator**, not the marked Java method
region.

`mapN` is useful for a fixed known number of heterogeneous fields;
`sequence` and `traverse` below are better for a variable number
of homogeneous checks.

## Sequence and traverse — many independent validations

`sequence` collects an iterable of already computed validations into
one validation of an **immutable, ordered list**:

```java
Validation<List<String>, List<Integer>> result = app.sequence(List.of(
        Validation.valid(42),
        Validation.invalid(List.of("quantity invalid")),
        Validation.invalid(List.of("price invalid"))));
// Invalid(["quantity invalid", "price invalid"])
```

`traverse` is its mapping counterpart: given an iterable of source
values, apply a validation-producing function **once to each element**
in encounter order, and then accumulate all errors.

```java
Validation<List<String>, Integer> checkPositive(int n) {
    return n > 0
            ? Validation.valid(n)
            : Validation.invalid(List.of("must be positive: " + n));
}

Validation<List<String>, List<Integer>> checked =
        app.traverse(List.of(3, -1, -2, 5), this::checkPositive);
// Invalid(["must be positive: -1", "must be positive: -2"])
```

The callback and iterable references are validated at method entry.
Each element is visited **even after any number of Invalid results**.
A null validation *container* (including a null value returned by the
mapper) throws `NullPointerException` when encountered. Input values
can be null if the input type is explicitly `@Nullable`. A successful
nullable output value remains an entry in the result list; it is not
mistaken for an error or removed.

Both operations follow the same policy:

| Input cases | Result |
| --- | --- |
| Empty iterable | `Valid([])` |
| Every element valid | `Valid(unmodifiable ordered snapshot)` |
| One invalid | `Invalid(that error)` |
| Several invalid | `Invalid(combineErrors(...))`, encounter order |

The combined error type requires **associativity** but no identity.
The returned list cannot be modified through its public API, and it
does not change if the input iterable is later modified. Stored element
objects and error payloads are not defensively deep-copied.

`traverse` does **not** eagerly schedule checks before it begins
iteration or run them in parallel. It executes each mapper synchronously
while traversing once. It does **not** catch or accumulate Java
exceptions: exceptions from iterators, mappers, or the error combiner
propagate immediately. Consequently, accumulation is guaranteed only
for validations that are actually returned; a thrown exception
terminates the operation. Both APIs assume a **finite** iterable, and
perform a linear number of steps; an infinite iterable does not
terminate.

These operations use JDK `Iterable`, `Function` and `List`, with
no new typeclass hierarchy. `sequence` is equivalent to traversing
the validation objects with `Function.identity()`; `traverse(xs,
Validation::valid)` yields a valid list of the original values.

## Independently supplied CompletionStage results

`ValidationApplicative<E>` can combine already-created JDK
`CompletionStage<Validation<E,A>>` values without blocking or
scheduling new work.

```java
record Quote(String symbol, int quantity) {}

CompletableFuture<Validation<List<String>, String>> symbol =
        new CompletableFuture<>();
CompletableFuture<Validation<List<String>, Integer>> quantity =
        new CompletableFuture<>();

CompletionStage<Validation<List<String>, Quote>> combined =
        app.map2Stages(symbol, quantity, Quote::new);

// These stages might be completed by independent APIs or executors.
quantity.complete(Validation.invalid(List.of("quantity invalid")));
symbol.complete(Validation.invalid(List.of("symbol invalid")));

// The caller chooses when (or whether) to await the result.
var result = combined.toCompletableFuture().join();
// Invalid(["symbol invalid", "quantity invalid"])
```

The new APIs are **`map2Stages(left, right, mapper)`** and
**`sequenceStages(Iterable<CompletionStage<Validation<...>>>)`**.
Both use JDK `thenCombine`, delegating every ordinary pairwise
validation result to the same `map2` algebra. With two normally
completed stages, `map2Stages` invokes its mapper once only when
both are `Valid`. Multiple normally completed `Invalid` values
combine their errors in the supplied **argument order**, not the
temporal completion order.

`sequenceStages` enumerates a **finite iterable once at
registration time**, builds a dependent chain and returns immediately
without waiting for completion. An empty iterable produces an
already-completed `Valid([])`. When all stages complete normally,
it returns either every validation failure in **input encounter
order**, or a shallowly immutable snapshot of all successful
payloads. Explicitly nullable result payloads are preserved.

```java
CompletionStage<Validation<List<String>, List<Integer>>> checks =
        app.sequenceStages(List.of(
                CompletableFuture.completedFuture(
                        Validation.<List<String>, Integer>valid(10)),
                CompletableFuture.completedFuture(
                        Validation.<List<String>, Integer>invalid(
                                List.of("invalid limit")))));
```

### Three different failure channels

| Event | Result |
| --- | --- |
| Both/all stages complete normally with `Valid` | Normal `Valid` completion |
| Any stage completes normally with `Invalid` | Normal `Invalid` completion, accumulating errors from other normal `Invalid` results |
| A stage completes exceptionally, including cancellation | Dependent stage completes exceptionally, **not** `Invalid` |
| User mapper or error combiner throws | Dependent stage completes exceptionally |
| Stage completes normally with a null `Validation` object | Dependent stage completes exceptionally with a null-contract failure |

Java exceptions, connection loss, timeouts and cancellation are
**execution-level concerns**, not arbitrary domain validation errors.
If two source stages complete exceptionally, JDK `CompletionStage`
does not promise which exception is exposed by their combination.
There is no automatic aggregation of exceptions into an `E` payload.

### Scheduling, cancellation and evaluation

The caller supplies the stages and decides how they start. Hercules
does **not** invoke `supplyAsync`, allocate an executor, call
`join`/`get`, launch tasks or catch their exceptions. A non-async
`thenCombine` continuation executes under its stage
implementation's execution policy; it may run on a completion thread.

This is also **not a cancellation framework**: cancelling a returned
`CompletableFuture` does not cancel either source stage. Cancelling
a source is exceptional completion, not `Invalid`. There is no
promise to cancel siblings or await all stages after an exceptional
completion. These properties are intentional—higher layers own
timeouts, retries, batch cancellation and recovery.

Null stage references and a null mapper fail **at method call time**.
A null Validation object supplied by a stage is rejected **when that
stage completes normally**, making the dependent stage exceptional.
An exception thrown while enumerating the input iterable is thrown
during registration, rather than converted into a stage result.

### Choosing the right JDK abstraction

- Use plain JDK `thenCombine` when both stages compute ordinary
  results and no accumulation of independent domain errors is needed.
- Use `map2Stages` to combine **heterogeneous** validated results;
  use `sequenceStages` for a variable number of **homogeneous**
  validated stages.
- Use synchronous `map2` / `sequence` when all Validation objects
  are already available; do not wrap those values in futures solely
  to call asynchronous APIs.
- `Optional` can describe an absent value, but it cannot represent
  typed, ordered error accumulation or distinguish failed validation
  from exceptional stage completion.

## Nullness and mutability

The package uses JSpecify `@NullMarked`. If an application explicitly
intends nullable validation payloads, it can write:

```java
Validation<@Nullable String, @Nullable Integer> result = Validation.valid(null);
```

The variant remains `Valid`, not `Invalid` or an absent state.
Lombok `@NonNull` validates required callback references, input
containers and combiner references at runtime, including record
constructor combiners. Generic value and error payloads are **not**
subject to blanket runtime null checks, preserving explicitly nullable
type arguments. See the [repository nullness policy](nullness.md).

These are **shallowly immutable** records; mutable payload objects
are not cloned.

## Why no flatMap here?

An unrestricted right-biased `flatMap` makes a later dependent
validation impossible to evaluate if an earlier validation is
`Invalid`. That would be fail-fast sequencing (as in `Either`),
rather than the independent accumulation that distinguishes this type.
Hercules therefore leaves `flatMap` out of this increment instead of
promising incompatible algebraic laws.

For independently executing work, `map2Stages` and `sequenceStages`
combine normally completed validations without converting stage
exceptions into `Invalid` values. They do not introduce a cancellation
or execution policy. Synchronous `map3`–`map10` are described above.

## Verification

`ValidationLawsTest` and `ValidationApplicativeLawsTest` use Instancio
`@Given` values across both variants; they check Functor laws,
conversion and bimap coherence, all four Applicative laws, all invalid/
valid combinations, accumulation order, strict invocation, null
contracts and exception propagation.

`ValidationExamplesTest` demonstrates form fields and curried
constructors. `ValidationTraversalLawsTest` and
`ValidationTraversalBehaviorTest` check ordered accumulation, mapping
coherence, empty/singleton cases, no short-circuiting, null contracts,
single traversal, immutable results and error propagation.
`ValidationMapNLawsTest` exercises all failure subsets from
arity three through ten with Instancio-generated values and checks
error order, exactly-once mapping, error/mapper exceptions, non-null
container contracts and nullable result types. `ValidationPublicApiTest`,
`ValidationTraversalPublicApiTest`, `ValidationMapNPublicApiTest`,
and `ValidationStagesPublicApiTest` compile and run from an independent
Java package. `ValidationStagesTest` covers reverse completion order,
exceptional and cancelled stages, null structural results, pure
collection behavior and immutable outputs.

```bash
./mvnw -B -ntp clean verify
```

Java 17 is the baseline; CI also verifies Java 21 and Java 25.
Parent: #9. Accumulating Applicative: #39; sequence/traverse: #54;
arity-aware map3–map10: #56; CompletionStage composition: #61.
