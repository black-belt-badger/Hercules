# State<S,A> — explicit immutable state transitions

`State<S,A>` represents `S -> (S,A)`: a computation that takes
a state snapshot and returns the **next state** together with a
computed **value**. It is an ordinary Java 17 functional interface;
it does not have a mutable state cell, a hidden singleton, or a
thread-local context.

This is the 3C increment of [#3](https://github.com/black-belt-badger/hercules/issues/3),
alongside the already implemented Reader and Writer. State is a
**description** of a transition that can be evaluated with an
explicit input. It does not execute database writes, submit orders
or perform network I/O.

## API and result ordering

`State.Result<S,A>` is a Java record with `state()` and
`value()` **in that order**. The first field is the state
after evaluation; the second is the computed value.

| Operation | Behavior |
| --- | --- |
| `run(initial)` | Evaluate with explicit input; returns `Result<S,A>` |
| `eval(initial)` | Evaluate once; return only the computed value |
| `exec(initial)` | Evaluate once; return only the next state |
| `map(f)` | Lazily transform value while preserving the next state |
| `flatMap(f)` | Lazily build a dependent State; thread **updated state** |
| `get()` | Read current state as both unchanged state and result value |
| `put(replacement)` | Replace state, producing `State.Unit.INSTANCE` |
| `modify(updater)` | Lazily transform state once, producing `State.Unit.INSTANCE` |
| `gets(selector)` | Lazily project a value from state, leaving state unchanged |
| `pure(value)` | Constant value, preserving the incoming state |

`State.Unit` is a nested one-value enum for operations with no
interesting return value. Unlike `null`, it provides an explicit,
non-null result of `put` or `modify`; no new general-purpose Unit
dependency or typeclass framework is introduced.

## Example: advancing an immutable market-data cursor

```java
record Cursor(long nextOffset, int processed) {
    Cursor advance() {
        return new Cursor(nextOffset + 1, processed + 1);
    }
}

State<Cursor, Long> takeOffset = State.<Cursor>get()
        .flatMap(before -> State.<Cursor>modify(Cursor::advance)
                .map(unit -> before.nextOffset()));

State<Cursor, List<Long>> twoOffsets = takeOffset.flatMap(first ->
        takeOffset.map(second -> List.of(first, second)));

Cursor initial = new Cursor(100, 0);
State.Result<Cursor, List<Long>> result = twoOffsets.run(initial);
// result.state(): Cursor(102, 2)
// result.value(): [100, 101]
// initial: Cursor(100, 0), unchanged
```

The **second** `takeOffset` sees cursor offset 101, rather
than receiving the original offset 100. That is the essential
difference between State's dependent binding and Reader's:
Reader passes one **shared, unchanged environment** to its
dependent steps; State passes the **newly produced state**.

This example performs no real downloads or trades. The state is
data, and interpreting it against an external system is a
separate responsibility. `StatePublicApiTest` exercises this
scenario from an independent consumer package.

## Evaluation, ordering and nullness

Creating a State and invoking `map` or `flatMap` is **lazy**.
The user-supplied functions are called only when the resulting
State is evaluated. During a single `flatMap` evaluation:

1. Evaluate the first State against the incoming state.
2. Extract its computed value and **updated state**.
3. Invoke the binder exactly once with that value, obtaining a
   dependent State.
4. Run that State once with the updated state, returning its result.

A callback is not scheduled, run in parallel, retried or cancelled
by Hercules. Results and callbacks are evaluated in deterministic
program order for pure inputs. Java can of course execute code
with side effects from user lambdas, but the library never
introduces a side-effecting interpreter.

Required callback **references** are checked eagerly with Lombok
`@NonNull`. A null structural `State.Result` or binder-returned
`State` is rejected when the computation is evaluated, rather
than encoded as a value or domain error. Exceptions thrown by user
functions propagate unchanged.

The JSpecify `@NullMarked` package is non-null by default.
Generic payloads and state can be **explicitly nullable** by
annotating the type use:

```java
State<@Nullable String, @Nullable Integer> length =
        State.gets(s -> s == null ? null : s.length());

assert length.run(null).equals(new State.Result<>(null, null));
```

A nullable **state payload** is different from a null
`State.Result` container, which is always a structural error.
The nested Unit value prevents the `put`/`modify` no-result
case from being represented by a null.

State records are **shallowly** immutable. The output record
does not deep-copy its state or value. Callers should model state
with immutable records and persistent collections. A callback
that mutates a supplied `ArrayList`, for example, can still
violate isolation and referential transparency.

## Algebra: functor and monad laws

Write `~=` for equality of `State.Result` values when both
computations are run under the same initial state. For pure
callbacks and normally completing evaluations:

```text
Functor identity:
    m.map(identity)                         ~= m

Functor composition:
    m.map(f).map(g)                         ~= m.map(f.andThen(g))

Monad left identity:
    State.pure(a).flatMap(f)                ~= f(a)

Monad right identity:
    m.flatMap(State::pure)                  ~= m

Monad associativity:
    m.flatMap(f).flatMap(g)                  ~=
    m.flatMap(a -> f(a).flatMap(g))
```

The laws require **no output combiner or monoid identity**.
Only Writer requires those. State instead carries the next state
explicitly into the dependent computation. Monad associativity
is the same ordering of transitions under different grouping.

`StateLawsTest` verifies these laws with Instancio-generated
values and multiple initial states. It covers transition order,
independent re-evaluation, `get`/`put`/`modify`/`gets`,
lazy callback evaluation, eager null-reference contracts,
structural null results, exceptions and explicitly nullable
state/value specializations.

Java does **not** enforce purity: a lambda may log, mutate
input state, read a clock or throw. Law checks cannot prove
referential transparency of arbitrary user code. Hercules only
supplies an API that makes pure state transformations convenient.

## JVM stack depth and Trampoline

The simple Java functional-interface implementation is a chain
of nested lambdas. Composition is **lazy and stack-safe to
construct**, but running a sufficiently deep chain of nested
`flatMap` evaluations consumes one Java stack frame per step.
This is the same limitation measured for Reader.

The intentionally non-JUnit probe is reproducible:

```sh
./mvnw -B -ntp test-compile
java -Xss512k -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.StateDepthProbe 100000
java -Xss512k -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.StateDepthProbe 100
```

**Observed on Hoopoe, Java 17, with 512 KB thread stack:**
constructing 100,000 chained State binds succeeds without calling
any binders, but `run(0)` overflows the stack. A chain with 100
binds runs normally and returns state `0`, value `100`.

The safe depth depends on the JVM, stack size and lambda shapes;
no fixed depth guarantee is claimed. A loop that constructs and
**executes independent, small transitions sequentially**, carrying
the state explicitly between iterations, can process arbitrarily
many steps without nested lambda calls. For stack-safe recursive
evaluation, consider the separately scoped **Trampoline #4**;
a fully defunctionalized State interpreter should be justified
and reviewed separately rather than added with unchecked casts.

## Choosing between Reader, Writer and State

- **Reader**: many dependent computations share the same
  read-only environment, such as configuration.
- **Writer**: combine a computed value with ordered immutable
  output data; requires a caller-supplied associative output
  combination and identity, and performs no logging I/O.
- **State**: dependent steps can transform explicit state;
  each new step sees the updated state from the prior step.

All three are opt-in, small Java APIs. None manages actual
external processes, network connections or a hidden state
runtime. Use `Either` or `Validation` when a computation
also needs explicit error semantics.

**Decision:** implement the direct Java functional interface,
a nested immutable result record and a nested Unit enum;
defer interpreted stack-safe State until an actual workload
justifies its complexity. Do not introduce a generic HKT
framework or mandatory runtime.

Related: #3, #4 (Trampoline), #15 (StateT), #18
(typed actors and effects).
