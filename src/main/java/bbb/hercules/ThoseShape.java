package bbb.hercules;

import lombok.NonNull;

/**
 * The four presence shapes of {@link Those}, deliberately discarding payloads.
 *
 * <p>Ordering is by inclusion of the two evidence kinds:
 * {@link #NEITHER} is bottom, {@link #BOTH} is top, and {@link #LEFT}
 * and {@link #RIGHT} are incomparable. This is a shape-only information
 * lattice, not a lattice over arbitrary payloads and not a claim that
 * {@code Those.Neither(E)} is an identity for evidence aggregation.
 *
 * <p>Both operations are associative, commutative and idempotent. Meet and
 * join satisfy absorption. Only {@link #of(Those)} loses explanations and
 * evidence values; there is intentionally no inverse conversion.
 */
public enum ThoseShape {
    /** Neither evidence type is present; no explanation is retained here. */
    NEITHER(0),
    /** Only left evidence is present. */
    LEFT(1),
    /** Only right evidence is present. */
    RIGHT(2),
    /** Both left and right evidence are present. */
    BOTH(3);

    private final int bits;

    ThoseShape(int bits) {
        this.bits = bits;
    }

    /**
     * Join two shapes in the information order (set union).
     *
     * @param other the other shape, not null
     * @return the least shape containing both evidence-presence sets
     */
    public ThoseShape join(@NonNull ThoseShape other) {
        return ofBits(bits | other.bits);
    }

    /**
     * Meet two shapes in the information order (set intersection).
     *
     * @param other the other shape, not null
     * @return the greatest shape contained in both presence sets
     */
    public ThoseShape meet(@NonNull ThoseShape other) {
        return ofBits(bits & other.bits);
    }

    /**
     * Project a full Those value to shape only.
     *
     * <p>This intentionally discards all payloads, including {@code E} from
     * {@code Neither(E)}. It must not be used for provenance-sensitive merges.
     *
     * @param value evidence or typed absence, not null
     * @return a presence-only shape
     */
    public static ThoseShape of(@NonNull Those<?, ?, ?> value) {
        return value.fold(
                left -> LEFT,
                right -> RIGHT,
                (left, right) -> BOTH,
                explanation -> NEITHER);
    }

    private static ThoseShape ofBits(int bits) {
        return switch (bits) {
            case 0 -> NEITHER;
            case 1 -> LEFT;
            case 2 -> RIGHT;
            case 3 -> BOTH;
            default -> throw new IllegalArgumentException("Unexpected presence bits: " + bits);
        };
    }
}
