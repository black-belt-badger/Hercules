package io.github.blackbeltbadger.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Laws are checked for both variants against Instancio-generated values.
 * The Instancio extension reports a reproducible seed if a test fails.
 */
@ExtendWith(InstancioExtension.class)
class EitherLawsTest {

    private static <L, R> List<Either<L, R>> variants(L left, R right) {
        return List.of(Either.left(left), Either.right(right));
    }

    @RepeatedTest(64)
    void functorIdentityAndComposition(@Given String leftValue, @Given Integer rightValue) {
        Function<Integer, Integer> f = x -> x + 5;
        Function<Integer, String> g = x -> "n:" + (x * 3);

        for (Either<String, Integer> either : variants(leftValue, rightValue)) {
            assertEquals(either, either.map(Function.identity()));
            assertEquals(either.map(f).map(g), either.map(f.andThen(g)));
        }
    }

    @RepeatedTest(64)
    void monadLeftIdentityRightIdentityAndAssociativity(
            @Given String leftValue, @Given Integer rightValue) {
        Function<Integer, Either<String, Integer>> f = x -> x % 3 == 0
                ? Either.left("divisible by three")
                : Either.right(x + 7);
        Function<Integer, Either<String, Integer>> g = x -> x % 5 == 0
                ? Either.left("divisible by five")
                : Either.right(x * 2);

        assertEquals(f.apply(rightValue), Either.<String, Integer>right(rightValue).flatMap(f));

        for (Either<String, Integer> either : variants(leftValue, rightValue)) {
            assertEquals(either, either.flatMap(Either::right));
            assertEquals(
                    either.flatMap(f).flatMap(g),
                    either.flatMap(x -> f.apply(x).flatMap(g)));
        }
    }

    @RepeatedTest(64)
    void bimapAndSwapLaws(@Given String leftValue, @Given Integer rightValue) {
        Function<String, Integer> leftMapper = String::length;
        Function<Integer, Long> rightMapper = Integer::longValue;

        for (Either<String, Integer> either : variants(leftValue, rightValue)) {
            assertEquals(either, either.bimap(Function.identity(), Function.identity()));
            assertEquals(either.map(rightMapper),
                    either.bimap(Function.identity(), rightMapper));
            assertEquals(either.mapLeft(leftMapper),
                    either.bimap(leftMapper, Function.identity()));
            assertEquals(either, either.swap().swap());
            assertEquals(either.map(rightMapper).swap(),
                    either.swap().mapLeft(rightMapper));
            assertEquals(either.mapLeft(leftMapper).swap(),
                    either.swap().map(leftMapper));
        }
    }

    @RepeatedTest(64)
    void foldsExactlyOneVariant(@Given String leftValue, @Given Integer rightValue) {
        var leftCalls = new AtomicInteger();
        var rightCalls = new AtomicInteger();
        Either<String, Integer> left = Either.left(leftValue);
        Either<String, Integer> right = Either.right(rightValue);

        assertEquals(leftValue, left.fold(x -> {
            leftCalls.incrementAndGet();
            return x;
        }, x -> {
            rightCalls.incrementAndGet();
            return String.valueOf(x);
        }));
        assertEquals(1, leftCalls.get());
        assertEquals(0, rightCalls.get());

        assertEquals(String.valueOf(rightValue), right.fold(x -> {
            leftCalls.incrementAndGet();
            return x;
        }, x -> {
            rightCalls.incrementAndGet();
            return String.valueOf(x);
        }));
        assertEquals(1, leftCalls.get());
        assertEquals(1, rightCalls.get());
    }

    @RepeatedTest(64)
    void convenienceOperationsPreserveTheCorrectBranch(
            @Given String leftValue, @Given Integer rightValue) {
        Either<String, Integer> left = Either.left(leftValue);
        Either<String, Integer> right = Either.right(rightValue);

        assertEquals(Integer.valueOf(7), left.getOrElse(7));
        assertEquals(rightValue, right.getOrElse(7));
        assertEquals(Either.<String, Integer>right(leftValue.length()),
                left.recover(String::length));
        assertEquals(right, right.recover(String::length));
        assertEquals(Optional.empty(), left.toOptional());
        assertEquals(Optional.of(rightValue), right.toOptional());
        assertEquals(new Either.Left<>(leftValue), left);
        assertEquals(new Either.Right<>(rightValue), right);
    }

