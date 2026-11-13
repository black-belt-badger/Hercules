# Reader<R,A> — pure environment-dependent computations

`Reader<R,A>` is a Java 17 `@FunctionalInterface` representing
a computation `R -> A`. It makes configuration, context and
dependency information **explicit**, rather than hiding it in
thread-local state, a global singleton or a framework container.

`Reader` is one focused increment of [#3](https://github.com/black-belt-badger/hercules/issues/3).
`Writer` is already available, and `State` remains a separate
increment. Readers are Java functions; they do not require an
application to adopt a functional-programming runtime.

## Public API

| Operation | Meaning |
| --- | --- |
| `run(environment)` | Evaluate under the explicit environment |
| `map(f)` | Lazily transform the returned value |
| `flatMap(f)` | Lazily choose another Reader based on the value, with the **same** environment |
| `ask()` | Return the current environment |
| `asks(selector)` | Lazily project a value from the environment |
| `local(transform)` | Run a selected Reader under a transformed **local** environment |
| `pure(value)` | Return a constant value, ignoring the environment |

`Reader<R,A>` can be created as a plain Java lambda:

```java
record Settings(String host, int timeoutSeconds) {}
record Request(String endpoint, int timeoutSeconds) {}

Reader<Settings, String> endpoint =
        Reader.asks(settings -> "https://" + settings.host() + "/orders");
Reader<Settings, Integer> timeout =
        Reader.asks(Settings::timeoutSeconds);

Reader<Settings, Request> request = endpoint.flatMap(url ->
        timeout.map(seconds -> new Request(url, seconds)));

Settings config = new Settings("api.example.test", 30);
Request result = request.run(config);
// Request("https://api.example.test/orders", 30)
```

The two constituent Readers receive the **same** `Settings` object.
A supplied configuration can be reused across independent Readers
or replaced for one evaluation. No Reader caches a previously
evaluated configuration or adds hidden state.

## Local environment transformations

`local` accepts an `R -> R` transformation and scopes it to the
specific Reader on which it is called:

```java
record Config(String region, int timeout) {}

Reader<Config, String> region = Reader.asks(Config::region);
Reader<Config, String> sandbox = region.local(
        original -> new Config("sandbox", original.timeout()));

Config prod = new Config("production", 15);

assert region.run(prod).equals("production");
assert sandbox.run(prod).equals("sandbox");
assert prod.region().equals("production");
```

The transformed environment is supplied when the Reader is run.
Hercules never mutates the caller's record or stores a writable
environment field. Java cannot forbid a user-supplied transformer
from mutating its own input, so the immutability guarantee requires
immutable inputs and pure transformations.

The current `local` uses the **same** environment type `R`,
following conventional Reader semantics. A broader `contramap`
operation accepting a different source environment type is left
for a separately justified API need.

## Evaluation and nullness

`map`, `flatMap`, `local` and `asks` build Readers **lazily**.
They do not invoke user callbacks or fetch an environment during
composition. Evaluation happens when `run(environment)` is called,
and each evaluation may produce a different value depending on
the explicit environment.

- `map(f)`: run the original Reader once, then invoke `f` once.
- `flatMap(f)`: run the original Reader once, invoke `f`
  once with the result, require the returned Reader to be non-null,
  then run it once with the **same** environment.
- `local(f)`: invoke the environment transformation once, then
  evaluate the original Reader once with the transformed environment.
- `asks(f)`: invoke `f` once at evaluation time.

Required callback **references** are checked eagerly at composition
time by Lombok `@NonNull`; a null Reader **returned by the binder**
is rejected at evaluation time with `NullPointerException`.
Exceptions thrown by user code propagate unchanged. They are not
converted into `Either.Left` or `Validation.Invalid`.

JSpecify's package-level `@NullMarked` applies. To support
intentionally nullable environments or computed values, annotate the
**generic type argument** with `@Nullable`:

```java
Reader<@Nullable String, @Nullable Integer> length =
        Reader.asks(text -> text == null ? null : text.length());
assert length.run(null) == null;
```

This does not weaken structural callback nullness. `pure(value)`
stores the supplied value by reference; it is not a deep clone.

## Laws, and what Java cannot prove

A pure Reader satisfies the expected Functor and Monad laws **by
observational equivalence**, i.e. running both sides under the same
environment produces the same result:

```text
Functor identity:
    m.map(identity)                          ~= m

Functor composition:
    m.map(f).map(g)                          ~= m.map(f.andThen(g))

Monad left identity:
    Reader.pure(a).flatMap(f)                ~= f(a)

Monad right identity:
    m.flatMap(Reader::pure)                  ~= m

Monad associativity:
    m.flatMap(f).flatMap(g)                   ~=
    m.flatMap(a -> f(a).flatMap(g))
```

They hold for **pure, deterministic** user functions which do
not throw, and for evaluations which complete normally. Java cannot
enforce purity: a lambda can mutate state, perform I/O, read a clock,
or throw. Such effects can make two lawful-looking expressions
observably different. The library does not claim otherwise.

`ReaderLawsTest` uses Instancio-generated environments and
values to test the laws, including associativity and
same-environment propagation. Other tests verify callback order
and counts, laziness, local scoping, null contracts and user
exception propagation. `ReaderPublicApiTest` demonstrates
independent consumer-package configuration queries.

## Stack depth: a measured limitation

The simplest and most interoperable Java implementation of Reader
is a lambda that invokes another Reader. Each `map` and
`flatMap` wraps the previous function in a new function.
Constructing a deep chain is **lazy and stack-safe**, but *running*
that chain creates nested JVM calls.

This is not a stack-safe interpreter of arbitrary Reader binds.
We deliberately keep the public abstraction as a straightforward
functional interface rather than hide unchecked heterogeneous
continuation casts or introduce an opaque execution runtime.

A manual, deliberately non-JUnit probe is checked in:

```sh
./mvnw -B -ntp test-compile
java -Xss512k -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.ReaderDepthProbe 100000
java -Xss512k -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.ReaderDepthProbe 100
```

**Observed on Hoopoe, Java 17, with a 512 KB thread stack:**
constructing 100,000 binds succeeds without invoking any binder;
executing that chain throws `StackOverflowError` (the probe catches
and reports it). A chain of 100 binds evaluates normally.
The overflow threshold varies with JVM version, thread-stack size
and lambda shape. No depth ceiling or guaranteed safe depth is
promised beyond normal JVM behavior.

For 100,000 sequential operations, use an **ordinary iterative
loop** instead of recursively nested Readers. A separately
designed Trampoline (#4) can support explicitly deferred recursive
computations. If practical Reader workloads demand deep stack-safe
composition, a future iteration can evaluate a defunctionalized
Reader representation with an explicit continuation stack; it
should be reviewed on its type-safety and allocation merits rather
than introduced silently.

This limitation differs from **Writer**: Writer's current
`flatMap` performs its work eagerly, so a loop can make 120,000
successive binds without building a deferred call chain, as long
as its output carrier (such as DList) is stack-safe.

## Decision

**Implement** `Reader<R,A>` as a small Java functional interface
with the six requested operations plus `pure`, using ordinary
`Function` callbacks and explicit environments.
**Defer** defunctionalized/interpreted Reader evaluation until
deep composition is a real use case, and investigate it alongside
#4 (Trampoline).
**Drop** hidden thread-locals, implicit dependency injection,
a mandatory Reader monad typeclass hierarchy, and any claim
that arbitrary user lambdas are guaranteed pure.

Related: #3 (Reader/Writer/State), #4 (Trampoline), #15 (ReaderT).
