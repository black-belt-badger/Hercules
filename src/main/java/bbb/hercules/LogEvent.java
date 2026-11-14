package bbb.hercules;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import lombok.NonNull;

/**
 * Immutable description of one structured diagnostic event, not an I/O action.
 *
 * <p>The producer provides the timestamp and semantic metadata explicitly.
 * Creating or aggregating events never reads a clock, prints, opens files,
 * emits to a logger or schedules work. Field map membership is shallowly
 * snapshotted on construction; {@link LogValue} payloads are immutable.
 *
 * <p>Raw fields remain accessible to the producer. Do not send an unredacted
 * event to a third-party sink or serialize it without applying
 * {@link #redacted()} first. The default {@link #toString()} is safe
 * and uses the redacting {@link LogFormatter}.
 *
 * <p>Source, name, field keys and correlation identifiers are structural
 * metadata, not classified field values. Keep personal information and
 * secrets out of those fields. UUID correlation and causation identifiers
 * are intentionally represented separately from free-text payloads.
 *
 * @param level severity level
 * @param name stable semantic event name (e.g. {@code order.validated})
 * @param occurredAt caller-supplied time; no implicit clock read
 * @param source stable producer/component identifier
 * @param correlationId optional correlation identifier
 * @param causationId optional causation identifier
 * @param fields immutable, named and sensitivity-classified scalar fields
 */
public record LogEvent(
        @NonNull Level level,
        @NonNull String name,
        @NonNull Instant occurredAt,
        @NonNull String source,
        @NonNull Optional<UUID> correlationId,
        @NonNull Optional<UUID> causationId,
        @NonNull Map<String, LogField> fields) {

    /** Ordered diagnostic severity, without backend-specific semantics. */
    public enum Level {
        /** Verbose development detail. */
        TRACE,
        /** Debugging detail. */
        DEBUG,
        /** Ordinary operational information. */
        INFO,
        /** A suspicious or degraded operation. */
        WARN,
        /** A failed operation. */
        ERROR
    }

    /**
     * Snapshot fields and validate metadata without reading external state.
     *
     * @param level diagnostic severity
     * @param name stable semantic event name
     * @param occurredAt explicit timestamp
     * @param source component identifier
     * @param correlationId optional correlation UUID
     * @param causationId optional causation UUID
     * @param fields immutable snapshot of classified values
     */
    public LogEvent {
        requireToken("name", name);
        requireToken("source", source);
        Objects.requireNonNull(fields, "fields");
        TreeMap<String, LogField> snapshot = new TreeMap<>();
        for (Map.Entry<String, LogField> entry : fields.entrySet()) {
            requireToken("field name", entry.getKey());
            snapshot.put(entry.getKey(), Objects.requireNonNull(
                    entry.getValue(), "null field"));
        }
        fields = Collections.unmodifiableMap(snapshot);
    }

    private static void requireToken(String role, String value) {
        Objects.requireNonNull(value, role);
        if (value.isEmpty() || !asciiLetter(value.charAt(0))) {
            throw new IllegalArgumentException(role + " must be a stable nonblank token");
        }
        for (int index = 1; index < value.length(); index++) {
            char letter = value.charAt(index);
            if (!asciiLetter(letter) && !(letter >= '0' && letter <= '9')
                    && letter != '_' && letter != '.' && letter != '-') {
                throw new IllegalArgumentException(
                        role + " must be a stable nonblank token");
            }
        }
    }

    private static boolean asciiLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /**
     * Return a safe immutable snapshot with sensitive values replaced.
     *
     * <p>No caller-supplied backend can access raw field values if it
     * receives this result exclusively. Raw source event remains unchanged.
     *
     * @return redacted output event
     */
    public LogEvent redacted() {
        TreeMap<String, LogField> sanitized = new TreeMap<>();
        fields.forEach((key, field) -> sanitized.put(key, field.redacted()));
        return new LogEvent(level, name, occurredAt, source, correlationId, causationId, sanitized);
    }

    /**
     * Safe default diagnostic formatting, independent of map insertion order.
     *
     * @return escaped, redacted and deterministically ordered representation
     */
    @Override
    public String toString() {
        return LogFormatter.format(this);
    }
}
