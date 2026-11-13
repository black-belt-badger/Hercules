# Choice and Alternative — explicit, lawful operations

Haskell's `Alternative` typeclass combines an `Applicative` with
an associative choice operator `<|>` and its identity `empty`.
For the choice part, the core laws are:

```text
empty <|> x                 = x
x <|> empty                 = x
(x <|> y) <|> z             = x <|> (y <|> z)
```

This is a **monoid of alternatives**. The applicative aspect also
requires its own laws and operations. Hercules is Java 17 and does not
emulate higher-kinded typeclasses with unchecked casts. This first
experiment implements a *concrete choice monoid* where it adds value,
not a full `Alternative<F<_>>` interface.

## Two established Java instances need no new wrapper

For `Optional<A>`: `empty = Optional.empty()` and choice takes the
leftmost present value. Since Java 9, `Optional.or(Supplier)` provides
a lazy fallback directly.

```java
Optional<Integer> result = Optional.<Integer>empty()
        .or(() -> Optional.of(42)); // Optional[42]
```

For lists: `empty = List.of()` and choice concatenates lists in order.

```java
List<Integer> result = Stream.concat(
        List.of(1, 2).stream(),
        List.of(3).stream()).toList(); // [1,2,3]
```

These operations have associative choice and identity by content equality;
the JDK already supplies the necessary building blocks. In particular,
Hercules does **not** create a new `Alternative` interface merely to
rename `Optional.or` or `Stream.concat`.

## Either is different: what is its empty value?

Hercules `Either<L,R>` contains either a left or a right value.
Unlike `Optional`, it has no universally defined empty value: the
left payload `L` may carry arbitrary information. We cannot silently
invent an empty `L` or choose whether to discard failed alternatives.

`EitherChoice<L>` makes one coherent policy **explicit**:

- The caller supplies `emptyLeft` and an associative `combineLeft`.
- `empty()` produces `Left(emptyLeft)`.
- `choose(first, second)` prefers the leftmost `Right`.
- Two left alternatives are combined as `Left(combineLeft(first,second))`.
- `choose(first, Supplier<second>)` defers evaluating the second
  alternative unless `first` is `Left`.

For laws to hold, the caller must supply a true monoid on L: its
combiner must be associative and `emptyLeft` must be a two-sided
identity. Hercules does not infer or verify this for arbitrary lambdas.

```java
EitherChoice<String> choice = new EitherChoice<>("", String::concat);

Either<String, Integer> result = choice.choose(
        Either.left("No data from A; "),
        () -> Either.right(42));

// Right(42)
```

This is deliberately **leftmost-success choice**. Previous Left
diagnostics are discarded when a later Right succeeds. The example
`"No data from A; "` does **not** survive into `Right(42)`. If that
diagnostic needs to accompany the successful value, use a richer result
such as `These<L,R>` (or an application-specific record).

To accumulate failures when **nothing succeeds**:

```java
var choice = new EitherChoice<>(
        List.<String>of(),
        (List<String> a, List<String> b) ->
                Stream.concat(a.stream(), b.stream()).toList());

Either<List<String>, Integer> result = choice.choose(
        Either.left(List.of("A missing")),
        Either.left(List.of("B missing")));

// Left([A missing, B missing])
```

The `EitherChoice` record is shallowly immutable. It holds the
caller-supplied identity and combiner, but does not copy identity data
or prove an arbitrary function associative. Use immutable payloads.

## Evaluation and nullness

`choose(first, second)` takes two already computed Eithers.
`choose(first, Supplier<second>)` evaluates the fallback only if
the first is Left, even when the first Left may contain null.
Arguments and callback references are checked for null immediately;
a supplier returning null is rejected *when invoked*. Exceptions
from suppliers or combiners propagate unchanged.

JSpecify `@NullMarked` is the default. Explicitly nullable L
type arguments are allowed, including `EitherChoice<@Nullable L>`,
if the supplied combiner genuinely has a nullable identity.

## Scope boundaries

The type is an **explicit choice monoid for Either**. It is not,
by itself, a complete Haskell `Alternative` instance: Hercules does
not currently expose an Applicative typeclass or `ap` API, and
does not promise any corresponding distribution/absorption laws.

`Either.flatMap` is fail-fast sequencing, not error accumulation;
`EitherChoice.choose` is alternative selection, not sequencing.
`These.flatMap` is a different dependent computation that can
preserve warnings with an explicit left combiner.

We do not add `some`, `many`, generic typeclass witnesses,
universal `Alternative` instances for `Either`, or choice to
`Those` where `Neither(E)` would be silently discarded.

Future:
- #9: Applicative `ap`, mapN, validation/error accumulation
- #27: Evidence aggregation and preservation of Neither explanations
- #23: Deferred methods on Either itself
- #5: Umbrella outcomes and choice design

## Tests

`EitherChoiceLawsTest` runs Instancio-generated left and right values
through left/right identity and associativity laws with a concrete
lawful combination (`String::concat` with empty string), plus
leftmost-success behavior, laziness, diagnostic order, null and
exception handling, and an intentional counterexample with subtraction
to show why an arbitrary `BinaryOperator` does not imply the laws.

`ChoiceExamplesTest` compares the JDK alternatives and demonstrates
either-choice behavior in a small parsing example.

```bash
./mvnw -B -ntp verify
```

Java 17 is the baseline, with verification on Java 21 and 25.
Issue #29 implements this narrow 5D increment, under umbrella #5.
