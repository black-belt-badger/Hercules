package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class TheseLawsTest {

    private static <L, R> List<These<L, R>> variants(L left, R right) {
        return List.of(These.left(left), These.right(right), These.both(left, right));
    }

    private static final BinaryOperator<String> APPEND =
            (first, second) -> first + "|" + second;

    @RepeatedTest(64)
    void functorIdentityAndComposition(@Given String left, @Given Integer right) {
        Function<Integer, Integer> f = n -> n + 5;
        Function<Integer, String> g = n -> "n:" + n * 2;

        for (These<String, Integer> value : variants(left, right)) {
            assertEquals(value, value.map(Function.identity()));
            assertEquals(value.map(f).map(g), value.map(f.andThen(g)));
        }
    }

    @RepeatedTest(64)
    void bimapCoherenceAndSwapInvolution(@Given String left, @Given Integer right) {
        Function<String, Integer> l = String::length;
        Function<Integer, String> r = x -> "v:" + x;
        Function<String, String> decorate = x -> "[" + x + "]";
        Function<Integer, Integer> increment = x -> x + 1;

        for (These<String, Integer> value : variants(left, right)) {
            assertEquals(value, value.bimap(Function.identity(), Function.identity()));
            assertEquals(value.map(r), value.bimap(Function.identity(), r));
            assertEquals(value.mapLeft(l), value.bimap(l, Function.identity()));
            assertEquals(value, value.swap().swap());
            assertEquals(value.map(r).swap(), value.swap().mapLeft(r));
            assertEquals(value.mapLeft(l).swap(), value.swap().map(l));
            assertEquals(value.bimap(decorate, increment).bimap(l, r),
                    value.bimap(decorate.andThen(l), increment.andThen(r)));
            assertEquals(value.mapLeft(decorate).mapLeft(l),
                    value.mapLeft(decorate.andThen(l)));
        }
    }

    @RepeatedTest(64)
    void flatMapIdentityAndAssociativityForAssociativeLeftCombine(
            @Given String left, @Given Integer right) {
        Function<Integer, These<String, Integer>> f = n -> switch (Math.floorMod(n, 3)) {
            case 0 -> These.left("f:" + n);
            case 1 -> These.right(n + 1);
            default -> These.both("f:" + n, n * 2);
        };
        Function<Integer, These<String, Integer>> g = n -> switch (Math.floorMod(n, 3)) {
            case 0 -> These.both("g:" + n, n - 1);
            case 1 -> These.left("g:" + n);
            default -> These.right(n + 2);
        };

        assertEquals(f.apply(right), These.<String, Integer>right(right).flatMap(f, APPEND));

        for (These<String, Integer> value : variants(left, right)) {
            assertEquals(value, value.flatMap(These::right, APPEND));
            assertEquals(
                    value.flatMap(f, APPEND).flatMap(g, APPEND),
                    value.flatMap(n -> f.apply(n).flatMap(g, APPEND), APPEND));
        }
    }

    @Test
    void flatMapPreservesAndAccumulatesLeftValuesInOrder() {
        These<String, Integer> original = These.both("previous", 10);

        assertEquals(These.left("previous|new"),
                original.flatMap(n -> These.left("new"), APPEND));
        assertEquals(These.both("previous", 20),
                original.flatMap(n -> These.right(n * 2), APPEND));
        assertEquals(These.both("previous|new", 20),
                original.flatMap(n -> These.both("new", n * 2), APPEND));

        assertEquals(These.left("previous"),
                These.<String, Integer>left("previous")
                        .flatMap(n -> { fail("binder must be skipped"); return These.right(n); }, APPEND));

        assertEquals(These.left("new"),
                These.<String, Integer>right(10).flatMap(n -> These.left("new"), APPEND));
        assertEquals(These.right(20),
                These.<String, Integer>right(10).flatMap(n -> These.right(20), APPEND));
        assertEquals(These.both("new", 20),
                These.<String, Integer>right(10).flatMap(n -> These.both("new", 20), APPEND));
    }

    @RepeatedTest(64)
    void foldsExhaustivelyAndCallsOneHandler(@Given String left, @Given Integer right) {
        var leftCalls = new AtomicInteger();
        var rightCalls = new AtomicInteger();
        var bothCalls = new AtomicInteger();

        assertEquals(left, These.<String, Integer>left(left).fold(
                v -> { leftCalls.incrementAndGet(); return v; },
                v -> { rightCalls.incrementAndGet(); return v.toString(); },
                (l, r) -> { bothCalls.incrementAndGet(); return l + r; }));
        assertEquals(right.toString(), These.<String, Integer>right(right).fold(
                v -> { leftCalls.incrementAndGet(); return v; },
                v -> { rightCalls.incrementAndGet(); return v.toString(); },
                (l, r) -> { bothCalls.incrementAndGet(); return l + r; }));
        assertEquals(left + right, These.<String, Integer>both(left, right).fold(
                v -> { leftCalls.incrementAndGet(); return v; },
                v -> { rightCalls.incrementAndGet(); return v.toString(); },
                (l, r) -> { bothCalls.incrementAndGet(); return l + r; }));

        assertEquals(1, leftCalls.get());
        assertEquals(1, rightCalls.get());
        assertEquals(1, bothCalls.get());
    }

    @Test
    void callbackCountsShowStrictEvaluationAndUnusedBranchesAreSkipped() {
        var lCalls = new AtomicInteger();
        var rCalls = new AtomicInteger();
        var binderCalls = new AtomicInteger();
        var combineCalls = new AtomicInteger();
        BinaryOperator<String> combine = (l, r) -> {
            combineCalls.incrementAndGet();
            return l + r;
        };

        These<String, Integer> left = These.left("warn");
        These<String, Integer> right = These.right(5);
        These<String, Integer> both = These.both("warn", 5);

        Function<String, String> mapL = l -> { lCalls.incrementAndGet(); return l + "!"; };
        Function<Integer, Integer> mapR = r -> { rCalls.incrementAndGet(); return r * 2; };

        assertEquals(These.left("warn!"), left.bimap(mapL, mapR));
        assertEquals(These.right(10), right.bimap(mapL, mapR));
        assertEquals(These.both("warn!", 10), both.bimap(mapL, mapR));
        assertEquals(2, lCalls.get());
        assertEquals(2, rCalls.get());

        Function<Integer, These<String, Integer>> next = n -> {
            binderCalls.incrementAndGet();
            return These.both("next", n + 1);
        };

        assertEquals(left, left.flatMap(next, combine));
        assertEquals(0, binderCalls.get());
        assertEquals(These.both("next", 6), right.flatMap(next, combine));
        assertEquals(1, binderCalls.get());
        assertEquals(These.both("warnnext", 6), both.flatMap(next, combine));
        assertEquals(2, binderCalls.get());
        assertEquals(1, combineCalls.get());
    }

    @Test
    void nullableTypeArgumentsPreserveVariantAndPayload() {
        These<@Nullable String, @Nullable Integer> a = These.left(null);
        These<@Nullable String, @Nullable Integer> b = These.right(null);
        These<@Nullable String, @Nullable Integer> c = These.both(null, null);

        assertInstanceOf(These.This.class, a);
        assertInstanceOf(These.That.class, b);
        assertInstanceOf(These.Both.class, c);
        assertEquals(These.<@Nullable String, @Nullable Integer>left(null), a);
        assertEquals(These.<@Nullable String, @Nullable Integer>right(null), b);
        assertEquals(These.<@Nullable String, @Nullable Integer>both(null, null), c);
        assertEquals(c, c.swap());
        assertNull(a.fold(Function.identity(), x -> "unused", (l, r) -> "unused"));
        assertNull(b.fold(x -> 1, Function.identity(), (l, r) -> 1));
        assertNull(c.fold(x -> 1, x -> 1, (l, r) -> l));
    }

    @Test
    void rejectsNullCallbacksIncludingThoseNotSelected() {
        for (These<String, Integer> value : variants("diagnostic", 10)) {
            assertThrows(NullPointerException.class, () -> value.map(null));
            assertThrows(NullPointerException.class, () -> value.mapLeft(null));
            assertThrows(NullPointerException.class, () -> value.bimap(null, Function.identity()));
            assertThrows(NullPointerException.class, () -> value.bimap(Function.identity(), null));
            assertThrows(NullPointerException.class, () -> value.flatMap(null, APPEND));
            assertThrows(NullPointerException.class, () -> value.flatMap(These::right, null));
            assertThrows(NullPointerException.class, () -> value.fold(null, x -> x, (l, r) -> r));
            assertThrows(NullPointerException.class, () -> value.fold(x -> x, null, (l, r) -> r));
            assertThrows(NullPointerException.class, () -> value.fold(x -> x, x -> x, null));
        }
        assertThrows(NullPointerException.class,
                () -> These.<String, Integer>right(5).flatMap(x -> null, APPEND));
        assertThrows(NullPointerException.class,
                () -> These.<String, Integer>both("info", 5).flatMap(x -> null, APPEND));
    }

    @Test
    void userExceptionsPropagateUnchanged() {
        var failure = new IllegalStateException("expected");
        These<String, Integer> left = These.left("diagnostic");
        These<String, Integer> right = These.right(10);
        These<String, Integer> both = These.both("diagnostic", 10);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.map(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> left.mapLeft(x -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> both.bimap(x -> { throw failure; }, x -> x)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> both.flatMap(n -> These.left("next"), (x, y) -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> right.flatMap(x -> { throw failure; }, APPEND)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> both.fold(x -> "", x -> "", (l, r) -> { throw failure; })));
    }
}
