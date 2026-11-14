package bbb.hercules;

import lombok.NonNull;

/**
 * Deterministic, escaped, redacted single-line rendering of diagnostics.
 *
 * <p>Fields are sorted lexicographically; timestamp comes from the
 * producer, so formatting never consults the system clock. Text values are
 * quoted and control characters escaped to avoid multi-line log injection.
 * Sensitive fields are unconditionally redacted before rendering.
 *
 * <p>This is for operational diagnostics, not a durable structured
 * audit-event serialization protocol.
 */
public final class LogFormatter {

    private LogFormatter() {
    }

    /**
     * Render one event into an unambiguous single-line representation.
     *
     * @param event non-null event
     * @return a redacted, deterministic and single-line log message
     */
    public static String format(@NonNull LogEvent event) {
        StringBuilder output = new StringBuilder(144);
        output.append("at=").append(event.occurredAt());
        output.append(" level=").append(event.level());
        output.append(" name=").append(event.name());
        output.append(" source=").append(event.source());
        event.correlationId().ifPresent(id -> output.append(" correlation=").append(id));
        event.causationId().ifPresent(id -> output.append(" causation=").append(id));
        event.fields().forEach((key, field) -> {
            output.append(" field.").append(key).append('=');
            if (field.visibility() != LogField.Visibility.PUBLIC) {
                quoted(output, "[REDACTED]");
            } else {
                LogValue value = field.value();
                if (value instanceof LogValue.Text text) {
                    quoted(output, text.value());
                } else if (value instanceof LogValue.Count count) {
                    output.append(count.value());
                } else if (value instanceof LogValue.Decimal decimal) {
                    output.append(decimal.value().toPlainString());
                } else if (value instanceof LogValue.Flag flag) {
                    output.append(flag.value());
                } else {
                    throw new IllegalStateException("Unknown LogValue variant");
                }
            }
        });
        return output.toString();
    }

    private static void quoted(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> output.append("\\\\");
                case '"' -> output.append("\\\"");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (Character.isISOControl(ch) || ch == '\u2028' || ch == '\u2029') {
                        output.append("\\u");
                        String digits = Integer.toHexString(ch);
                        output.append("0".repeat(4 - digits.length())).append(digits);
                    } else {
                        output.append(ch);
                    }
                }
            }
        }
        output.append('"');
    }
}
