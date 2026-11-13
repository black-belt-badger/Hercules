package bbb.hercules.fixtures;

import bbb.hercules.Either;
import org.jspecify.annotations.NullMarked;

/** Intentionally uncompilable: the NullAway error is this test's success. */
@NullMarked
public final class InvalidConsumer {
    private InvalidConsumer() {}

    public static String forbiddenReturn() {
        return null;
    }

    public static Either<String, Integer> forbiddenPayload() {
        return Either.<String, Integer>left(null);
    }

    public static Either<String, Integer> forbiddenCallback() {
        return Either.<String, Integer>right(1).map(null);
    }
}
