package io.github.blackbeltbadger.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the distinction between non-null structural references
 * and generic payloads that may explicitly be nullable.
 */
@ExtendWith(InstancioExtension.class)
class NullSafetyContractsTest {

    @RepeatedTest(64)
    void strictHandlersMustBeNonNullEvenWhenTheirCasesAreNotSelected(
            @Given String left, @Given Integer right, @Given Long absent) {
        for (Either<String, Integer> value : List.<Either<String, Integer>>of(
                Either.left(left), Either.right(right))) {
            assertThrows(NullPointerException.class,
                    () -> value.fold(null, x -> "right"));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "left", null));
            assertThrows(NullPointerException.class,
                    () -> value.map(null));
            assertThrows(NullPointerException.class,
                    () -> value.mapLeft(null));
            assertThrows(NullPointerException.class,
                    () -> value.flatMap(null));
            assertThrows(NullPointerException.class,
                    () -> value.recover(null));
        }

        for (These<String, Integer> value : List.<These<String, Integer>>of(
                These.left(left), These.right(right), These.both(left, right))) {
            assertThrows(NullPointerException.class,
                    () -> value.fold(null, x -> "right", (a, b) -> "both"));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "left", null, (a, b) -> "both"));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "left", x -> "right", null));
            assertThrows(NullPointerException.class,
                    () -> value.map(null));
            assertThrows(NullPointerException.class,
                    () -> value.flatMap(null, String::concat));
            assertThrows(NullPointerException.class,
                    () -> value.flatMap(x -> These.right(x), null));
        }

        for (Those<String, Integer, Long> value : List.<Those<String, Integer, Long>>of(
                Those.left(left), Those.right(right), Those.both(left, right),
                Those.neither(absent))) {
            assertThrows(NullPointerException.class,
                    () -> value.fold(null, x -> "", (a, b) -> "", x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", null, (a, b) -> "", x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", x -> "", null, x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", x -> "", (a, b) -> "", null));
            assertThrows(NullPointerException.class,
                    () -> value.map(null));
            assertThrows(NullPointerException.class,
                    () -> value.mapNeither(null));
        }

        for (Validation<String, Integer> value : List.<Validation<String, Integer>>of(
                Validation.invalid(left), Validation.valid(right))) {
            assertThrows(NullPointerException.class,
                    () -> value.fold(null, x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", null));
            assertThrows(NullPointerException.class,
                    () -> value.map(null));
            assertThrows(NullPointerException.class,
                    () -> value.mapLeft(null));
        }
    }

    @Test
    void functionAndPolicyParametersUseEagerLombokNullChecks() {
        assertThrows(NullPointerException.class,
                () -> new EitherChoice<String>("", null));
        assertThrows(NullPointerException.class,
                () -> new ValidationApplicative<String>(null));

        EitherChoice<String> choice = new EitherChoice<>("", String::concat);
        assertThrows(NullPointerException.class,
                () -> choice.choose(null, Either.<String, Integer>right(1)));
        assertThrows(NullPointerException.class,
                () -> choice.choose(Either.<String, Integer>right(1),
                        (Either<String, Integer>) null));
        assertThrows(NullPointerException.class,
                () -> choice.choose(Either.<String, Integer>right(1),
                        (Supplier<Either<String, Integer>>) null));

        ValidationApplicative<String> app = new ValidationApplicative<>(String::concat);
        Validation<String, Integer> invalid = Validation.invalid("bad");
        Validation<String, Integer> valid = Validation.valid(1);
        assertThrows(NullPointerException.class,
                () -> app.map2(null, invalid, Integer::sum));
        assertThrows(NullPointerException.class,
                () -> app.map2(invalid, null, Integer::sum));
        assertThrows(NullPointerException.class,
                () -> app.map2(invalid, valid, null));
        assertThrows(NullPointerException.class,
                () -> app.ap(null, valid));
        assertThrows(NullPointerException.class,
                () -> app.ap(Validation.valid((Function<Integer, Integer>) (x -> x)), null));
    }

    @Test
    void nullStructuralResultsAreRejectedOnEvaluationOnly() {
        Either<String, Integer> success = Either.right(1);
        Either<String, Integer> failure = Either.left("bad");
        assertThrows(NullPointerException.class,
                () -> success.flatMap(x -> null));
        assertEquals(failure, failure.flatMap(x -> null));

        These<String, Integer> both = These.both("notice", 1);
        assertThrows(NullPointerException.class,
                () -> both.flatMap(x -> null, String::concat));
        assertEquals(These.left("notice"),
                These.<String, Integer>left("notice").flatMap(x -> null, String::concat));

        assertThrows(NullPointerException.class,
                () -> Those.<String, Integer, Long>fromEither(Either.right(null)));
        assertThrows(NullPointerException.class,
                () -> Validation.<String, Integer>fromEither(null));

        EitherChoice<String> choice = new EitherChoice<>("", String::concat);
        assertThrows(NullPointerException.class,
                () -> choice.choose(failure, () -> null));
        assertEquals(success, choice.choose(success, () -> null));
    }

    @Test
    void typedNullablePayloadsRemainLegalIncludingRecordConstructors() {
        Either<@Nullable String, @Nullable Integer> left = Either.left(null);
        Either<@Nullable String, @Nullable Integer> right = Either.right(null);
        These<@Nullable String, @Nullable Integer> both = These.both(null, null);
        Those<@Nullable String, @Nullable Integer, @Nullable Long> neither = Those.neither(null);
        Validation<@Nullable String, @Nullable Integer> invalid = Validation.invalid(null);
        Validation<@Nullable String, @Nullable Integer> valid = Validation.valid(null);

        assertEquals(Either.left(null), left);
        assertEquals(Either.right(null), right);
        assertEquals(These.both(null, null), both);
        assertEquals(Those.neither(null), neither);
        assertEquals(Validation.invalid(null), invalid);
        assertEquals(Validation.valid(null), valid);

        assertNull(left.fold(Function.identity(), value -> "unused"));
        assertNull(right.fold(value -> "unused", Function.identity()));
        assertNull(both.fold(value -> "unused", value -> "unused", (a, b) -> a));
        assertNull(neither.fold(value -> "unused", value -> "unused",
                (a, b) -> "unused", Function.identity()));

        EitherChoice<@Nullable String> choice =
                new EitherChoice<>(null, (a, b) -> a == null ? b : b);
        assertNull(choice.empty().fold(Function.identity(), ignored -> "unused"));

        ValidationApplicative<@Nullable String> app =
                new ValidationApplicative<>((a, b) -> a == null ? b : a);
        assertEquals(Validation.invalid("bad"),
                app.map2(invalid, Validation.invalid("bad"),
                        (BiFunction<@Nullable Integer, @Nullable Integer, Integer>)
                                (a, b) -> 1));
    }

    @Test
    void payloadMapperMayReturnNullForExplicitlyNullableResultTypes() {
        Either<String, @Nullable Integer> either = Either.<String, Integer>right(3)
                .map(x -> null);
        These<String, @Nullable Integer> these = These.<String, Integer>right(3)
                .map(x -> null);
        Those<String, @Nullable Integer, Long> those =
                Those.<String, Integer, Long>right(3).map(x -> null);
        Validation<String, @Nullable Integer> validation =
                Validation.<String, Integer>valid(3).map(x -> null);

        assertEquals(Either.right(null), either);
        assertEquals(These.right(null), these);
        assertEquals(Those.right(null), those);
        assertEquals(Validation.valid(null), validation);
    }
}
