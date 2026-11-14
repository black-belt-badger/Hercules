package bbb.hercules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.NonNull;

/**
 * Explicit, synchronous boundary for interpreting structured log data.
 *
 * <p>Accepts a caller-selected backend as a JDK {@link Consumer}; this
 * makes a test collector, SLF4J logger adapter or any other implementation
 * possible without imposing a logging dependency. Only redacted,
 * immutable event snapshots reach the backend. The interpreter performs
 * no logging unless explicitly invoked by the caller.
 *
 * <p>Source iteration is bounded by {@link LogPolicy#maxInspected()}.
 * Sampling is deterministic in encounter order. Sink invocation is
 * synchronous, so a slow sink applies caller-thread backpressure and no
 * unbounded queue is created. The interpreter never promises delivery:
 * an accepted callback may buffer or discard its event.
 *
 * <p>No I/O is performed by {@link #collect(Iterable, LogPolicy)}
 * or {@link #silent(Iterable, LogPolicy)}. These APIs are still
 * effect boundaries because collection allocates and the interpreter
 * executes the supplied iteration; a pure producer need not invoke them.
 */
public final class LogInterpreter {

    private LogInterpreter() {
    }

    /**
     * A bounded execution summary; acceptance by a sink is not guaranteed
     * durable delivery.
     *
     * @param inspected source events consumed
     * @param offered events selected by sampling and passed to a sink
     * @param accepted sink calls returning normally
     * @param failures sink calls throwing a RuntimeException in continue mode
     * @param truncated true if more source events remained at the bound
     */
    public record Report(int inspected, int offered, int accepted,
                         int failures, boolean truncated) {
    }

    /**
     * In-memory bounded test collection with immutable, redacted events.
     *
     * @param events redacted events accepted by the collector
     * @param report execution summary
     */
    public record Collected(@NonNull List<LogEvent> events, @NonNull Report report) {
        /**
         * Defensively copy the collected events.
         *
         * @param events safely redacted events
         * @param report bounded interpretation summary
         */
        public Collected {
            events = List.copyOf(events);
        }
    }

    /**
     * Interpret and emit a bounded sample through a caller-selected sink.
     *
     * <p>The sink receives only {@link LogEvent#redacted()} values. Sink
     * RuntimeExceptions follow the configured failure policy and are
     * never converted into validation errors. Iterator exceptions
     * propagate. The interpreter does not catch {@link Error} or retry.
     * An exception in propagate mode aborts without a report.
     *
     * @param input finite or infinite iterable of pure event descriptions
     * @param sink explicitly supplied synchronous backend
     * @param policy bounds, sampling and sink failure semantics
     * @return counts and indication of truncation, not durable delivery
     */
    public static Report emit(
            @NonNull Iterable<? extends LogEvent> input,
            @NonNull Consumer<? super LogEvent> sink,
            @NonNull LogPolicy policy) {
        Iterator<? extends LogEvent> cursor =
                Objects.requireNonNull(input.iterator(), "null event iterator");
        int inspected = 0;
        int offered = 0;
        int accepted = 0;
        int failures = 0;

        while (inspected < policy.maxInspected() && cursor.hasNext()) {
            LogEvent event = Objects.requireNonNull(cursor.next(), "null LogEvent");
            int index = inspected++;
            if (index % policy.sampleEvery() != 0) {
                continue;
            }
            offered++;
            LogEvent safe = event.redacted();
            try {
                sink.accept(safe);
                accepted++;
            } catch (RuntimeException exception) {
                failures++;
                if (policy.failureMode() == LogPolicy.FailureMode.PROPAGATE) {
                    throw exception;
                }
            }
        }

        return new Report(inspected, offered, accepted, failures,
                inspected == policy.maxInspected() && cursor.hasNext());
    }

    /**
     * Collect at most the selected sample of {@code maxInspected} events.
     * The returned collection is a structurally immutable defensive copy
     * containing redacted fields only.
     *
     * @param input source events
     * @param policy bounds and sampling
     * @return collected events and policy report
     */
    public static Collected collect(
            @NonNull Iterable<? extends LogEvent> input,
            @NonNull LogPolicy policy) {
        ArrayList<LogEvent> output = new ArrayList<>();
        Report report = emit(input, output::add, policy);
        return new Collected(output, report);
    }

    /**
     * Run a bounded, silent interpretation without storing or emitting
     * any events. Accepted counts represent successful no-op handling,
     * not actual logging or delivery.
     *
     * @param input source events
     * @param policy bounds and sampling
     * @return summary of the ignored events
     */
    public static Report silent(
            @NonNull Iterable<? extends LogEvent> input,
            @NonNull LogPolicy policy) {
        Iterator<? extends LogEvent> cursor =
                Objects.requireNonNull(input.iterator(), "null event iterator");
        int inspected = 0;
        int handled = 0;
        while (inspected < policy.maxInspected() && cursor.hasNext()) {
            Objects.requireNonNull(cursor.next(), "null LogEvent");
            if (inspected++ % policy.sampleEvery() == 0) {
                handled++;
            }
        }
        // No sink exists in silent mode: do not allocate redacted copies.
        return new Report(inspected, handled, handled, 0,
                inspected == policy.maxInspected() && cursor.hasNext());
    }
}
