# DList — persistent difference-list concatenation

`DList<A>` is an immutable sequence optimized for repeatedly joining
persistent sequence fragments before materializing a final JDK `List<A>`.
It is useful for accumulating diagnostic/log-event **data**, rendering
fragments in a pretty printer, or joining immutable tree-traversal results.

```java
DList<String> first = DList.fromList(List.of("start", "parsed"));
DList<String> second = DList.singleton("validated").snoc("finished");

DList<String> combined = first.append(second);
List<String> ordered = combined.toList();
// ["start", "parsed", "validated", "finished"]

// Neither input was changed:
assert first.toList().equals(List.of("start", "parsed"));
```

The public API is deliberately small:

| Operation | Cost | Behavior |
| --- | --- | --- |
| `empty()` | O(1) | An empty sequence; identity for append |
| `singleton(a)` | O(1) | Exactly one element |
| `fromList(list)` | O(n) | Shallow defensive copy of the supplied list |
| `cons(a)` | O(1) | Prefix an element |
| `snoc(a)` | O(1) | Suffix an element |
| `append(next)` | O(1) | Persistent concatenation, preserving both operands |
| `toList()` | O(nodes + elements) | Iterative flattening into a new unmodifiable shallow snapshot |
| `equals`, `hashCode`, `toString` | O(nodes + elements) | Value-oriented behavior; no structural tree equality |

The JSpecify package-level `@NullMarked` applies. Input collection
and other DList references must not be null; Lombok enforces those
structural checks at runtime. Explicitly nullable element types are
permitted, and `null` elements are **preserved**, not removed:

```java
DList<@Nullable String> values =
        DList.<@Nullable String>singleton(null).snoc("present");
assert values.toList().equals(Arrays.asList(null, "present"));
```

No method exposes mutable structural nodes. `fromList` snapshots the
input container; `toList` returns a fresh, unmodifiable list. Payload
objects are **not** deeply cloned; a mutable element can still be mutated
by its owner. `DList` also does not cache its flattened form, so repeated
`toList`/value comparison calls pay the flattening cost each time.

## Why not `Function<List<A>, List<A>>`?

A traditional difference list can be represented by an endomorphism,
with append as composition:

```text
empty           = identity
singleton(a)    = tail -> a :: tail
append(x, y)    = x ∘ y
toList(x)       = x([])
```

Although function composition makes `append` constant-time, a Java
chain such as `f = f.andThen(next)` builds deeply nested function calls.
Materializing a sequence formed by 100,000 successive compositions can
exhaust the JVM thread stack: call-stack depth grows with the number of
appends. Lazy functional composition also does not automatically make
construction or evaluation stack-safe in Java.

Hercules uses **defunctionalization**: instead of executing a nested
chain of function values, it stores an immutable tree with `Empty`,
`One`, `Chunk` and `Join` nodes. Each node represents a sequence
operation as *data*. `append` constructs only one join node, without
copying either operand.

At materialization time, `toList` uses an explicit `ArrayDeque` of
nodes. It pushes the right branch before the left branch, preserving
encounter order and avoiding recursion. An `ArrayList` buffer is used
**only locally** for the final output, then wrapped as an unmodifiable
snapshot. This internal mutation does not alter any DList and cannot be
observed by an API caller.

The same traversal handles deep **left-associated** and **right-associated**
trees, as well as `cons` and `snoc` chains. JUnit tests materialize
120,000-element left/right append chains and 110,000-element
`cons`/`snoc` chains; no recursion occurs during flattening.

## Algebra

The identity and associativity laws hold by **element-sequence equality**:

```text
empty.append(xs) == xs
xs.append(empty) == xs

xs.append(ys).append(zs) == xs.append(ys.append(zs))

fromList(xs).toList() == xs (by value)
xs.cons(a).toList() == [a] ++ xs.toList()
xs.snoc(a).toList() == xs.toList() ++ [a]
```

`equals` compares materialized sequences, not node identity, and the
`hashCode` agrees with equal sequences. This means append grouping
is unobservable in the values returned by `toList`; it does **not**
mean that the internal tree shapes or allocations are identical.

