package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationMapNLawsTest {

    private static final ValidationApplicative<List<String>> AP =
            new ValidationApplicative<>((a, b) ->
                    Stream.concat(a.stream(), b.stream()).toList());

    /**
     * Every subset of failed arguments must produce exactly those errors in
     * their original order, and the mapper must run once iff no errors occur.
     */
    private static void verifyAllFailurePatterns(
            int arity, int offset, String prefix,
            BiFunction<List<Validation<List<String>, Integer>>, AtomicInteger,
                    Validation<List<String>, Integer>> invoke) {
        int sum = IntStream.range(0, arity).map(i -> offset + i).sum();

        for (int failureMask = 0; failureMask < (1 << arity); failureMask++) {
            List<Validation<List<String>, Integer>> inputs = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < arity; i++) {
                if ((failureMask & (1 << i)) != 0) {
                    String error = prefix + ":" + i;
                    inputs.add(Validation.invalid(List.of(error)));
                    errors.add(error);
                } else {
                    inputs.add(Validation.valid(offset + i));
                }
            }

            AtomicInteger mapped = new AtomicInteger();
            Validation<List<String>, Integer> actual = invoke.apply(inputs, mapped);
            Validation<List<String>, Integer> expected = errors.isEmpty()
                    ? Validation.valid(sum)
                    : Validation.invalid(List.copyOf(errors));

            assertEquals(expected, actual, "arity=" + arity + ", mask=" + failureMask);
            assertEquals(errors.isEmpty() ? 1 : 0, mapped.get(),
                    "wrong mapper invocation count for mask " + failureMask);
        }
    }

    @RepeatedTest(8)
    void map3AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(3, offset, prefix,
                (v, calls) -> AP.map3(v.get(0), v.get(1), v.get(2), (a, b, c) -> {
                    calls.incrementAndGet();
                    return a + b + c;
                }));
    }

    @RepeatedTest(8)
    void map4AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(4, offset, prefix,
                (v, calls) -> AP.map4(v.get(0), v.get(1), v.get(2), v.get(3),
                        (a, b, c, d) -> {
                            calls.incrementAndGet();
                            return a + b + c + d;
                        }));
    }

    @RepeatedTest(8)
    void map5AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(5, offset, prefix,
                (v, calls) -> AP.map5(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        (a, b, c, d, f) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f;
                        }));
    }

    @RepeatedTest(8)
    void map6AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(6, offset, prefix,
                (v, calls) -> AP.map6(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        v.get(5), (a, b, c, d, f, g) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f + g;
                        }));
    }

    @RepeatedTest(8)
    void map7AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(7, offset, prefix,
                (v, calls) -> AP.map7(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        v.get(5), v.get(6), (a, b, c, d, f, g, h) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f + g + h;
                        }));
    }

    @RepeatedTest(8)
    void map8AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(8, offset, prefix,
                (v, calls) -> AP.map8(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        v.get(5), v.get(6), v.get(7),
                        (a, b, c, d, f, g, h, i) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f + g + h + i;
                        }));
    }

    @RepeatedTest(8)
    void map9AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(9, offset, prefix,
                (v, calls) -> AP.map9(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        v.get(5), v.get(6), v.get(7), v.get(8),
                        (a, b, c, d, f, g, h, i, j) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f + g + h + i + j;
                        }));
    }

    @RepeatedTest(8)
    void map10AllVariants(@Given Integer offset, @Given String prefix) {
        verifyAllFailurePatterns(10, offset, prefix,
                (v, calls) -> AP.map10(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4),
                        v.get(5), v.get(6), v.get(7), v.get(8), v.get(9),
                        (a, b, c, d, f, g, h, i, j, k) -> {
                            calls.incrementAndGet();
                            return a + b + c + d + f + g + h + i + j + k;
                        }));
    }

    @RepeatedTest(64)
    void map3AgreesWithMap2AndAp(@Given Integer a, @Given Integer b, @Given Integer c,
                                  @Given String firstError, @Given String secondError) {
        List<Validation<List<String>, Integer>> variantsA = List.of(
                Validation.valid(a), Validation.invalid(List.of(firstError)));
        List<Validation<List<String>, Integer>> variantsB = List.of(
                Validation.valid(b), Validation.invalid(List.of(secondError)));

        for (var first : variantsA) {
            for (var second : variantsB) {
                Validation<List<String>, Integer> third = Validation.valid(c);
                var map3 = AP.map3(first, second, third, (x, y, z) -> x + y + z);
                var map2 = AP.map2(AP.map2(first, second, Integer::sum),
                        third, Integer::sum);
                assertEquals(map2, map3);
            }
        }
    }

    @Test
    void mapperExceptionsPropagateAndOnlyRunWhenAllValid() {
        var failure = new IllegalStateException("mapper failed");
        var valid = Validation.<String, Integer>valid(1);
        var invalid = Validation.<String, Integer>invalid("error");
        var app = new ValidationApplicative<String>(String::concat);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> app.map3(valid, valid, valid, (a, b, c) -> { throw failure; })));
        assertEquals(invalid,
                app.map3(invalid, valid, valid, (a, b, c) -> { throw failure; }));
    }

    @Test
    void mapperAndContainersMustBeNonNullEvenWhenAnEarlierValueIsInvalid() {
        var app = new ValidationApplicative<String>(String::concat);
        var invalid = Validation.<String, Integer>invalid("failed");
        var valid = Validation.<String, Integer>valid(1);

        assertThrows(NullPointerException.class,
                () -> app.map3(invalid, valid, valid, null));
        assertThrows(NullPointerException.class,
                () -> app.map3(invalid, null, valid, (a, b, c) -> 1));
        assertThrows(NullPointerException.class,
                () -> app.map10(invalid, valid, valid, valid, valid, valid,
                        valid, valid, valid, null, (a, b, c, d, f, g, h, i, j, k) -> 1));
        assertThrows(NullPointerException.class,
                () -> app.map10(invalid, valid, valid, valid, valid, valid,
                        valid, valid, valid, valid, null));
    }

    @Test
    void errorCombinerExceptionsPropagateWithoutMapperExecution() {
        var failure = new IllegalStateException("combination failed");
        var app = new ValidationApplicative<String>((a, b) -> { throw failure; });
        var invalid = Validation.<String, Integer>invalid("error");
        var mapped = new AtomicInteger();

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> app.map4(invalid, invalid, invalid, invalid, (a, b, c, d) -> {
                    mapped.incrementAndGet();
                    return 0;
                })));
        assertEquals(0, mapped.get());
    }

    @Test
    void explicitNullableResultTypesRemainValidWhenMapperReturnsNull() {
        ValidationApplicative<String> app = new ValidationApplicative<>(String::concat);
        var valid = Validation.<String, Integer>valid(1);

        Validation<String, @org.jspecify.annotations.Nullable Integer> mapped =
                app.map3(valid, valid, valid, (a, b, c) -> null);
        assertEquals(Validation.valid(null), mapped);
    }
}
