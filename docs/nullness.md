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
would break explicitly nullable generic specializations. A generic
parameter that permits nullable instantiations is declared as
`<T extends @Nullable Object>`; this is a **bound on the type parameter**,
not permission to pass null when instantiated with a non-null type:
`Either<String, Integer>` still rejects null at statically checked call sites.

`Left(null)`, `Right(null)`, `Both(null,null)`,
`Neither(null)`, `Invalid(null)`, and `Valid(null)` remain
distinguishable records whenever their type parameters are deliberately
nullable. A null payload is **never** a substitute for a variant.

`Either.getOrElse(fallback)`, `ValidationApplicative.pure(value)`,
and `EitherChoice.emptyLeft` follow the same generic-payload rule.
`Either.toOptional()` intentionally produces `Optional.empty()` for
`Right(null)`, which is a documented lossy conversion.

JSpecify describes static contracts, not runtime checks. The Maven build
now enforces its statically checkable contracts with NullAway, as described
below. Compilation does not add runtime validation of generic payloads.

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
own package-level nullness declaration. Static analysis was introduced in
#41; the underlying runtime nullness policy was established in #35.

## Static verification: NullAway in Maven and CI (#41)

`./mvnw verify` runs NullAway **in JSpecify mode**, through Error Prone,
for every production Java file in `src/main/java`. This includes the
checked-in, generator-produced `F3`–`F10` interfaces. No blanket nullness
suppressions have been introduced. Intentionally nullable payloads use
JSpecify `@Nullable` type arguments and `extends @Nullable Object` bounds;
structural callback references remain non-null by default.

We use `nullability-maven-plugin` 0.5.2 to integrate with the existing
Maven compiler configuration (including Lombok), NullAway 0.14.2, and
Error Prone 2.42.0. The latter is pinned because later Error Prone
releases require JDK 21 to run; Hercules retains Java 17-compatible builds.
JSpecify mode on JDK 17 and 21 requires recent OpenJDK/Temurin versions
(17.0.19+ / 21.0.8+) and `-XDaddTypeAnnotationsToSymbol=true`;
the plugin supplies that flag. JDK 25 can run without it. The CI matrix
compiles and verifies under 17, 21 and 25 while emitting Java 17 bytecode.

The trade-off versus Checker Framework is fast, incremental Error Prone
integration and light annotation maintenance, at the cost of NullAway's
known incomplete coverage of generic inference and some generic-class
internals. Static analysis is valuable, but not a proof of total null safety.
The normal build checks production sources; deliberately nullable JUnit
runtime tests remain separate from the static check. See also
https://github.com/uber/NullAway/wiki/JSpecify-Support.

Run `./scripts/verify-nullness.sh` to exercise both **positive** and
**negative** external consumer source fixtures in disposable Maven builds:
- Explicitly nullable `Either`, `These`, `Those`, `Validation`, `F3`,
  and `ValidationApplicative.sequence`/`traverse` payloads must compile.
- A null return, a null non-null generic payload and a null required
  callback must each generate a NullAway compilation diagnostic.

The CI matrix runs this script after the standard `verify`. It uses copies
of sources, not temporary mutations of the working tree, so the tests cannot
leave broken Java files behind. Lombok `@NonNull` checks remain a distinct
**runtime** defense and are never replaced by compiler checking.