`DListLawsTest` uses Instancio-generated data to exercise the laws and
compares mixed `cons`/`snoc`/`append`/`fromList` construction to
an ordinary `ArrayList` oracle. It also tests nullable payloads,
persistent forks, source-list mutation, independent immutable output
snapshots and deep stack-safe materialization.

## Real use: diagnostics as data

A pure validation step can return `DList<Diagnostic>` without writing
to a log. Separate steps can append diagnostics in order, and a caller
can later choose whether to present them in a UI, store them, or send
them to a logging system:

```java
record Diagnostic(String source, String message) {}

DList<Diagnostic> first = DList.singleton(
        new Diagnostic("parser", "read input"));
DList<Diagnostic> second = DList.singleton(
        new Diagnostic("validator", "quantity must be positive"));
DList<Diagnostic> events = first.append(second);

List<Diagnostic> logData = events.toList();
// Interpret logData at an explicit boundary, not inside pure computation.
```

`DListPublicApiTest` demonstrates multiple validation stages
accumulating diagnostic records, with no logging I/O, from a separate
consumer package. This is a building block for a future Writer API,
not a general effect, logging or tracing framework by itself.

## Measured performance and allocations

To reproduce the **manual, non-JUnit** benchmark with the JDK's
thread-allocation counters where available:

```sh
./mvnw -B -ntp test-compile
java -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.DListBenchmark 100000
```

The benchmark distinguishes **construction** from **final freezing**.
Every strategy builds the same integer values in order and ends with
an unmodifiable JDK `List`. It reports elapsed time and bytes
allocated on its current thread; the counters include boxing.
It exercises four alternatives:

- `DList`: persistent O(1) `snoc`, then iterative `toList`.
- `ArrayList`: mutable appends, followed by one defensive unmodifiable copy.
- `Stream.Builder`: mutable builder appends, then `build().toList()`.
- Immutable list copying: copy the *entire* previous list for each
  append, wrapping each new list; O(n²), so capped at 3,000 elements.

Illustrative **single local run** on Hoopoe using Java 25 (times in
milliseconds, memory in MB of allocated bytes; rounded):

| Strategy | Elements | Build ms | Freeze ms | Build MB | Freeze MB |
| --- | ---: | ---: | ---: | ---: | ---: |
| DList | 100,000 | 4.677 | 12.893 | 7.20 | 2.59 |
| ArrayList | 100,000 | 4.127 | 0.039 | 2.88 | 0.40 |
| Stream.Builder | 100,000 | 4.749 | 2.554 | 2.12 | 0.40 |
| DList | 3,000 | 0.066 | 0.445 | 0.21 | 0.10 |
| ArrayList | 3,000 | 0.041 | 0.006 | 0.10 | 0.01 |
| Stream.Builder | 3,000 | 0.043 | 0.049 | 0.06 | 0.01 |
| Copy entire immutable list each append | 3,000 | 12.598 | ~0 | 63.33 | ~0 |

These numbers are **not performance guarantees or rigorous JMH
measurements**. One-run timings vary with JVM warmup, garbage collection
and workstation load. The important structural observation is that
copy-on-every-append performs quadratic copying, while the DList
approach adds cheap structural nodes and pays a single final flattening
cost. The mutation-permitted `ArrayList` or `Stream.Builder` is the
better default for a single local owner. DList becomes interesting when
intermediate sequences need to remain persistent, can be shared across
branches, or are assembled from numerous immutable fragments.

## Decision

**Implement** this private-node, stack-safe `DList`, with JDK
`List` at the API boundary. **Drop** deeply composed Java-function
closures as the public backing representation because they risk
`StackOverflowError`. **Defer** special-purpose chunk-balancing,
cached lengths, parallel flattening, and Guava-specific collection
overloads until concrete workloads justify them. No new dependencies
or generic FP typeclasses are required.

Related: #17 (this API), #1 (pure interpreted logging), #3 (Writer),
#4 (trampoline) and #14 (tree morphisms).
