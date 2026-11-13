package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class EitherFilterLawsTest {

    @RepeatedTest(64)
    void leftIsPreservedAndNeitherCallbackRuns(
            @Given String left, @Given Integer right) {
        Either<String, Integer> original = Either.left(left);
        var predicates = new AtomicInteger();
        var failures = new AtomicInteger();

        Either<String, Integer> result = original.filterOrElse(
                value -> {
                    predicates.incrementAndGet();
                    return value > right;
                },
                value -> {
                    failures.incrementAndGet();
                    return "rejected " + value;
                });

        assertEquals(original, result);
        assertEquals(0, predicates.get());
        assertEquals(0, failures.get());
    }

    @RepeatedTest(64)
    void successfulRightIsPreservedWithoutCallingFailureFactory(
            @Given Integer right, @Given String error) {
        Either<String, Integer> original = Either.right(right);
        var predicates = new AtomicInteger();
        var failures = new AtomicInteger();

        Either<String, Integer> result = original.filterOrElse(
                value -> {
                    predicates.incrementAndGet();
                    return true;
                },
                value -> {
                    failures.incrementAndGet();
                    return error;
                });

        assertEquals(original, result);
        assertEquals(1, predicates.get());
        assertEquals(0, failures.get());
    }

    @RepeatedTest(64)
    void rejectedRightBecomesCallerDefinedLeftExactlyOnce(
            @Given Integer right, @Given String reason) {
        var predicates = new AtomicInteger();
        var failures = new AtomicInteger();

        Either<String, Integer> result = Either.<String, Integer>right(right)
                .filterOrElse(
                        value -> {
                            predicates.incrementAndGet();
                            return false;
                        },
                        value -> {
                            failures.incrementAndGet();
                            return reason + ":" + value;
                        });

        assertEquals(Either.left(reason + ":" + right), result);
        assertEquals(1, predicates.get());
        assertEquals(1, failures.get());
    }

    @RepeatedTest(64)
    void filterOrElseMatchesTotalFoldSemantics(
            @Given Integer value, @Given String originalLeft,
            @Given Integer threshold) {
        Predicate<Integer> condition = n -> n > threshold;
        Function<Integer, String> rejected = n -> "below: " + n;

        for (Either<String, Integer> input : List.<Either<String, Integer>>of(
                Either.left(originalLeft), Either.right(value))) {
            Either<String, Integer> expected = input.fold(
                    Either::left,
                    n -> condition.test(n) ? Either.right(n) : Either.left(rejected.apply(n)));

            assertEquals(expected, input.filterOrElse(condition, rejected));
        }
    }

    @Test
    void requiredCallbacksAreCheckedEagerlyEvenOnLeftAndPassingRight() {
        Either<String, Integer> left = Either.left("unavailable");
        Either<String, Integer> right = Either.right(1);

        assertThrows(NullPointerException.class, () -> left.filterOrElse(null, n -> "bad"));
        assertThrows(NullPointerException.class, () -> left.filterOrElse(n -> true, null));
        assertThrows(NullPointerException.class, () -> right.filterOrElse(null, n -> "bad"));
        assertThrows(NullPointerException.class, () -> right.filterOrElse(n -> true, null));
    }

    @Test
    void callbackExceptionsPropagateWithoutWrappingOrConvertingToLeft() {
        var failure = new IllegalStateException("callback failed");
        Either<String, Integer> right = Either.right(4);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.filterOrElse(n -> {
                    throw failure;
                }, n -> "bad")));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.filterOrElse(n -> false, n -> {
                    throw failure;
                })));

        assertEquals(right, right.filterOrElse(n -> true, n -> {
            throw failure;
        }));
        assertEquals(Either.<String, Integer>left("absent"),
                Either.<String, Integer>left("absent").filterOrElse(n -> {
                    throw failure;
                }, n -> {
                    throw failure;
                }));
    }

    @Test
    void explicitlyNullableGenericPayloadsArePreserved() {
        Either<@Nullable String, @Nullable Integer> nullRight = Either.right(null);

        assertEquals(Either.right(null),
                nullRight.filterOrElse(value -> value == null, value -> "missing"));
        assertEquals(Either.left(null),
                nullRight.filterOrElse(value -> value != null, value -> null));

        Either<@Nullable String, @Nullable Integer> nullLeft = Either.left(null);
        assertEquals(nullLeft, nullLeft.filterOrElse(n -> false, n -> "not used"));
    }
}
