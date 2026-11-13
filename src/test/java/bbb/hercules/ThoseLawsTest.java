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

import static org.junit.jupiter.api.Assertions.*;

/** Property-style laws for all four cases, using reproducible Instancio data. */
@ExtendWith(InstancioExtension.class)
class ThoseLawsTest {

    private static <A, B, E> List<Those<A, B, E>> variants(A left, B right, E absent) {
        return List.of(
                Those.left(left),
                Those.right(right),
                Those.both(left, right),
                Those.neither(absent));
    }

    @RepeatedTest(64)
    void eachComponentMappingHasIdentityAndComposition(
            @Given String left, @Given Integer right, @Given Long absent) {
        Function<String, String> f = x -> "[" + x + "]";
        Function<String, Integer> g = String::length;
        Function<Integer, Integer> h = x -> x + 4;
        Function<Integer, String> i = x -> "n:" + x;
        Function<Long, Long> j = x -> x * 3;
        Function<Long, String> k = Object::toString;

        for (Those<String, Integer, Long> value : variants(left, right, absent)) {
            assertEquals(value, value.map(Function.identity()));
            assertEquals(value, value.mapLeft(Function.identity()));
            assertEquals(value, value.mapNeither(Function.identity()));
            assertEquals(value.map(h).map(i), value.map(h.andThen(i)));
            assertEquals(value.mapLeft(f).mapLeft(g), value.mapLeft(f.andThen(g)));
            assertEquals(value.mapNeither(j).mapNeither(k), value.mapNeither(j.andThen(k)));
        }
    }

    @RepeatedTest(64)
    void bimapAndTrimapAgreeWithComponentMappings(
            @Given String left, @Given Integer right, @Given Long absent) {
        Function<String, Integer> a = String::length;
        Function<Integer, String> b = n -> "value:" + n;
        Function<Long, String> e = n -> "absent:" + n;

        for (Those<String, Integer, Long> value : variants(left, right, absent)) {
            assertEquals(value, value.bimap(Function.identity(), Function.identity()));
            assertEquals(value, value.trimap(
                    Function.identity(), Function.identity(), Function.identity()));
            assertEquals(value.map(b), value.bimap(Function.identity(), b));
            assertEquals(value.mapLeft(a), value.bimap(a, Function.identity()));
            assertEquals(value.mapNeither(e),
                    value.trimap(Function.identity(), Function.identity(), e));
            assertEquals(value.bimap(a, b), value.mapLeft(a).map(b));
            assertEquals(value.trimap(a, b, e),
                    value.mapLeft(a).map(b).mapNeither(e));
        }
    }

    @RepeatedTest(64)
    void swappingTwiceRestoresAllFourVariants(
            @Given String left, @Given Integer right, @Given Long absent) {
        for (Those<String, Integer, Long> value : variants(left, right, absent)) {
            assertEquals(value, value.swap().swap());
            assertEquals(value.map(Object::toString).swap(),
                    value.swap().mapLeft(Object::toString));
            assertEquals(value.mapLeft(String::length).swap(),
                    value.swap().map(String::length));
            assertEquals(value.mapNeither(Object::toString).swap(),
                    value.swap().mapNeither(Object::toString));
        }
    }

    @RepeatedTest(64)
    void nestedEitherConversionIsLosslessInBothDirections(
            @Given String left, @Given Integer right, @Given Long absent) {
        for (Those<String, Integer, Long> value : variants(left, right, absent)) {
            assertEquals(value, Those.fromEither(value.toEither()));
            assertEquals(value.toEither(),
                    Those.<String, Integer, Long>fromEither(value.toEither()).toEither());
        }

        List<Either<Long, These<String, Integer>>> nested = List.of(
                Either.left(absent),
                Either.right(These.left(left)),
                Either.right(These.right(right)),
                Either.right(These.both(left, right)));

        for (Either<Long, These<String, Integer>> value : nested) {
            assertEquals(value, Those.fromEither(value).toEither());
        }
    }

    @RepeatedTest(64)
    void foldIsTotalAndInvokesExactlyOneHandler(
            @Given String left, @Given Integer right, @Given Long absent) {
        var leftCalls = new AtomicInteger();
        var rightCalls = new AtomicInteger();
        var bothCalls = new AtomicInteger();
        var neitherCalls = new AtomicInteger();

        for (Those<String, Integer, Long> value : variants(left, right, absent)) {
            String result = value.fold(
                    a -> { leftCalls.incrementAndGet(); return "L:" + a; },
                    b -> { rightCalls.incrementAndGet(); return "R:" + b; },
                    (a, b) -> { bothCalls.incrementAndGet(); return "B:" + a + b; },
                    e -> { neitherCalls.incrementAndGet(); return "N:" + e; });
            assertNotNull(result);
        }

        assertEquals(1, leftCalls.get());
        assertEquals(1, rightCalls.get());
        assertEquals(1, bothCalls.get());
        assertEquals(1, neitherCalls.get());
    }

