package bbb.hercules;

import lombok.NonNull;

/**
 * One structured field with explicit sensitivity classification.
 *
 * <p>Sensitive and secret scalar values are replaced with a fixed marker
 * before any interpreter invokes a caller-supplied sink. Producers must
 * classify fields accurately; marking a credential PUBLIC defeats redaction.
 * Field names are metadata and must not contain secrets or personal data.
 *
 * @param visibility disclosure classification
 * @param value immutable scalar payload
 */
public record LogField(@NonNull Visibility visibility, @NonNull LogValue value) {

    /** Classification used to enforce safe-by-default output. */
    public enum Visibility {
        /** Harmless operational data, explicitly approved for emission. */
        PUBLIC,
        /** Personally identifying or potentially linkable information. */
        PERSONAL,
        /** Credentials, tokens, keys and other secrets. */
        SECRET
    }

    /**
     * Replace sensitive content with a public fixed marker while preserving
     * the original classification. Returns the same object for public data.
     *
     * @return a field safe to expose to an output backend
     */
    public LogField redacted() {
        return visibility == Visibility.PUBLIC
                ? this : new LogField(visibility, new LogValue.Text("[REDACTED]"));
    }

    /**
     * Avoid leaking raw sensitive values in automatic record diagnostics.
     *
     * @return a redacted diagnostic representation
     */
    @Override
    public String toString() {
        return "LogField[" + visibility + ", "
                + (visibility == Visibility.PUBLIC ? value : "[REDACTED]") + "]";
    }
}
