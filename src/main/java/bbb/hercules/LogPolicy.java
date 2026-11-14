package bbb.hercules;

import lombok.NonNull;

/**
 * Bounded, deterministic policy for interpreting pure logging descriptions.
 *
 * <p>At most {@code maxInspected} source events are read from the supplied
 * iterable. Sampling selects zero-based positions divisible by
 * {@code sampleEvery}, so the first event is always selected.
 * No data is buffered unless the caller explicitly requests collection.
 * The policy intentionally never starts asynchronous work.
 *
 * <p>For errors from the chosen sink, {@link FailureMode#PROPAGATE}
 * throws immediately, while {@link FailureMode#COUNT_AND_CONTINUE}
 * explicitly counts the failure and proceeds. Sink failures are never
 * misrepresented as successful deliveries.
 *
 * @param maxInspected positive maximum number of events to inspect
 * @param sampleEvery positive deterministic sampling interval
 * @param failureMode behavior when the caller-selected sink throws
 */
public record LogPolicy(int maxInspected, int sampleEvery,
                        @NonNull FailureMode failureMode) {

    /** How to handle runtime failures thrown by the output sink. */
    public enum FailureMode {
        /** Propagate the original sink exception without wrapping. */
        PROPAGATE,
        /** Record a failed attempt and continue with remaining events. */
        COUNT_AND_CONTINUE
    }

    /**
     * Validate positive inspection/sampling bounds on construction.
     *
     * @param maxInspected maximum number of events inspected
     * @param sampleEvery sampling interval in inspected source positions
     * @param failureMode sink exception handling
     */
    public LogPolicy {
        if (maxInspected <= 0 || sampleEvery <= 0) {
            throw new IllegalArgumentException("maxInspected and sampleEvery must be positive");
        }
    }

    /**
     * Strict bounded policy: no sampling, fail fast on sink errors.
     *
     * @param maxInspected maximum source events inspected
     * @return strict policy
     */
    public static LogPolicy strict(int maxInspected) {
        return new LogPolicy(maxInspected, 1, FailureMode.PROPAGATE);
    }
}