    @Test
    void mappingIsStrictAndRunsOnlyCallbacksForExistingPayloads() {
        var aCalls = new AtomicInteger();
        var bCalls = new AtomicInteger();
        var eCalls = new AtomicInteger();
        Function<String, String> a = value -> {
            aCalls.incrementAndGet();
            return value + "!";
        };
        Function<Integer, Integer> b = value -> {
            bCalls.incrementAndGet();
            return value + 1;
        };
        Function<String, String> e = value -> {
            eCalls.incrementAndGet();
            return value + "!";
        };

        assertEquals(Those.left("A!"), Those.<String, Integer, String>left("A")
                .trimap(a, b, e));
        assertEquals(1, aCalls.get());
        assertEquals(0, bCalls.get());
        assertEquals(0, eCalls.get());

        assertEquals(Those.right(11), Those.<String, Integer, String>right(10)
                .trimap(a, b, e));
        assertEquals(1, bCalls.get());

        assertEquals(Those.both("A!", 11), Those.<String, Integer, String>both("A", 10)
                .trimap(a, b, e));
        assertEquals(2, aCalls.get());
        assertEquals(2, bCalls.get());

        assertEquals(Those.neither("unavailable!"), Those.<String, Integer, String>neither("unavailable")
                .trimap(a, b, e));
        assertEquals(1, eCalls.get());
        assertEquals(2, aCalls.get());
        assertEquals(2, bCalls.get());
    }

    @Test
    void rejectsNullCallbacksForEveryVariantEvenWhenInactive() {
        for (Those<String, Integer, String> value : variants("A", 10, "no evidence")) {
            assertThrows(NullPointerException.class, () -> value.map(null));
            assertThrows(NullPointerException.class, () -> value.mapLeft(null));
            assertThrows(NullPointerException.class, () -> value.mapNeither(null));
            assertThrows(NullPointerException.class,
                    () -> value.bimap(null, Function.identity()));
            assertThrows(NullPointerException.class,
                    () -> value.bimap(Function.identity(), null));
            assertThrows(NullPointerException.class,
                    () -> value.trimap(null, Function.identity(), Function.identity()));
            assertThrows(NullPointerException.class,
                    () -> value.trimap(Function.identity(), null, Function.identity()));
            assertThrows(NullPointerException.class,
                    () -> value.trimap(Function.identity(), Function.identity(), null));
            assertThrows(NullPointerException.class,
                    () -> value.fold(null, x -> "", (l, r) -> "", x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", null, (l, r) -> "", x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", x -> "", null, x -> ""));
            assertThrows(NullPointerException.class,
                    () -> value.fold(x -> "", x -> "", (l, r) -> "", null));
        }

        assertThrows(NullPointerException.class,
                () -> Those.<String, Integer, String>fromEither(null));
        assertThrows(NullPointerException.class,
                () -> Those.<String, Integer, String>fromEither(Either.right(null)));
    }

    @Test
    void supportsExplicitlyNullablePayloadTypesWithoutChangingVariants() {
        Those<@Nullable String, @Nullable Integer, @Nullable Long> a = Those.left(null);
        Those<@Nullable String, @Nullable Integer, @Nullable Long> b = Those.right(null);
        Those<@Nullable String, @Nullable Integer, @Nullable Long> both = Those.both(null, null);
        Those<@Nullable String, @Nullable Integer, @Nullable Long> neither = Those.neither(null);

        assertInstanceOf(Those.This.class, a);
        assertInstanceOf(Those.That.class, b);
        assertInstanceOf(Those.Both.class, both);
        assertInstanceOf(Those.Neither.class, neither);

        for (Those<@Nullable String, @Nullable Integer, @Nullable Long> value :
                List.of(a, b, both, neither)) {
            assertEquals(value, Those.fromEither(value.toEither()));
            assertEquals(value, value.trimap(Function.identity(), Function.identity(),
                    Function.identity()));
        }
    }

    @Test
    void exceptionsFromUserFunctionsPropagateWithoutWrapping() {
        var thrown = new IllegalStateException("from callback");
        Those<String, Integer, Long> left = Those.left("A");
        Those<String, Integer, Long> right = Those.right(3);
        Those<String, Integer, Long> both = Those.both("A", 3);
        Those<String, Integer, Long> neither = Those.neither(12L);

        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> left.mapLeft(value -> { throw thrown; })));
        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> right.map(value -> { throw thrown; })));
        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> both.bimap(value -> { throw thrown; }, value -> value)));
        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> neither.mapNeither(value -> { throw thrown; })));
        assertSame(thrown, assertThrows(IllegalStateException.class,
                () -> neither.fold(v -> "", v -> "", (a, b) -> "", v -> { throw thrown; })));

        // The missing right payload is not treated as an exception path.
        assertEquals(left, left.map(value -> { throw thrown; }));
        assertEquals(neither, neither.map(value -> { throw thrown; }));
    }
}
