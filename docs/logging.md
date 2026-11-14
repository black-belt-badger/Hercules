# Structured logging as an explicitly interpreted effect

Hercules uses **data first, effects at a chosen boundary**. A pure
producer creates immutable **LogEvent** descriptions; a caller
explicitly invokes **LogInterpreter** to collect, ignore or emit them.
There is no global logger, implicit I/O, hidden clock or required
SLF4J dependency.

This implements [#1](https://github.com/black-belt-badger/hercules/issues/1).
Log descriptions are ordinary values, not network messages, durable
database records or guaranteed delivered events.

## Event model

A LogEvent contains severity, a stable semantic name, a caller-supplied
Instant, a source/component identifier, optional UUID correlation and
causation IDs, and a snapshotted map of typed, classified fields.

| Type | Responsibility |
| --- | --- |
| **LogEvent** | Pure description; TRACE/DEBUG/INFO/WARN/ERROR severity |
| **LogField** | PUBLIC, PERSONAL, SECRET classification per field |
| **LogValue** | Closed immutable scalars: Text, Count, Decimal, Flag |
| **LogFormatter** | Deterministic, single-line, escaped, **always redacted** |
| **LogPolicy** | maxInspected, sampleEvery and sink failure policy |
| **LogInterpreter** | Caller-selected emission, bounded collection and silence |

Example (all types come from bbb.hercules):

~~~java
Instant at = Instant.parse("2026-11-14T08:00:00Z");
LogEvent event = new LogEvent(
        LogEvent.Level.INFO, "order.validated", at, "OrderValidator",
        Optional.of(UUID.fromString("12345678-1234-1234-1234-123456789abc")),
        Optional.empty(),
        Map.of(
                "symbol", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Text("BTCUSD")),
                "quantity", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Count(2)),
                "customer", new LogField(LogField.Visibility.PERSONAL,
                        new LogValue.Text("private customer"))));

String safeText = LogFormatter.format(event);
// ... field.customer="[REDACTED]" ...
~~~

No logger was invoked. The timestamp is **injected by the caller**
instead of read from a system clock, making equality and formatting
deterministic. UUID correlation/causation identifiers are typed separately
from free-form payloads.

Event names, sources and field names must be stable ASCII tokens
(letters, digits, underscores, dots and hyphens; first character a
letter). These are **metadata and not redaction targets**: never put
personal data or secrets in them.

The field map is copied defensively on construction and retains a
deterministic lexical order. Arbitrary objects and mutable payload
collections are not permitted as LogValue. Domain values must be
converted into one of the approved immutable scalar variants.

## Redaction, boundaries and PII

On emission, both PERSONAL and SECRET payloads are replaced with
the fixed marker **[REDACTED]** *before* any caller-selected backend
receives the event. LogFormatter and LogEvent.toString are also
redacted by default; LogField.toString and LogValue.Text.toString
do not display the raw sensitive text.

The producer-owned original event **still holds the raw values in
memory**, accessible through its field map. This is intentional:
classification and redaction happen at the interpretation boundary.
Never serialize raw fields or send the original event to a third-party
backend. **Only use events handed to the interpreter's sink**, which
are sanitized copies. The bounded collection API also returns
redacted copies.

PUBLIC data is rendered as supplied. If a credential is classified
PUBLIC, the library cannot infer that mistake. Consuming projects
should approve public field names/values and classify all sensitive
values explicitly. Do not stuff arbitrary exception messages into
PUBLIC text fields.

Text rendering escapes quotes, backslashes, newline characters,
tabs, control codes and Unicode line separators to prevent
multi-line log injection. Fields render in lexicographic key order.
The same data renders identically regardless of map insertion order.
It is not encryption, process-memory scrubbing or general PII detection.

## Interpreter choices

For tests and pure-data inspection:

~~~java
LogInterpreter.Collected collected = LogInterpreter.collect(
        List.of(event), LogPolicy.strict(100));

List<LogEvent> safeEvents = collected.events();
// Immutable redacted list, at most 100 source events inspected
~~~

To explicitly perform output, supply a standard JDK Consumer:

~~~java
LogInterpreter.Report report = LogInterpreter.emit(
        List.of(event),
        safeEvent -> myBackend.accept(LogFormatter.format(safeEvent)),
        LogPolicy.strict(100));
~~~

Here myBackend is a backend supplied by the *application*. Hercules
never opens a file, selects a logger or launches an executor.

For an application with SLF4J, the adapter remains **in the application**.
Illustrative code assumes it already has an org.slf4j.Logger named logger
and a source Iterable named events:

~~~java
LogInterpreter.emit(events, safe -> {
    String message = LogFormatter.format(safe);
    switch (safe.level()) {
        case TRACE -> logger.trace("{}", message);
        case DEBUG -> logger.debug("{}", message);
        case INFO -> logger.info("{}", message);
        case WARN -> logger.warn("{}", message);
        case ERROR -> logger.error("{}", message);
    }
}, LogPolicy.strict(1_000));
~~~

This supports SLF4J-style emission without making SLF4J a mandatory
dependency. A backend may instead translate safe typed fields into
its native structured protocol.

**LogInterpreter.silent** inspects only the configured number of
events, counts sampled positions and discards everything; it invokes
no backend and does not allocate redacted copies.

## Reader, Writer, State and Free/Freer composition

Writer with DList can accumulate pure log descriptions efficiently:

