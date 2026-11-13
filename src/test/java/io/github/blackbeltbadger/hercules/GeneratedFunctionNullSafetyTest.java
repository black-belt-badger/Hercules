package io.github.blackbeltbadger.hercules;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Null contracts across all generated arities; edit the generator, not generated code. */
class GeneratedFunctionNullSafetyTest {

    @Test
    void mapAndFlatMapRejectNullReferencesAtCompositionTimeForEveryArity() {
        F3<Integer, Integer, Integer, Integer> f3 = (a, b, c) -> a;
        F4<Integer, Integer, Integer, Integer, Integer> f4 = (a, b, c, d) -> a;
        F5<Integer, Integer, Integer, Integer, Integer, Integer> f5 =
                (a, b, c, d, e) -> a;
        F6<Integer, Integer, Integer, Integer, Integer, Integer, Integer> f6 =
                (a, b, c, d, e, f) -> a;
        F7<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> f7 =
                (a, b, c, d, e, f, g) -> a;
        F8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> f8 =
                (a, b, c, d, e, f, g, h) -> a;
        F9<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer,
                Integer, Integer> f9 = (a, b, c, d, e, f, g, h, i) -> a;
        F10<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer,
                Integer, Integer, Integer> f10 = (a, b, c, d, e, f, g, h, i, j) -> a;

        assertThrows(NullPointerException.class, () -> f3.map(null));
        assertThrows(NullPointerException.class, () -> f4.map(null));
        assertThrows(NullPointerException.class, () -> f5.map(null));
        assertThrows(NullPointerException.class, () -> f6.map(null));
        assertThrows(NullPointerException.class, () -> f7.map(null));
        assertThrows(NullPointerException.class, () -> f8.map(null));
        assertThrows(NullPointerException.class, () -> f9.map(null));
        assertThrows(NullPointerException.class, () -> f10.map(null));

        assertThrows(NullPointerException.class, () -> f3.flatMap(null));
        assertThrows(NullPointerException.class, () -> f4.flatMap(null));
        assertThrows(NullPointerException.class, () -> f5.flatMap(null));
        assertThrows(NullPointerException.class, () -> f6.flatMap(null));
        assertThrows(NullPointerException.class, () -> f7.flatMap(null));
        assertThrows(NullPointerException.class, () -> f8.flatMap(null));
        assertThrows(NullPointerException.class, () -> f9.flatMap(null));
        assertThrows(NullPointerException.class, () -> f10.flatMap(null));

        assertThrows(NullPointerException.class, () -> f3.compose1(null));
        assertThrows(NullPointerException.class, () -> f4.compose2(null));
        assertThrows(NullPointerException.class, () -> f5.compose3(null));
        assertThrows(NullPointerException.class, () -> f6.compose4(null));
        assertThrows(NullPointerException.class, () -> f7.compose5(null));
        assertThrows(NullPointerException.class, () -> f8.compose6(null));
        assertThrows(NullPointerException.class, () -> f9.compose7(null));
        assertThrows(NullPointerException.class, () -> f10.compose10(null));
    }

    @Test
    void uncurryRejectsNullFunctionAtConstructionTime() {
        assertThrows(NullPointerException.class,
                () -> F3.<Integer, Integer, Integer, Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F4.<Integer, Integer, Integer, Integer, Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F5.<Integer, Integer, Integer, Integer, Integer, Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F6.<Integer, Integer, Integer, Integer, Integer, Integer, Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F7.<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer>
                        uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F8.<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer,
                        Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F9.<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer,
                        Integer, Integer>uncurry(null));
        assertThrows(NullPointerException.class,
                () -> F10.<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer,
                        Integer, Integer, Integer>uncurry(null));
    }

    @Test
    void flatMapNullStructuralResultIsCheckedOnlyWhenFunctionIsEvaluated() {
        F3<Integer, Integer, Integer, Integer> sum = (a, b, c) -> a + b + c;
        F3<Integer, Integer, Integer, String> nested = sum.flatMap(x -> null);
        assertThrows(NullPointerException.class, () -> nested.apply(1, 2, 3));
    }

    @Test
    void nullableGenericPayloadsRemainDistinctFromRequiredCallbackReferences() {
        F3<@Nullable String, @Nullable Integer, @Nullable Long, @Nullable String> first =
                (a, b, c) -> a;
        assertNull(first.apply(null, null, null));

        F3<Integer, Integer, Integer, @Nullable String> result =
                ((F3<Integer, Integer, Integer, Integer>) (a, b, c) -> a).map(x -> null);
        assertNull(result.apply(1, 2, 3));

        Function<@Nullable String, @Nullable String> identity = x -> x;
        assertNull(first.compose1(identity).apply(null, null, null));
    }
}
