package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationTraversalLawsTest {

    private static final ValidationApplicative<List<String>> AP =
            new ValidationApplicative<>((left, right) ->
                    Stream.concat(left.stream(), right.stream()).toList());

    private static <A> List<Validation<List<String>, A>> variants(String error, A valid) {
        return List.of(Validation.invalid(List.of(error)), Validation.valid(valid));
    }

    @Test
    void emptySequenceAndTraverseReturnValidImmutableEmptyList() {
        Validation<List<String>, List<String>> sequenced =
                AP.sequence(List.<Validation<List<String>, String>>of());
        Validation<List<String>, List<String>> traversed =
                AP.traverse(List.<String>of(), value -> {
                    fail("empty input should not invoke mapper");
                    return Validation.valid(value);
                });

        assertEquals(Validation.valid(List.of()), sequenced);
        assertEquals(sequenced, traversed);
        assertThrows(UnsupportedOperationException.class,
                () -> sequenced.fold(e -> fail("unexpected invalid"), values -> values.add("x")));
    }

    @RepeatedTest(64)
    void traversalPreservesPureAndMapCoherence(
            @Given Integer a, @Given Integer b, @Given Integer c) {
        List<Integer> values = List.of(a, b, c);
        List<Validation<List<String>, Integer>> successful =
                values.stream().<Validation<List<String>, Integer>>map(Validation::valid).toList();

        assertEquals(AP.pure(values), AP.sequence(successful));
        assertEquals(AP.pure(values), AP.traverse(values, AP::pure));
        assertEquals(AP.sequence(successful),
                AP.traverse(values, Validation::valid));

        Function<Integer, Integer> f = n -> n * 2 + 1;
        assertEquals(AP.sequence(successful).map(xs -> xs.stream().map(f).toList()),
                AP.traverse(values, n -> Validation.valid(f.apply(n))));
    }

    @RepeatedTest(64)
    void sequenceIsCompatibleWithMap2AndAllEightThreeElementVariants(
            @Given String e1, @Given String e2, @Given String e3,
            @Given Integer a, @Given Integer b, @Given Integer c) {
        for (var first : variants(e1, a)) {
            for (var second : variants(e2, b)) {
                for (var third : variants(e3, c)) {
                    Validation<List<String>, List<Integer>> expected = AP.map2(
                            AP.map2(first, second, (x, y) -> List.of(x, y)),
                            third, (xs, z) -> List.of(xs.get(0), xs.get(1), z));

                    var input = List.of(first, second, third);
                    assertEquals(expected, AP.sequence(input));
                    assertEquals(expected, AP.traverse(input, Function.identity()));
                }
            }
        }
    }

    @RepeatedTest(64)
    void orderOfAllFailuresIsStableAndNoIdentityIsNeeded(
            @Given String e1, @Given String e2, @Given String e3,
            @Given Integer n) {
        var invalid1 = Validation.<List<String>, Integer>invalid(List.of(e1));
        var invalid2 = Validation.<List<String>, Integer>invalid(List.of(e2));
        var invalid3 = Validation.<List<String>, Integer>invalid(List.of(e3));
        var valid = Validation.<List<String>, Integer>valid(n);

        assertEquals(Validation.invalid(List.of(e1, e2, e3)),
                AP.sequence(List.of(invalid1, invalid2, invalid3)));
        assertEquals(Validation.invalid(List.of(e1, e2, e3)),
                AP.sequence(List.of(invalid1, valid, invalid2, valid, invalid3)));
        assertEquals(Validation.invalid(List.of(e2)),
                AP.sequence(List.of(valid, invalid2, valid)));
        assertEquals(Validation.valid(List.of(n, n)),
                AP.sequence(List.of(valid, valid)));
    }

    @Test
    void resultsAreImmutableSnapshotsButNullablePayloadsRemainValid() {
        var mutable = new ArrayList<Validation<String, @Nullable String>>();
        mutable.add(Validation.valid(null));
        mutable.add(Validation.valid("ok"));

        ValidationApplicative<String> app = new ValidationApplicative<>(String::concat);
        Validation<String, List<@Nullable String>> result = app.sequence(mutable);
        assertEquals(Validation.valid(Arrays.asList(null, "ok")), result);

        List<@Nullable String> snapshot = result.fold(
                e -> fail("unexpected invalid"),
                Function.identity());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("new"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.set(0, "changed"));

        mutable.clear();
        assertEquals(Arrays.asList(null, "ok"), snapshot);

        List<@Nullable String> inputs = Arrays.asList(null, "ok");
        Validation<String, List<@Nullable String>> traversed =
                app.traverse(inputs, Validation::valid);
        assertEquals(result, traversed);
    }

    @RepeatedTest(64)
    void sequenceRespectsConcatenationInBothSuccessAndFailureCases(
            @Given String errorA, @Given String errorB,
            @Given Integer first, @Given Integer second) {
        for (Validation<List<String>, Integer> left : variants(errorA, first)) {
            for (Validation<List<String>, Integer> right : variants(errorB, second)) {
                var firstSegment = List.of(left, Validation.<List<String>, Integer>valid(first));
                var nextSegment = List.of(right, Validation.<List<String>, Integer>valid(second));

                var combined = Stream.concat(firstSegment.stream(), nextSegment.stream()).toList();
                var independent = AP.map2(
                        AP.sequence(firstSegment),
                        AP.sequence(nextSegment),
                        (xs, ys) -> Stream.concat(xs.stream(), ys.stream()).toList());

                assertEquals(independent, AP.sequence(combined));
            }
        }
    }

    @Test
    void largeFiniteSequenceIsIterativeAndKeepsOrder() {
        List<Validation<Integer, Integer>> many = new ArrayList<>();
        for (int n = 0; n < 10_000; n++) {
            many.add(Validation.valid(n));
        }
        var app = new ValidationApplicative<Integer>(Integer::sum);
        List<Integer> results = app.sequence(many).fold(
                failure -> fail("unexpected invalid"),
                Function.identity());

        assertEquals(10_000, results.size());
        assertEquals(0, results.get(0));
        assertEquals(9_999, results.get(9_999));
    }

    @Test
    void explicitlyNullableErrorsAreNotRejected() {
        ValidationApplicative<@Nullable String> app =
                new ValidationApplicative<>((left, right) -> left == null ? right : left);
        Validation<@Nullable String, Integer> one = Validation.invalid(null);
        Validation<@Nullable String, Integer> two = Validation.invalid("problem");

        assertEquals(Validation.invalid("problem"),
                app.sequence(List.of(one, Validation.valid(7), two)));
    }
}
