package io.github.blackbeltbadger.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationApplicativeLawsTest {

    private static final ValidationApplicative<List<String>> AP =
            new ValidationApplicative<>((left, right) ->
                    Stream.concat(left.stream(), right.stream()).toList());

    private static <A> List<Validation<List<String>, A>> variants(String error, A value) {
        return List.of(Validation.invalid(List.of(error)), Validation.valid(value));
    }

    @RepeatedTest(64)
    void applicativeIdentity(@Given String error, @Given Integer value) {
        Function<Integer, Integer> identity = Function.identity();
        for (Validation<List<String>, Integer> item : variants(error, value)) {
            assertEquals(item, AP.ap(AP.pure(identity), item));
        }
    }

    @RepeatedTest(64)
    void applicativeCompositionAllEightCombinations(
            @Given String errorU, @Given String errorV, @Given String errorW,
            @Given Integer value) {
        Function<Integer, Integer> f = n -> n + 7;
        Function<Integer, Integer> g = n -> n * 3;
        Function<Function<Integer, Integer>,
                Function<Function<Integer, Integer>, Function<Integer, Integer>>>
                compose = outer -> inner -> x -> outer.apply(inner.apply(x));

        for (Validation<List<String>, Function<Integer, Integer>> u : variants(errorU, f)) {
            for (Validation<List<String>, Function<Integer, Integer>> v : variants(errorV, g)) {
                for (Validation<List<String>, Integer> w : variants(errorW, value)) {
                    var left = AP.ap(AP.ap(AP.ap(AP.pure(compose), u), v), w);
                    var right = AP.ap(u, AP.ap(v, w));
                    assertEquals(right, left);
                }
            }
        }
    }

    @RepeatedTest(64)
    void applicativeHomomorphismAndInterchange(
            @Given String error, @Given Integer value) {
        Function<Integer, Integer> f = x -> x * 2 + 1;
        assertEquals(AP.pure(f.apply(value)),
                AP.ap(AP.pure(f), AP.pure(value)));

        Function<Function<Integer, Integer>, Integer> applyToValue =
                function -> function.apply(value);
        for (Validation<List<String>, Function<Integer, Integer>> u : variants(error, f)) {
            assertEquals(
                    AP.ap(u, AP.pure(value)),
                    AP.ap(AP.pure(applyToValue), u));
        }
    }

    @RepeatedTest(64)
    void apAgreesWithFunctorMapAndMap2(
            @Given String error, @Given Integer value) {
        Function<Integer, Integer> f = x -> x - 10;
        for (Validation<List<String>, Integer> item : variants(error, value)) {
            assertEquals(item.map(f), AP.ap(AP.pure(f), item));
            assertEquals(item.map(f), AP.map2(AP.pure(f), item, Function::apply));
        }
    }

    @RepeatedTest(64)
    void map2CombinesAllFourCasesAndAssociatesInEncounterOrder(
            @Given String errorA, @Given String errorB, @Given String errorC,
            @Given Integer a, @Given Integer b, @Given Integer c) {
        for (Validation<List<String>, Integer> first : variants(errorA, a)) {
            for (Validation<List<String>, Integer> second : variants(errorB, b)) {
                for (Validation<List<String>, Integer> third : variants(errorC, c)) {
                    assertEquals(
                            AP.map2(AP.map2(first, second, Integer::sum),
                                    third, Integer::sum),
                            AP.map2(first,
                                    AP.map2(second, third, Integer::sum), Integer::sum));
                }
            }
        }

        assertEquals(Validation.valid(a + b),
                AP.map2(Validation.valid(a), Validation.valid(b), Integer::sum));
        assertEquals(Validation.invalid(List.of(errorA)),
                AP.map2(Validation.invalid(List.of(errorA)), Validation.valid(b), Integer::sum));
        assertEquals(Validation.invalid(List.of(errorB)),
                AP.map2(Validation.valid(a), Validation.invalid(List.of(errorB)), Integer::sum));
        assertEquals(Validation.invalid(List.of(errorA, errorB)),
                AP.map2(Validation.invalid(List.of(errorA)),
                        Validation.invalid(List.of(errorB)), Integer::sum));
    }

    @RepeatedTest(64)
    void apAccumulatesFunctionErrorsBeforeValueErrors(
            @Given String errorFunction, @Given String errorValue,
            @Given Integer value) {
        Function<Integer, Integer> f = n -> n + 1;
        Validation<List<String>, Function<Integer, Integer>> badFunction =
                Validation.invalid(List.of(errorFunction));
        Validation<List<String>, Integer> badValue =
                Validation.invalid(List.of(errorValue));

        assertEquals(Validation.invalid(List.of(errorFunction, errorValue)),
                AP.ap(badFunction, badValue));
        assertEquals(Validation.invalid(List.of(errorFunction)),
                AP.ap(badFunction, Validation.valid(value)));
        assertEquals(Validation.invalid(List.of(errorValue)),
                AP.ap(Validation.valid(f), badValue));
        assertEquals(Validation.valid(value + 1),
                AP.ap(Validation.valid(f), Validation.valid(value)));
    }

    @Test
    void mapperAndCombinerAreOnlyRunWhenApplicable() {
        var mapped = new AtomicInteger();
        var combined = new AtomicInteger();
        ValidationApplicative<String> policy = new ValidationApplicative<>((a, b) -> {
            combined.incrementAndGet();
            return a + " | " + b;
        });
        var first = Validation.<String, Integer>invalid("invalid A");
        var second = Validation.<String, Integer>invalid("invalid B");
        var good = Validation.<String, Integer>valid(5);

        assertEquals(Validation.invalid("invalid A | invalid B"),
                policy.map2(first, second, (a, b) -> {
                    mapped.incrementAndGet();
                    return a + b;
                }));
        assertEquals(1, combined.get());
        assertEquals(0, mapped.get());

        assertEquals(first, policy.map2(first, good, (a, b) -> {
            mapped.incrementAndGet();
            return a + b;
        }));
        assertEquals(second, policy.map2(good, second, (a, b) -> {
            mapped.incrementAndGet();
            return a + b;
        }));
        assertEquals(1, combined.get());
        assertEquals(0, mapped.get());

        assertEquals(Validation.valid(10), policy.map2(good, good, (a, b) -> {
            mapped.incrementAndGet();
            return a + b;
        }));
        assertEquals(1, mapped.get());
        assertEquals(1, combined.get());
    }

    @Test
    void strictInputChecksAndFailuresFromUserCodeArePreserved() {
        assertThrows(NullPointerException.class, () -> new ValidationApplicative<>(null));
        assertThrows(NullPointerException.class,
                () -> AP.map2(null, Validation.<List<String>, Integer>valid(1), Integer::sum));
        assertThrows(NullPointerException.class,
                () -> AP.map2(Validation.<List<String>, Integer>valid(1), null, Integer::sum));
        assertThrows(NullPointerException.class,
                () -> AP.map2(Validation.<List<String>, Integer>invalid(List.of("A")),
                        Validation.<List<String>, Integer>invalid(List.of("B")), null));
        assertThrows(NullPointerException.class,
                () -> AP.ap(null, Validation.<List<String>, Integer>valid(1)));
        assertThrows(NullPointerException.class,
                () -> AP.ap(Validation.<List<String>,
                        Function<Integer, Integer>>valid(Function.identity()), null));

        var failure = new IllegalStateException("from user");
        ValidationApplicative<String> throwsOnCombine =
                new ValidationApplicative<>((a, b) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> throwsOnCombine.map2(Validation.<String, Integer>invalid("A"),
                        Validation.<String, Integer>invalid("B"), Integer::sum)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> AP.map2(Validation.<List<String>, Integer>valid(1),
                        Validation.<List<String>, Integer>valid(2),
                        (a, b) -> { throw failure; })));
    }

    @Test
    void explicitNullableErrorTypeCanUseNullablePayloads() {
        ValidationApplicative<@Nullable String> policy = new ValidationApplicative<>(
                (a, b) -> a == null ? b : b == null ? a : a + b);
        Validation<@Nullable String, Integer> left = Validation.invalid(null);
        Validation<@Nullable String, Integer> right = Validation.invalid("error");

        assertEquals(Validation.invalid("error"), policy.map2(left, right, Integer::sum));
        assertInstanceOf(Validation.Invalid.class, policy.map2(left, right, Integer::sum));
    }

    @Test
    void thereIsNoRequiredIdentityForErrorCombination() {
        // A semigroup suffices: only two Invalid values ever need combining.
        ValidationApplicative<List<String>> nonEmptyErrors =
                new ValidationApplicative<>((a, b) ->
                        Stream.concat(a.stream(), b.stream()).toList());
        assertEquals(Validation.invalid(List.of("first", "second")),
                nonEmptyErrors.map2(Validation.invalid(List.of("first")),
                        Validation.invalid(List.of("second")), Integer::sum));
    }
}
