# Writer<W,A>: values accompanied by accumulated output data

`Writer<W,A>` is an immutable Java record containing a computation
`value` and accumulated `output`. Writer's purpose is to **describe
and combine** diagnostic events, pretty-printer fragments, provenance,
statistics or other structured output **as data**. It never logs, prints,
sends messages or performs I/O on its own.

Writer is one focused part of [#3](https://github.com/black-belt-badger/hercules/issues/3);
Reader and State are implemented as separate abstractions.

## Types and API

```java
public record Writer<W, A>(A value, W output) { ... }

public record WriterPolicy<W>(
        W empty,
        BinaryOperator<W> combineOutputs) { ... }
```

Writer exposes `value()`, `output()`, `map(f)`, and
`flatMap(f, policy)`. `WriterPolicy` exposes the two policy
components and `pure(value)`, which lifts any value with the supplied
identity output.

This is a **concrete** interface, not a new `Monoid` typeclass,
higher-kinded type framework or mandatory application architecture.
The output policy is passed **explicitly** to `flatMap`; it is not
stored in global state or hidden in the Writer data record.

A caller must supply a **two-sided identity** and an **associative**
`BinaryOperator<W>`. Commutativity is not needed; diagnostic
ordering usually matters. Neither JDK types nor Hercules can prove
that arbitrary Java callbacks satisfy the laws.

## Compose immutable diagnostics with DList

```java
record Diagnostic(String source, String message) {}
record Order(int quantity, BigDecimal price) {}

WriterPolicy<DList<Diagnostic>> policy =
        new WriterPolicy<>(DList.empty(), DList::append);

Writer<DList<Diagnostic>, Integer> quantity =
        new Writer<>(2, DList.singleton(
                new Diagnostic("quantity", "validated")));
Writer<DList<Diagnostic>, BigDecimal> price =
        new Writer<>(new BigDecimal("12.50"), DList.singleton(
                new Diagnostic("price", "validated")));

Writer<DList<Diagnostic>, Order> order = quantity.flatMap(
        qty -> price.map(p -> new Order(qty, p)),
        policy);

// order.value() == Order(2, 12.50)
// order.output().toList() ==
//   [Diagnostic("quantity", "validated"),
//    Diagnostic("price", "validated")]
```

The `DList` policy makes `flatMap` output concatenation
**constant time**, even when its output has a long append history.
The output is flattened **only when the caller invokes `toList()`**,
using DList's iterative stack-safe traversal. Already obtained
Writers remain usable and unchanged. No mutable output buffer escapes.

A regular immutable `List<W>` also works if the caller supplies a
suitable identity and associative concatenator. It is simpler for
small outputs but repeatedly copying long immutable lists makes
deep Writer chains more expensive.

`WriterPublicApiTest` provides a complete consumer-package example
of independent validation steps and structured diagnostics, without
performing any logging I/O. It demonstrates that a warning is output
**data**, not necessarily an error, exception or a failed computation.

## Evaluation and ordering

All operations are **eager, strict and synchronous**:

- `writer.map(f)` calls `f` exactly once with the current value,
  preserving the output unchanged.
- `writer.flatMap(f, policy)` calls `f` exactly once, requires a
  non-null returned `Writer`, then calls
  `policy.combineOutputs()` **once** on the old output followed by
  the next output. The value comes from the returned Writer.
- Both `f` and `policy` references are checked **at entry** by
  Lombok `@NonNull`; a null Writer produced by `f` is rejected when
  evaluated. The policy's combiner reference is checked at
  construction, but its identity is a generic payload.
- Callback and combiner exceptions propagate unchanged. Writer does
  not convert them to `Either.Left`, `Validation.Invalid`, or log
  them. Users retain control over interpreting effects and failures.

JSpecify `@NullMarked` applies. As with other Hercules generic
records, `A` and `W` may be specialized with explicit `@Nullable`
type arguments. A null payload or identity is not automatically
an exception, but a null structural callback or Writer is invalid.
User payload objects are shallowly stored and not copied.

## Algebraic laws

Let `p` be one fixed `WriterPolicy` with identity `e` and
associative operation `<>`. Then Writer's laws hold **by value
equality** when supplied functions are pure and outputs are
immutably combined:

```text
Functor identity:
    m.map(identity)                         == m

Functor composition:
    m.map(f).map(g)                         == m.map(f.andThen(g))

Monad left identity:
    p.pure(a).flatMap(f, p)                 == f(a)

Monad right identity:
    m.flatMap(p::pure, p)                   == m

Monad associativity:
    m.flatMap(f,p).flatMap(g,p)              ==
    m.flatMap(a -> f(a).flatMap(g,p), p)
```

The laws require `e <> w == w`, `w <> e == w`, and
`(a <> b) <> c == a <> (b <> c)`. They do **not**
require `a <> b == b <> a`. The laws assume the same
policy is used consistently. An operator that builds differently
parenthesized strings, for example, breaks associativity; a
counterexample is included in the tests.

`WriterLawsTest` uses Instancio to test functor and monad laws
and comparisons with an ordinary list oracle. It also tests
callback order and count, structural nullness, explicit nullable
payloads, persistence and exceptions.

## Stack safety and limits

Writer's `flatMap` executes eagerly. It does not construct a chain
of deferred Java functions that will recursively evaluate during
a later `run`. Together with a stack-safe output carrier such as
`DList`, a normal iterative loop can perform **120,000 sequential
binds** and materialize the result without stack overflow; this is
verified in an executable test.

This is **not** a stack-safe interpreter for arbitrary recursive
user code. If a binder recursively calls itself, those nested Java
calls can still exhaust the JVM stack. Use the separate
[Trampoline](trampoline.md) (#4) where a computation requires deferred recursion.

No memoization, concurrency, cancellation, retry or scheduling is
introduced. The Writer record stores a *completed* value and output.

## When Writer is appropriate

Use Writer when pure business steps need to **return diagnostics,
formatted fragments or provenance in addition to values**. Interpret
those outputs separately. For typed diagnostic events and explicitly
redacted, bounded backend emission, see [structured logging](logging.md).
A Writer can carry `DList<LogEvent>` without invoking a logger until
the application deliberately calls `LogInterpreter`.

Use ordinary return values or `DList` alone when no dependent
value sequencing is needed. Use `Validation` to accumulate
independent domain **errors**, and `Either` for dependent
fail-fast choices; Writer itself has **no failure branch**. A Writer
with warning events has still produced a value.

The public API deliberately omits `tell(Void)`/a synthetic
unit-type abstraction and a general `Monoid` hierarchy for now.
Callers can construct `new Writer<>(value, output)` to record
additional output and use `policy.pure(value)` for an empty-output
step. No additional library dependency is needed.
