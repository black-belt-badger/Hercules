package bbb.hercules.fixtures;

import bbb.hercules.Either;
import bbb.hercules.F3;
import bbb.hercules.These;
import bbb.hercules.Those;
import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Legal public API uses from outside the library package. */
@NullMarked
public final class NullableConsumer {
    private NullableConsumer() {}

    public static Either<@Nullable String, Integer> nullableLeft() {
        return Either.<@Nullable String, Integer>left(null);
    }

    public static Validation<String, @Nullable Integer> nullableValid() {
        return Validation.<String, @Nullable Integer>valid(null);
    }

    public static These<@Nullable String, Integer> nullableThese() {
        return These.<@Nullable String, Integer>both(null, 1);
    }

    public static Those<String, Integer, @Nullable String> nullableThose() {
        return Those.<String, Integer, @Nullable String>neither(null);
    }

    public static F3<@Nullable String, Integer, Long, @Nullable String> nullableFunction() {
        return (first, second, third) -> first;
    }

    public static Validation<String, List<@Nullable Integer>> nullableSequence() {
        var app = new ValidationApplicative<String>((a, b) -> a + b);
        return app.<@Nullable Integer>sequence(
                List.of(Validation.<String, @Nullable Integer>valid(null)));
    }

    public static Validation<String, List<@Nullable Integer>> nullableTraverse() {
        var app = new ValidationApplicative<String>((a, b) -> a + b);
        List<@Nullable String> inputs = new ArrayList<>();
        inputs.add(null);
        return app.<@Nullable String, @Nullable Integer>traverse(
                inputs, item -> Validation.<String, @Nullable Integer>valid(null));
    }
}
