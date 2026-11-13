# Nullness contracts for Hercules

Hercules has one shared nullness policy across its current public APIs:
`Either`, `These`, `Those`, `Validation`, the choice and applicative
policy records, and the generated `F3`–`F10` interfaces.

## JSpecify: non-null by default, explicit nullable type uses

The Java package is annotated with JSpecify `@NullMarked` in
`package-info.java`. Unless explicitly qualified, types in public
signatures are non-null. To represent an intentionally nullable payload,
**annotate the type argument**, not the variant itself:

```java
import org.jspecify.annotations.Nullable;

Either<@Nullable String, Integer> left = Either.left(null);
These<@Nullable String, @Nullable Integer> both = These.both(null, null);
Those<String, Integer, @Nullable String> absent = Those.neither(null);
Validation<String, @Nullable Integer> validNull = Validation.valid(null);
```

Generic payloads (`L`, `R`, `E`, `A`, and the argument/result type
parameters of `F3`–`F10`) must **not** acquire unconditional Lombok
`@NonNull` checks in their record components or factories. Such checks
would break explicitly nullable generic specializations.

`Left(null)`, `Right(null)`, `Both(null,null)`,
`Neither(null)`, `Invalid(null)`, and `Valid(null)` remain
distinguishable records whenever their type parameters are deliberately
nullable. A null payload is **never** a substitute for a variant.

`Either.getOrElse(fallback)`, `ValidationApplicative.pure(value)`,
and `EitherChoice.emptyLeft` follow the same generic-payload rule.
`Either.toOptional()` intentionally produces `Optional.empty()` for
`Right(null)`, which is a documented lossy conversion.

JSpecify describes static contracts. It does **not** automatically
check incoming null values or callback results at runtime. Automated
static nullness analysis is tracked separately in #41; do not confuse a
successful Java compilation with full JSpecify enforcement.

## Lombok: runtime checks on structural references

Annotate required **callback, function, combiner, supplier and input
container references** with Lombok `@NonNull`, including parameters to
public default/static methods and required record components:

```java
default <T> Either<L, T> map(@NonNull Function<? super R, ? extends T> mapper)

record ValidationApplicative<E>(@NonNull BinaryOperator<E> combineErrors)
```

The configured Lombok annotation processor emits a fail-fast
`NullPointerException` at method/constructor entry. These checks
remain eager even for handlers of cases not currently selected:

```java
Either<String, Integer> left = Either.left("no value");
left.map(null); // throws NullPointerException immediately
```

For total eliminators (`fold`), annotate **all** callbacks in the
sealed interface declaration and every record implementation.
Annotating only an abstract declaration documents intent but cannot
insert a runtime check into an implementation with no corresponding
annotation.

The caller-facing error **type** is `NullPointerException`. Lombok
generates its own diagnostic message (for example, `mapper is marked
non-null but is null`), which differs from handwritten
`Objects.requireNonNull` messages. Do not rely on the message text
as part of the supported API.

Lombok `@NonNull` on a record's combiner component checks that
function reference in its canonical/compact constructor, without
requiring manual boilerplate. Payload record components are
intentionally excluded. The nullness check is generated when the
**library** is compiled; consumers do not need to run Lombok themselves.

## A separate rule for callback results

Distinguish two cases:

1. **Structural results** (the next `Either`, `These`, another
   `Validation`-style container, an `F3`–`F10` function returned by
   `flatMap`, or a lazy choice supplier's `Either`) must not be null.
   Preserve an explicit `Objects.requireNonNull` **at the point the
   structural result is evaluated**. A skipped callback is never called.
2. **Payload results** are governed by their declared generic type.
   A mapping that produces an explicitly nullable result type can
   return null and must not be rejected unconditionally:

```java
Either<String, @Nullable Integer> result =
        Either.<String, Integer>right(7).map(value -> null);
// Right(null), not an exception
```

`flatMap` functions on `Either` and `These` run eagerly on their
right/both cases. `F3`–`F10` adapters are **lazy**: they validate
their callback references eagerly during composition but reject a
null returned function only when the resulting function is applied.
`EitherChoice.choose(first, supplier)` rejects a null supplier
immediately; it checks a null **supplier result** only if fallback is
actually needed.

A mapper declared to return a non-null type must honor that declared
contract. Hercules does not impose a blanket runtime result check on
generic payloads because it would reject intentionally nullable types.
This is a distinction between static nullness correctness and runtime
defensive validation.

## New-code checklist

- Keep `@NullMarked` at each package boundary (`package-info.java`).
- Identify **structural references** versus **generic payloads**.
- Put Lombok `@NonNull` on required structural parameters and
  record components, and let it generate the precondition check.
- Put JSpecify `@Nullable` on intentionally nullable **type uses**.
  Do not add blanket null checks to generic payload factories/records.
- Check structural callback **results** when executed; never eagerly
  invoke a lazy computation just for null validation.
- Do not use Lombok `@Data` or mutable generated classes for FP
  abstractions; retain sealed interfaces, records and pure functions.
- Change `scripts/generate_functions.py` first for `F3`–`F10`;
  regenerate the checked-in sources. Do not edit generated Java by hand.
- Test negative contracts with JUnit and Instancio for each active
  variant and arity, plus explicitly nullable payload specializations.
- Verify on Java 17, 21 and 25, including Javadocs; keep a
  separate-package consumer test where useful.

The canonical Java package is `bbb.hercules` (#36); its relocated
`package-info.java` retains `@NullMarked`. New subpackages require their
own package-level nullness declaration. Static nullness analysis is #41.
The policy was established in #35.
