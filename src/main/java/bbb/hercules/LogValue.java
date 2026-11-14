package bbb.hercules;

import java.math.BigDecimal;

import lombok.NonNull;

/**
 * Closed, immutable scalar values for structured logging fields.
 *
 * <p>No arbitrary user objects or mutable collections are accepted.
 * Convert a domain value to an explicitly chosen scalar at the pure
 * producer boundary. Strings may contain secrets; classification through
 * {@link LogField} determines whether an interpreter may emit them.
 *
 * <p>Text's diagnostic {@code toString} intentionally hides content so
 * accidental record debugging cannot display plaintext values. Use
 * {@link LogFormatter} at an interpreted boundary for redacted output.
 */
public sealed interface LogValue
        permits LogValue.Text, LogValue.Count, LogValue.Decimal, LogValue.Flag {

    /**
     * Immutable textual scalar. The actual value remains accessible to the
     * producer and is not itself encrypted or scrubbed from memory.
     *
     * @param value text payload
     */
    record Text(@NonNull String value) implements LogValue {
        @Override
        public String toString() {
            return "Text[content omitted]";
        }
    }

    /** An exact integer scalar. @param value integer value */
    record Count(long value) implements LogValue {
    }

    /** An exact decimal scalar. @param value decimal value */
    record Decimal(@NonNull BigDecimal value) implements LogValue {
    }

    /** A Boolean scalar. @param value flag value */
    record Flag(boolean value) implements LogValue {
    }
}
