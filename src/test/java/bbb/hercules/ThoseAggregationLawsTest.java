package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ThoseAggregationLawsTest {

    private static <A> List<A> append(List<A> first, List<A> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static final ThoseAggregation<List<Integer>, List<Long>, List<String>> POLICY =
            new ThoseAggregation<>(ThoseAggregationLawsTest::append,
                    ThoseAggregationLawsTest::append, ThoseAggregationLawsTest::append);

    private static List<Those<List<Integer>, List<Long>, List<String>>> four(
            int left, long right, String reason) {
        return List.of(
                Those.left(List.of(left)),
                Those.right(List.of(right)),
                Those.both(List.of(left), List.of(right)),
                Those.neither(List.of(reason)));
    }

    private static List<These<List<String>, These<List<Integer>, List<Long>>>> seven(
            int left, long right, String reason) {
        List<Integer> l = List.of(left);
        List<Long> r = List.of(right);
        List<String> e = List.of(reason);
        return List.of(
                These.left(e),
                These.right(These.left(l)),
                These.right(These.right(r)),
                These.right(These.both(l, r)),
                These.both(e, These.left(l)),
                These.both(e, These.right(r)),
                These.both(e, These.both(l, r)));
    }

    @RepeatedTest(32)
    void aggregationIsAssociativeAcrossAllSevenRepresentableCases(
            @Given int left1, @Given int left2, @Given int left3,
            @Given long right1, @Given long right2, @Given long right3,
            @Given String reason1, @Given String reason2, @Given String reason3) {
        for (var first : seven(left1, right1, reason1)) {
            for (var second : seven(left2, right2, reason2)) {
                for (var third : seven(left3, right3, reason3)) {
                    assertEquals(
                            POLICY.combine(POLICY.combine(first, second), third),
                            POLICY.combine(first, POLICY.combine(second, third)));
                }
            }
        }
    }

    @RepeatedTest(32)
    void liftingAndAggregatingAllFourCasesPreserveProvenanceAndAssociativity(
            @Given int left1, @Given int left2, @Given int left3,
            @Given long right1, @Given long right2, @Given long right3,
            @Given String reason1, @Given String reason2, @Given String reason3) {
        for (var first : four(left1, right1, reason1)) {
            for (var second : four(left2, right2, reason2)) {
                for (var third : four(left3, right3, reason3)) {
                    var a = POLICY.lift(first);
                    var b = POLICY.lift(second);
                    var c = POLICY.lift(third);

                    assertEquals(POLICY.combine(a, b), POLICY.combine(first, second));
                    assertEquals(POLICY.combine(POLICY.combine(a, b), c),
                            POLICY.combine(a, POLICY.combine(b, c)));
                    assertEquals(POLICY.combine(POLICY.combine(a, b), c),
                            POLICY.combineAll(List.of(first, second, third)));
                }
            }
        }
    }

    @RepeatedTest(64)
    void singletonAggregateIsExactLift(
            @Given Integer left, @Given Long right, @Given String reason) {
        for (var input : four(left, right, reason)) {
            assertEquals(POLICY.lift(input), POLICY.combineAll(List.of(input)));
            assertEquals(POLICY.lift(input), POLICY.lift(Those.fromEither(input.toEither())));
        }
    }

    @Test
    void timedOutSourceAndSuccessfulSourceKeepExplanationAlongsideEvidence() {
        var timeout = Those.<List<Integer>, List<Long>, List<String>>
                neither(List.of("source A timed out"));
        var success = Those.<List<Integer>, List<Long>, List<String>>
                right(List.of(42L));
        var secondTimeout = Those.<List<Integer>, List<Long>, List<String>>
                neither(List.of("source C unavailable"));

        var expected = These.<List<String>, These<List<Integer>, List<Long>>>both(
                List.of("source A timed out", "source C unavailable"),
                These.right(List.of(42L)));

        assertEquals(expected, POLICY.combineAll(List.of(timeout, success, secondTimeout)));
        assertEquals(expected, POLICY.combine(POLICY.combine(timeout, success),
                POLICY.lift(secondTimeout)));
        assertEquals(ThoseShape.RIGHT,
                ThoseShape.of(success).join(ThoseShape.of(timeout)));
        assertNotEquals(POLICY.lift(success), expected);
    }

    @Test
    void allThreePayloadChannelsMergeInEncounterOrder() {
        var entries = List.<Those<List<Integer>, List<Long>, List<String>>>of(
                Those.neither(List.of("timeout 1")),
                Those.both(List.of(1), List.of(10L)),
                Those.left(List.of(2)),
                Those.neither(List.of("timeout 2")),
                Those.right(List.of(20L)));

        assertEquals(These.both(
                List.of("timeout 1", "timeout 2"),
                These.both(List.of(1, 2), List.of(10L, 20L))),
                POLICY.combineAll(entries));
    }

    @Test
    void nonemptyInputIsRequiredBecauseErrorAndEvidenceHaveNoIdentity() {
        assertThrows(NoSuchElementException.class,
                () -> POLICY.combineAll(List.of()));
    }

    @Test
    void iterableIsVisitedOnceInEncounterOrderWithoutEarlyTermination() {
        var visits = new AtomicInteger();
        var iteratorCalls = new AtomicInteger();

        List<Those<List<Integer>, List<Long>, List<String>>> values = List.of(
                Those.neither(List.of("missing first")),
                Those.right(List.of(10L)),
                Those.neither(List.of("missing last")));

        Iterable<Those<List<Integer>, List<Long>, List<String>>> once = () -> {
            assertEquals(1, iteratorCalls.incrementAndGet());
            Iterator<Those<List<Integer>, List<Long>, List<String>>> delegate = values.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return delegate.hasNext(); }
                @Override public Those<List<Integer>, List<Long>, List<String>> next() {
                    visits.incrementAndGet();
                    return delegate.next();
                }
            };
        };

        assertEquals(These.both(
                List.of("missing first", "missing last"),
                These.right(List.of(10L))), POLICY.combineAll(once));
        assertEquals(1, iteratorCalls.get());
        assertEquals(3, visits.get());
    }

    @Test
    void callbackReferencesAndInputStructuresAreStrictlyNonNull() {
        assertThrows(NullPointerException.class,
                () -> new ThoseAggregation<List<Integer>, List<Long>, List<String>>(
                        null, ThoseAggregationLawsTest::append, ThoseAggregationLawsTest::append));
        assertThrows(NullPointerException.class,
                () -> new ThoseAggregation<List<Integer>, List<Long>, List<String>>(
                        ThoseAggregationLawsTest::append, null, ThoseAggregationLawsTest::append));
        assertThrows(NullPointerException.class,
                () -> new ThoseAggregation<List<Integer>, List<Long>, List<String>>(
                        ThoseAggregationLawsTest::append, ThoseAggregationLawsTest::append, null));

        var example = Those.<List<Integer>, List<Long>, List<String>>neither(List.of("missing"));
        var aggregate = POLICY.lift(example);
        assertThrows(NullPointerException.class, () -> POLICY.lift(null));
        assertThrows(NullPointerException.class, () -> POLICY.combine(example, null));
        assertThrows(NullPointerException.class,
                () -> POLICY.combine((These<List<String>, These<List<Integer>, List<Long>>>) null,
                        aggregate));
        assertThrows(NullPointerException.class, () -> POLICY.combineAll(null));
        assertThrows(NullPointerException.class,
                () -> POLICY.combineAll(Arrays.asList(example, null)));
        assertThrows(NullPointerException.class,
                () -> POLICY.combine(These.right(null), aggregate));
    }

    @Test
    void exceptionsFromCombinersAndIteratorsPropagateUnchanged() {
        var failure = new IllegalStateException("user combiner");
        var throwing = new ThoseAggregation<List<Integer>, List<Long>, List<String>>(
                (a, b) -> { throw failure; },
                ThoseAggregationLawsTest::append,
                ThoseAggregationLawsTest::append);
        var one = Those.<List<Integer>, List<Long>, List<String>>left(List.of(1));
        var two = Those.<List<Integer>, List<Long>, List<String>>left(List.of(2));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> throwing.combine(one, two)));

        Iterable<Those<List<Integer>, List<Long>, List<String>>> broken = () ->
                new Iterator<>() {
                    @Override public boolean hasNext() { throw failure; }
                    @Override public Those<List<Integer>, List<Long>, List<String>> next() {
                        return one;
                    }
                };
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> POLICY.combineAll(broken)));
    }

    @Test
    void explicitNullablePayloadsRemainSupported() {
        ThoseAggregation<@Nullable String, @Nullable Long, @Nullable String> app =
                new ThoseAggregation<>((a, b) -> a, (a, b) -> b, (a, b) -> a);

        Those<@Nullable String, @Nullable Long, @Nullable String> neither = Those.neither(null);
        Those<@Nullable String, @Nullable Long, @Nullable String> right = Those.right(null);

        assertEquals(These.both(null, These.right(null)), app.combine(neither, right));
        assertEquals(These.both(null, These.right(null)), app.combineAll(List.of(neither, right)));
    }

    @Test
    void combinerOrderIsObservableAndNotCommutative() {
        var policy = new ThoseAggregation<String, String, String>(
                (x, y) -> x + ">" + y, (x, y) -> x + ">" + y,
                (x, y) -> x + ">" + y);
        var first = Those.<String, String, String>neither("A");
        var second = Those.<String, String, String>neither("B");
        assertEquals(These.left("A>B"), policy.combine(first, second));
        assertEquals(These.left("B>A"), policy.combine(second, first));
    }
}
