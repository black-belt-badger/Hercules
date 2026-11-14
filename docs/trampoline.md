# Trampoline<A>: stack-safe deferred recursion

`Trampoline<A>` represents a computation that can either be
**completed** (`Done<A>`) or **suspended for one step**
(`More<A>`). A private typed bind node handles composition.
`run()` evaluates the description **iteratively on the calling
thread**, without growing Java call depth as the number of suspended
steps or composed binds increases.

Trampoline directly addresses [#4](https://github.com/black-belt-badger/hercules/issues/4).
It is an opt-in abstraction for **pure recursive computations**,
not a runtime framework.

## Public API

| Operation | Meaning |
| --- | --- |
| `Trampoline.done(value)` | Terminal value; no further steps |
| `Trampoline.more(supplier)` | Defer one computation step |
| `map(f)` | Lazily transform the eventual result |
| `flatMap(f)` | Lazily compose another Trampoline |
| `run()` | Execute suspended steps and binds in an iterative loop |

`Done` and `More` are public, final, immutable nested classes;
factories are preferable to invoking their constructors directly.
Unlike a plain public record union, a sealed abstract class permits a
**private typed bind node**, and its package-internal interpreter can
evaluate nodes without exposing an unsafe continuation API.

A simple countdown:

```java
static Trampoline<Long> sum(long remaining, long accumulated) {
    return remaining == 0
            ? Trampoline.done(accumulated)
            : Trampoline.more(() -> sum(remaining - 1, accumulated + remaining));
}

long answer = sum(1_000_000L, 0L).run();
// 500000500000
```

The recursive Java method invocation takes place **inside a
Supplier**. At any moment, only one such method call is on the JVM
stack: each `More` returns a new description to the loop.

Mutual recursion works the same way:

```java
static Trampoline<Boolean> even(int n) {
    return n == 0 ? Trampoline.done(true)
            : Trampoline.more(() -> odd(n - 1));
}

static Trampoline<Boolean> odd(int n) {
    return n == 0 ? Trampoline.done(false)
            : Trampoline.more(() -> even(n - 1));
}

boolean answer = even(1_000_000).run();
// true
```

## Why a naïve two-case trampoline is insufficient

A terminal `Done` and deferred `More` suffice for a *tail-recursive*
stepper. But this common `flatMap` implementation is **not**
safe for deeply left-associated composition:

```java
// Unsafe approach for arbitrary depth:
flatMap(f) = more(() -> f.apply(this.run()));
```

The call to `this.run()` is nested in an earlier deferred computation;
a chain of hundreds of thousands of binds can still overflow the Java
stack. Wrapping that call in a Supplier does **not** solve the problem.

Hercules therefore stores each `flatMap` as a private,
heterogeneously typed `Bind<S,A>` node. The iterative interpreter
handles one node at a time, using the monad associativity equation:

```text
(source.flatMap(f)).flatMap(g)
    =
source.flatMap(a -> f(a).flatMap(g))
```

Reassociation transforms a left-nested bind into a shallower
continuation structure **without evaluating the source recursively**.
When a `Done` is reached, its continuation is invoked once; when
a `More` is reached, exactly one supplier step is forced.
Every intermediate value is captured in the private generic bind
class, so implementation requires **no unchecked casts**, raw
continuation stacks, global mutable state or higher-kinded types.

The type-system choice matters: `Trampoline` is a sealed **abstract
class** rather than an interface with public record subtypes so
the typed `Bind` implementation and internal stepping methods stay
non-public. `Done` and `More` are the only public variants.

## Composing a million binds

```java
Trampoline<Integer> computation = Trampoline.done(0);

for (int i = 0; i < 1_000_000; i++) {
    computation = computation.flatMap(n -> Trampoline.done(n + 1));
}

int result = computation.run();
// 1000000, without StackOverflowError
```

The test suite runs this example with **one million binds**, and also
checks a million suspended tail-recursive steps and a million mutual
recursion steps. It tests deeply nested mixed `More`/`flatMap`
composition, functor/monad laws and ordering with Instancio-generated
values.

These properties apply to **the Trampoline interpreter**. They do
**not** automatically change the separately defined `Reader` and
`State` functional interfaces into stack-safe monads. Both still
have their measured deep-nested-lambda limitations; converting them
into Trampoline-based interpreters would be a separate design.

## Laziness, evaluation order, exceptions and termination

All Trampoline constructors and combinators are **lazy**. A
`Supplier` passed to `more`, or a mapper passed to `map` or
`flatMap`, is not executed during composition. `run` forces
suspensions in program order, invoking a binder once when its input
result is ready. The same description can be evaluated multiple
times: Hercules **does not memoize** its values or cache side effects.

Required `Supplier` and `Function` **references** are
validated eagerly using Lombok `@NonNull`. A null Trampoline
**returned** from a supplier/binder is rejected when forced.
Explicitly nullable payload values are supported using JSpecify
`@Nullable` type arguments, and are not treated as structural
errors.

Exceptions thrown by user code propagate **unchanged**, rather
than becoming `Either.Left` or `Validation.Invalid`. The
Trampoline interpreter itself never retries, logs or catches them.
An infinite suspension chain remains nonterminating; stack safety
does not prove termination or place a step limit.

Java can perform I/O or mutation in a supplied lambda, so the API
does not guarantee that arbitrary programs are pure. For mathematical
laws and repeatable results, user functions must be deterministic,
side-effect-free and terminating.

## Not async, virtual threads or a reactive library

Trampoline changes **call-stack consumption**, not where the work
runs. Its `run()` method is a synchronous, ordinary Java loop on
the **calling thread**.

It does **not** create tasks or executors, yield to a scheduler,
enable parallel execution, interrupt a computation, propagate
cancellation, apply timeouts, or provide backpressure. Virtual
threads address concurrency and blocked-thread scalability; they
do not turn recursively nested function calls into an iterative
interpreter. Reactor, RxJava and reactive streams add different
execution protocols. Choose them only when those independent
capabilities are required.

For a long-running computation that needs interruption or a
resource budget, a caller must provide that policy explicitly,
for example through a bounded state machine or a separately
designed cancellable interpreter.

## Reproducible performance and allocation comparison

An opt-in **manual** benchmark is checked in. After compiling
tests:

```sh
./mvnw -B -ntp test-compile
java -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.TrampolineBenchmark 1000000
```

It measures the same million-step arithmetic work in an explicit
`for` loop versus a tail-suspended Trampoline, and a second
million-step counter in a loop versus a deeply bound Trampoline.
The full cost of building and running the bind chain is included.
JDK thread-allocation counters include allocations made by
the Java lambdas/nodes. Every strategy returns a checked numeric
result consumed by the benchmark, avoiding obviously dead work.

Illustrative **single Java 25 run on Hoopoe** at 1,000,000 steps,
rounded (milliseconds and bytes allocated by the benchmark thread):

| Strategy | Time (ms) | Allocated bytes |
| --- | ---: | ---: |
| Explicit sum loop | 1.69 | 0 |
| Trampoline `More` | 13.91 | 48,000,040 |
| Explicit count loop | 1.11 | 0 |
| Trampoline `flatMap` | 41.62 | 135,996,896 |

These values are **not performance guarantees**. JVM warmup,
JIT optimizations, heap size, instrumentation and workstation load
affect measurements. There are no timing thresholds in CI; for
statistically valid performance comparisons use JMH.

For straightforward iterative computation, an ordinary loop is
simpler and generally faster. Trampoline is valuable where
**recursive and compositional structure matters** and stack
safety is required. Avoid retaining giant completed graphs when a
simple state-loop suffices.

## Laws and scope

For pure, normally terminating functions:

```text
Functor identity:
    m.map(identity).run()                          == m.run()

Functor composition:
    m.map(f).map(g).run()                          == m.map(f.andThen(g)).run()

Monad left identity:
    done(a).flatMap(f).run()                       == f(a).run()

Monad right identity:
    m.flatMap(Trampoline::done).run()               == m.run()

Monad associativity:
    m.flatMap(f).flatMap(g).run()                   ==
    m.flatMap(a -> f(a).flatMap(g)).run()
```

`TrampolineLawsTest` checks all these observational laws with
Instancio inputs, plus laziness, exact callback order/count,
exception propagation, structural nullness, explicit nullable
results, non-memoization and deep stack safety.
`TrampolinePublicApiTest` demonstrates a pure large
market-data cursor calculation from a **different Java package**.

**Decision:** implement a sealed, private-bind Trampoline with
public `Done`/`More` variants and a synchronous iterative
interpreter. Defer cooperative cancellation, execution budgeting,
tracing, integration with Reader/State interpreters, and async
evaluation until explicit requirements justify the extra API.
No external runtime dependency or new typeclass is required.
