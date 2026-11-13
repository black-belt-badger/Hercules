package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class EitherChoiceLawsTest {

    private static final EitherChoice<String> CHOICE =
            new EitherChoice<>("", String::concat);

    private static List<Either<String, Integer>> variants(String failure, Integer success) {
        return List.of(Either.left(failure), Either.right(success));
    }

    @RepeatedTest(64)
    void emptyIsLeftAndRightIdentityForEitherChoice(
            @Given String failure, @Given Integer success) {
        for (Either<String, Integer> value : variants(failure, success)) {
            assertEquals(value, CHOICE.choose(CHOICE.empty(), value));
            assertEquals(value, CHOICE.choose(value, CHOICE.empty()));
        }
    }

    @RepeatedTest(64)
    void choiceIsAssociativeWithAnAssociativeLeftCombiner(
            @Given String l1, @Given String l2, @Given String l3,
            @Given Integer r1, @Given Integer r2, @Given Integer r3) {
        for (Either<String, Integer> a : variants(l1, r1)) {
            for (Either<String, Integer> b : variants(l2, r2)) {
                for (Either<String, Integer> c : variants(l3, r3)) {
                    assertEquals(
                            CHOICE.choose(CHOICE.choose(a, b), c),
                            CHOICE.choose(a, CHOICE.choose(b, c)));
                }
            }
        }
    }

    @RepeatedTest(64)
    void leftmostSuccessWinsAndFailuresAccumulateInOrder(
            @Given String firstError, @Given String secondError,
            @Given Integer firstValue, @Given Integer secondValue) {
        Either<String, Integer> left1 = Either.left(firstError);
        Either<String, Integer> left2 = Either.left(secondError);
        Either<String, Integer> right1 = Either.right(firstValue);
        Either<String, Integer> right2 = Either.right(secondValue);

        assertEquals(Either.left(firstError + secondError), CHOICE.choose(left1, left2));
        assertEquals(right1, CHOICE.choose(right1, right2));
        assertEquals(right1, CHOICE.choose(left1, right1));
        assertEquals(right1, CHOICE.choose(right1, left1));
        // Deliberate policy: a successful fallback discards preceding errors.
        assertEquals(right2, CHOICE.choose(left2, right2));
    }

    @Test
    void fallbackIsLazyWhenTheFirstResultIsRight() {
        var calls = new AtomicInteger();
        Supplier<Either<String, Integer>> second = () -> {
            calls.incrementAndGet();
            return Either.right(42);
        };

        Either<String, Integer> first = Either.right(7);
        assertSame(first, CHOICE.choose(first, second));
        assertEquals(0, calls.get());

        assertEquals(Either.right(42), CHOICE.choose(Either.left("failed"), second));
        assertEquals(1, calls.get());

        assertEquals(Either.left("AB"), CHOICE.choose(
                Either.<String, Integer>left("A"), () -> {
                    calls.incrementAndGet();
                    return Either.left("B");
                }));
        assertEquals(2, calls.get());
    }

    @Test
    void leftCombinerRunsOnlyWhenBothAlternativesAreLeft() {
        var calls = new AtomicInteger();
        EitherChoice<String> policy = new EitherChoice<>("", (a, b) -> {
            calls.incrementAndGet();
            return a + b;
        });

        assertEquals(Either.right(3), policy.choose(Either.left("A"), Either.right(3)));
        assertEquals(Either.right(3), policy.choose(Either.right(3), Either.left("A")));
        assertEquals(0, calls.get());

        assertEquals(Either.left("AB"), policy.choose(Either.left("A"), Either.left("B")));
        assertEquals(1, calls.get());
    }

    @Test
    void nullCallbacksAndNullSupplierResultsAreRejected() {
        assertThrows(NullPointerException.class, () -> new EitherChoice<>("identity", null));
        assertThrows(NullPointerException.class,
                () -> CHOICE.choose(null, Either.<String, Integer>right(4)));
        assertThrows(NullPointerException.class,
                () -> CHOICE.choose(Either.right(4), (Either<String, Integer>) null));
        assertThrows(NullPointerException.class,
                () -> CHOICE.choose(Either.right(4), (Supplier<Either<String, Integer>>) null));
        assertThrows(NullPointerException.class,
                () -> CHOICE.choose(Either.<String, Integer>left("A"), () -> null));
        assertEquals(Either.right(4), CHOICE.choose(Either.<String, Integer>right(4),
                () -> null));
    }

    @Test
    void exceptionsFromSupplierAndCombinerAreNotWrapped() {
        var failure = new IllegalStateException("expected");
        EitherChoice<String> bad = new EitherChoice<>("", (a, b) -> { throw failure; });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> CHOICE.choose(Either.<String, Integer>left("A"), () -> {
                    throw failure;
                })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> bad.choose(Either.<String, Integer>left("A"), Either.left("B"))));
        assertEquals(Either.right(10), bad.choose(
                Either.<String, Integer>right(10), Either.left("B")));
    }

    @Test
    void explicitlyNullableLeftTypeAllowsNullIdentity() {
        EitherChoice<@Nullable String> policy = new EitherChoice<>(null,
                (a, b) -> a == null ? b : b == null ? a : a + b);
        Either<@Nullable String, Integer> nothing = policy.empty();

        assertInstanceOf(Either.Left.class, nothing);
        assertNull(nothing.fold(value -> value, value -> "unexpected"));
        assertEquals(Either.<@Nullable String, Integer>left("problem"),
                policy.choose(nothing, Either.left("problem")));
        assertEquals(Either.<@Nullable String, Integer>left("problem"),
                policy.choose(Either.left("problem"), nothing));
    }

    @Test
    void nonMonoidalLeftPolicyDoesNotEarnTheIdentityLaw() {
        EitherChoice<Integer> subtraction = new EitherChoice<>(0, (a, b) -> a - b);
        Either<Integer, String> error = Either.left(4);

        assertNotEquals(error, subtraction.choose(subtraction.empty(), error));
        // This explicitly demonstrates why a random BinaryOperator is not
        // automatically evidence of monoidal laws.
    }
}