    @Test
    void nullableTypeArgumentsPreservePayloadsUntilExplicitConversion() {
        Either<@Nullable String, @Nullable Integer> left = Either.left(null);
        Either<@Nullable String, @Nullable Integer> right = Either.right(null);

        assertInstanceOf(Either.Left.class, left);
        assertInstanceOf(Either.Right.class, right);
        assertNull(left.fold(Function.identity(), x -> 1));
        assertNull(right.fold(x -> 1, Function.identity()));
        assertEquals(Either.<@Nullable String, @Nullable Integer>left(null), left);
        assertEquals(Either.<@Nullable String, @Nullable Integer>right(null), right);
        assertEquals(Optional.empty(), left.toOptional());
        assertEquals(Optional.empty(), right.toOptional()); // Loss of a nullable Right.
    }

    @Test
    void validatesAllCallbackObjectsEvenOnUnusedBranches() {
        Either<String, Integer> left = Either.left("left");
        Either<String, Integer> right = Either.right(1);

        for (Either<String, Integer> either : List.of(left, right)) {
            assertThrows(NullPointerException.class, () -> either.map(null));
            assertThrows(NullPointerException.class, () -> either.mapLeft(null));
            assertThrows(NullPointerException.class, () -> either.bimap(null, x -> x));
            assertThrows(NullPointerException.class, () -> either.bimap(x -> x, null));
            assertThrows(NullPointerException.class, () -> either.flatMap(null));
            assertThrows(NullPointerException.class, () -> either.recover(null));
            assertThrows(NullPointerException.class, () -> either.fold(null, x -> x));
            assertThrows(NullPointerException.class, () -> either.fold(x -> x, null));
        }
        assertThrows(NullPointerException.class, () -> right.flatMap(x -> null));
    }

    @Test
    void transformationsExecuteStrictlyAndOnlyOnSelectedBranch() {
        var rightCalls = new AtomicInteger();
        var leftCalls = new AtomicInteger();
        var bindCalls = new AtomicInteger();
        Either<String, Integer> left = Either.left("stop");
        Either<String, Integer> right = Either.right(10);

        assertEquals(left, left.map(x -> { rightCalls.incrementAndGet(); return x + 1; }));
        assertEquals(left, left.flatMap(x -> {
            bindCalls.incrementAndGet();
            return Either.right(x + 1);
        }));
        assertEquals(0, rightCalls.get());
        assertEquals(0, bindCalls.get());

        assertEquals(Either.<String, Integer>right(11),
                right.map(x -> { rightCalls.incrementAndGet(); return x + 1; }));
        assertEquals(1, rightCalls.get());
        assertEquals(Either.<String, Integer>right(12),
                right.flatMap(x -> { bindCalls.incrementAndGet(); return Either.right(x + 2); }));
        assertEquals(1, bindCalls.get());

        assertEquals(Either.<Integer, Integer>left(4), left.mapLeft(x -> {
            leftCalls.incrementAndGet();
            return x.length();
        }));
        assertEquals(1, leftCalls.get());
        assertEquals(Either.<Integer, Integer>right(10), right.mapLeft(x -> {
            leftCalls.incrementAndGet();
            return x.length();
        }));
        assertEquals(1, leftCalls.get());
    }

    @Test
    void userExceptionsPropagateWithoutWrapping() {
        var failure = new IllegalStateException("expected");
        Either<String, Integer> right = Either.right(42);
        Either<String, Integer> left = Either.left("error");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.map(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.flatMap(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> left.mapLeft(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> left.recover(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.fold(x -> 0, x -> { throw failure; })));

        assertEquals(left, left.map(x -> { throw failure; }));
        assertEquals(right, right.mapLeft(x -> { throw failure; }));
    }
}