~~~java
WriterPolicy<DList<LogEvent>> policy =
        new WriterPolicy<>(DList.empty(), DList::append);

Writer<DList<LogEvent>, Integer> first =
        new Writer<>(2, DList.singleton(event));

Writer<DList<LogEvent>, Integer> second = first.flatMap(
        number -> new Writer<>(number + 1, DList.singleton(nextEvent)),
        policy);

LogInterpreter.emit(second.output().toList(), chosenBackend,
        LogPolicy.strict(100));
~~~

The nextEvent and chosenBackend values are caller-provided.
Effects happen only on the final line. A Reader can supply a
logging context (clock value, correlation UUID and component).
A State transition can produce a new immutable cursor together
with a LogEvent description. **LogEffectsPublicApiTest** demonstrates
Reader, Writer and State as independent, compatible pieces.

Free/Freer programs may also *describe* LogEvent instructions and
delegate the actual I/O to a separate interpreter. That integration
is deliberately not required by this API; Free/Freer is the
separately scoped #12.

**Buffering caution:** DList append is persistent and O(1), but
DList.toList materializes the **entire accumulated history** before
passing it to this interpreter. Use bounded batches. For huge or
infinite logs, supply an Iterable with an explicit production
budget rather than accumulating everything in Writer.

## Bounds, sampling, backpressure, errors

**LogPolicy(maxInspected, sampleEvery, failureMode)** accepts only
positive bounds. Sampling is by zero-based input position: with
sampleEvery = 3, positions 0, 3, 6, 9 ... are emitted in encounter
order. The first event is selected.

At most **maxInspected source events** are consumed, including
from infinite iterables. Report.truncated indicates that more
elements were visible after reaching this bound. Collection uses
at most the sampled portion of that bound and produces a shallowly
immutable list of redacted events. Silent execution retains none.

The interpreter is **synchronous on the caller thread**. A slow
backend blocks the producer; there is no hidden queue, managed
background worker, reactive streams contract or unbounded buffering.
The underlying iterator may block or throw: those errors propagate.
The final hasNext check at the bound is used to determine truncation.

Interpretation returns a **Report** with inspected, offered,
accepted, failures and truncated. Accepted means that the sink
callback *returned normally*; it does **not** mean delivered,
persisted, flushed or acknowledged.

~~~java
LogPolicy policy = new LogPolicy(
        1_000, 10, LogPolicy.FailureMode.COUNT_AND_CONTINUE);
LogInterpreter.Report result = LogInterpreter.emit(events, sink, policy);
~~~

Failure policies:

- **PROPAGATE:** stop immediately and rethrow the original
  RuntimeException from the sink, without returning a partial report
- **COUNT_AND_CONTINUE:** count the sink failure without incrementing
  accepted, then attempt subsequent events; may intentionally lose logs

The interpreter never catches Error, does not retry and does
not convert backend exceptions to domain-level Either or Validation
errors. Iterator failures propagate in either mode. Silent's accepted
count means successful no-op handling, **not actual emission**.

## Measured overhead and reproducible benchmark

An optional manual benchmark uses JDK thread-allocation counters:

~~~sh
./mvnw -B -ntp test-compile
java -cp target/classes:target/test-classes \
  bbb.hercules.benchmark.LogEffectsBenchmark 100000
~~~

It distinguishes event construction, a direct loop (no log), silent
interpretation, redacted formatted emission and sampled collection.

Single **illustrative Java 25 run on Hoopoe** at 100,000 source events,
after replacing regex compilation in the event construction path
with a small allocation-free metadata-token check:

| Operation | Milliseconds | Allocated bytes |
| --- | ---: | ---: |
| Construct 100,000 typed immutable events | 68.12 | 62,008,272 |
| Direct loop, no logging | 12.42 | 72 |
| Bounded silent interpreter | 8.55 | 128 |
| Redact and format all 100,000 events | 127.12 | 108,829,616 |
| Collect one event in ten | 7.76 | 5,279,000 |

These are **single-run diagnostic figures**, not statistically
rigorous JMH numbers or a promise of relative performance. The
workloads differ and are not normalized to identical work. JIT
warmup, CPU load, heap and GC change these values. The general
tradeoff is clear: typed allocations and safe redaction cost
memory and CPU, so bound and sample before emitting, and avoid
constructing unneeded events in extremely hot paths.

## Operational diagnostics are not a durable audit ledger

Log output can be sampled, filtered, silently ignored, lost in a
failed backend, or lost during a process crash. This makes it
inappropriate as a required financial/trading audit trail.

**Domain and audit events require different guarantees**: stable
versioned schemas, transactional persistence/outbox semantics,
retention, authorization, idempotent replay and explicit delivery
acknowledgement. LogInterpreter does not implement any of these
and must not be treated as an exactly-once event bus.

## Design decisions

**Implement:** pure immutable structured event descriptions,
explicit classified typed scalars, safe formatting, bounded test
collection, silent interpretation, selected synchronous emission,
deterministic sampling and explicit sink exception policy.

**Defer:** provider-specific adapters, managed async queues,
retries, retention policies, dynamic level filtering and
Free/Freer instruction interpreters until separately justified.

**Drop from the core:** global side-effecting logger callbacks,
unbounded buffers, hidden clock reads, implicit logging backends
and any claim that diagnostic logs are durable domain/audit events.

Related: #3 (Reader/Writer/State), #4 (Trampoline), #12 (Free/Freer),
#18 (typed actors).
