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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationStagesTest {

    private static final ValidationApplicative<List<String>> AP =
            new ValidationApplicative<>((first, second) ->
                    Stream.concat(first.stream(), second.stream()).toList());

    private static <A> List<Validation<List<String>, A>> variants(String error, A value) {
        return List.of(Validation.invalid(List.of(error)), Validation.valid(value));
    }

    @RepeatedTest(64)
    void map2StagesAgreesWithSynchronousMap2RegardlessOfCompletionOrder(
            @Given Integer a, @Given Integer b, @Given String errorA,
            @Given String errorB) {
        for (var first : variants(errorA, a)) {
            for (var second : variants(errorB, b)) {
                var left = new CompletableFuture<Validation<List<String>, Integer>>();
                var right = new CompletableFuture<Validation<List<String>, Integer>>();
                var mapperCalls = new AtomicInteger();
                var output = AP.map2Stages(left, right, (x, y) -> {
                    mapperCalls.incrementAndGet();
                    return x + y;
                });

                assertFalse(output.toCompletableFuture().isDone());
                right.complete(second);
                assertFalse(output.toCompletableFuture().isDone());
                left.complete(first);

                assertEquals(AP.map2(first, second, Integer::sum),
                        output.toCompletableFuture().join());
                int expectedCalls =
                        first instanceof Validation.Valid<?, ?>
                        && second instanceof Validation.Valid<?, ?> ? 1 : 0;
                assertEquals(expectedCalls, mapperCalls.get());
            }
        }
    }

    @RepeatedTest(64)
    void sequenceStagesAccumulatesAllErrorsInInputOrderNotCompletionOrder(
            @Given String firstError, @Given String secondError,
            @Given String thirdError, @Given Integer value) {
        var first = new CompletableFuture<Validation<List<String>, Integer>>();
        var second = new CompletableFuture<Validation<List<String>, Integer>>();
        var third = new CompletableFuture<Validation<List<String>, Integer>>();
        var fourth = new CompletableFuture<Validation<List<String>, Integer>>();
        var fifth = new CompletableFuture<Validation<List<String>, Integer>>();

        CompletionStage<Validation<List<String>, List<Integer>>> output =
                AP.sequenceStages(List.of(first, second, third, fourth, fifth));

        fifth.complete(Validation.invalid(List.of(thirdError)));
        fourth.complete(Validation.valid(value));
        third.complete(Validation.invalid(List.of(secondError)));
        second.complete(Validation.valid(value));
        assertFalse(output.toCompletableFuture().isDone());
        first.complete(Validation.invalid(List.of(firstError)));

        assertEquals(Validation.invalid(List.of(firstError, secondError, thirdError)),
                output.toCompletableFuture().join());
    }

    @RepeatedTest(64)
    void sequenceStagesAgreesWithSynchronousSequenceForNormalCompletions(
            @Given Integer a, @Given Integer b, @Given String errorA,
            @Given String errorB) {
        for (var first : variants(errorA, a)) {
            for (var second : variants(errorB, b)) {
                var values = List.of(first, Validation.<List<String>, Integer>valid(a),
                        second, Validation.<List<String>, Integer>valid(b));
                var stages = values.stream().map(CompletableFuture::completedFuture).toList();

                assertEquals(AP.sequence(values),
                        AP.sequenceStages(stages).toCompletableFuture().join());
            }
        }
    }

    @Test
    void emptyAndAllValidSequenceStagesProduceImmutableNullableSnapshots() {
        var empty = AP.<String>sequenceStages(
                List.<CompletionStage<Validation<List<String>, String>>>of())
                .toCompletableFuture().join();
        assertEquals(Validation.valid(List.of()), empty);

        var app = new ValidationApplicative<String>(String::concat);
        var initial = new ArrayList<CompletionStage<Validation<String, @Nullable Integer>>>();
        initial.add(CompletableFuture.completedFuture(Validation.valid(null)));
        initial.add(CompletableFuture.completedFuture(Validation.valid(4)));
        var result = app.sequenceStages(initial).toCompletableFuture().join();
        assertEquals(Validation.valid(Arrays.asList(null, 4)), result);

        List<@Nullable Integer> list = result.fold(
                err -> fail("unexpected Invalid"), Function.identity());
        assertThrows(UnsupportedOperationException.class, () -> list.add(5));
        initial.clear();
        assertEquals(Arrays.asList(null, 4), list);
    }

    @Test
    void map2StagesSupportsExplicitlyNullableSuccessfulPayloadsAndResults() {
        var app = new ValidationApplicative<String>(String::concat);
        CompletionStage<Validation<String, @Nullable Integer>> left =
                CompletableFuture.completedFuture(Validation.valid(null));
        CompletionStage<Validation<String, @Nullable Integer>> right =
                CompletableFuture.completedFuture(Validation.valid(3));

        Validation<String, @Nullable Integer> result = app.map2Stages(
                left, right, (a, b) -> a).toCompletableFuture().join();

        assertEquals(Validation.valid(null), result);
    }

    @Test
    void iterableIsEnumeratedExactlyOnceWhenStagesAreRegistered() {
        var iterationCount = new AtomicInteger();
        var nextCount = new AtomicInteger();
        var future = new CompletableFuture<Validation<String, Integer>>();
        Iterable<CompletionStage<Validation<String, Integer>>> iterable = () -> {
            iterationCount.incrementAndGet();
            Iterator<CompletionStage<Validation<String, Integer>>> original =
                    List.<CompletionStage<Validation<String, Integer>>>of(future).iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return original.hasNext(); }
                @Override public CompletionStage<Validation<String, Integer>> next() {
                    nextCount.incrementAndGet();
                    return original.next();
                }
            };
        };

        var app = new ValidationApplicative<String>(String::concat);
        var output = app.sequenceStages(iterable);
        assertEquals(1, iterationCount.get());
        assertEquals(1, nextCount.get());
        assertFalse(output.toCompletableFuture().isDone());
        future.complete(Validation.valid(12));
        assertEquals(Validation.valid(List.of(12)), output.toCompletableFuture().join());
    }

    @Test
    void stageInputsAndMapperReferencesAreStrictButNullResultsFailDependentStage() {
        var app = new ValidationApplicative<String>(String::concat);
        var good = CompletableFuture.completedFuture(Validation.<String, Integer>valid(2));

        assertThrows(NullPointerException.class, () -> app.map2Stages(null, good, Integer::sum));
        assertThrows(NullPointerException.class, () -> app.map2Stages(good, null, Integer::sum));
        assertThrows(NullPointerException.class, () -> app.map2Stages(good, good, null));
        assertThrows(NullPointerException.class, () -> app.sequenceStages(null));
        assertThrows(NullPointerException.class,
                () -> app.sequenceStages(Arrays.<CompletionStage<Validation<String, Integer>>>
                        asList(good, null)));

        CompletableFuture<Validation<String, Integer>> nullResult =
                CompletableFuture.completedFuture(null);
        assertInstanceOf(NullPointerException.class, assertThrows(
                CompletionException.class,
                () -> app.map2Stages(nullResult, good, Integer::sum)
                        .toCompletableFuture().join()).getCause());
        assertInstanceOf(NullPointerException.class, assertThrows(
                CompletionException.class,
                () -> app.sequenceStages(List.of(good, nullResult))
                        .toCompletableFuture().join()).getCause());
    }

    @Test
    void sourceExceptionsStayExceptionalAndNeverBecomeValidationErrors() {
        var app = new ValidationApplicative<String>(String::concat);
        var failure = new IllegalStateException("IO failed");
        var left = new CompletableFuture<Validation<String, Integer>>();
        var right = new CompletableFuture<Validation<String, Integer>>();
        var output = app.map2Stages(left, right, Integer::sum);

        left.completeExceptionally(failure);
        right.complete(Validation.invalid("ordinary validation failure"));

        assertSame(failure, assertThrows(CompletionException.class,
                () -> output.toCompletableFuture().join()).getCause());

        var exceptional = new CompletableFuture<Validation<String, Integer>>();
        var sequence = app.sequenceStages(
                List.of(CompletableFuture.completedFuture(Validation.<String, Integer>valid(1)),
                        exceptional));
        exceptional.completeExceptionally(failure);
        assertSame(failure, assertThrows(CompletionException.class,
                () -> sequence.toCompletableFuture().join()).getCause());
    }

    @Test
    void cancellationIsExceptionalAndDoesNotPropagateBackToSources() {
        var app = new ValidationApplicative<String>(String::concat);
        var left = new CompletableFuture<Validation<String, Integer>>();
        var right = new CompletableFuture<Validation<String, Integer>>();

        var output = app.map2Stages(left, right, Integer::sum).toCompletableFuture();
        assertTrue(output.cancel(false));
        assertFalse(left.isCancelled());
        assertFalse(right.isCancelled());
        assertFalse(left.isDone());
        assertFalse(right.isDone());

        var otherLeft = new CompletableFuture<Validation<String, Integer>>();
        var otherRight = new CompletableFuture<Validation<String, Integer>>();
        var dependent = app.map2Stages(otherLeft, otherRight, Integer::sum);
        assertTrue(otherLeft.cancel(false));
        otherRight.complete(Validation.valid(7));

        assertInstanceOf(CancellationException.class,
                assertThrows(CompletionException.class,
                        () -> dependent.toCompletableFuture().join()).getCause());
        assertFalse(otherRight.isCancelled());

        var first = new CompletableFuture<Validation<String, Integer>>();
        var second = new CompletableFuture<Validation<String, Integer>>();
        var collected = app.sequenceStages(List.of(first, second)).toCompletableFuture();
        assertTrue(collected.cancel(false));
        assertFalse(first.isCancelled());
        assertFalse(second.isCancelled());
    }

    @Test
    void combinerAndMapperExceptionsStayExceptional() {
        var failure = new IllegalArgumentException("user code");
        var throwsOnError = new ValidationApplicative<String>((a, b) -> { throw failure; });

        var bothInvalid = throwsOnError.map2Stages(
                CompletableFuture.completedFuture(Validation.<String, Integer>invalid("A")),
                CompletableFuture.completedFuture(Validation.<String, Integer>invalid("B")),
                Integer::sum);

        assertSame(failure, assertThrows(CompletionException.class,
                () -> bothInvalid.toCompletableFuture().join()).getCause());

        var app = new ValidationApplicative<String>(String::concat);
        var mapperCalls = new AtomicInteger();
        var successful = app.map2Stages(
                CompletableFuture.completedFuture(Validation.<String, Integer>valid(1)),
                CompletableFuture.completedFuture(Validation.<String, Integer>valid(2)),
                (a, b) -> {
                    mapperCalls.incrementAndGet();
                    throw failure;
                });
        assertSame(failure, assertThrows(CompletionException.class,
                () -> successful.toCompletableFuture().join()).getCause());
        assertEquals(1, mapperCalls.get());
    }

    @Test
    void worksWithCompletionStageOnlyViewsWithoutRequiringConcreteFutureInputs() {
        CompletionStage<Validation<List<String>, Integer>> left =
                CompletableFuture.completedFuture(
                        Validation.<List<String>, Integer>valid(7)).minimalCompletionStage();
        CompletionStage<Validation<List<String>, Integer>> right =
                CompletableFuture.completedFuture(
                        Validation.<List<String>, Integer>valid(5)).minimalCompletionStage();

        assertEquals(Validation.valid(12),
                AP.map2Stages(left, right, Integer::sum).toCompletableFuture().join());
        assertEquals(Validation.valid(List.of(7, 5)),
                AP.sequenceStages(List.of(left, right)).toCompletableFuture().join());
    }

    @Test
    void stagesAreSuppliedNotStartedAndCompletionIsNotBlocked() {
        var app = new ValidationApplicative<String>(String::concat);
        var pending = new CompletableFuture<Validation<String, Integer>>();
        var completed = CompletableFuture.completedFuture(
                Validation.<String, Integer>invalid("no value"));
        var callbackCount = new AtomicInteger();

        var output = app.map2Stages(completed, pending, (a, b) -> {
            callbackCount.incrementAndGet();
            return a + b;
        });

        assertFalse(output.toCompletableFuture().isDone(),
                "an Invalid does not terminate the combination before other stage completes");
        pending.complete(Validation.invalid("another failure"));

        assertEquals(Validation.invalid("no valueanother failure"),
                output.toCompletableFuture().join());
        assertEquals(0, callbackCount.get());
    }

    @Test
    void sequenceStagesWorksForLargeFiniteAlreadyCompletedInputs() {
        var app = new ValidationApplicative<String>(String::concat);
        var stages = new ArrayList<CompletionStage<Validation<String, Integer>>>();
        for (int n = 0; n < 2_000; n++) {
            stages.add(CompletableFuture.completedFuture(Validation.valid(n)));
        }

        var result = app.sequenceStages(stages).toCompletableFuture().join();
        List<Integer> values = result.fold(e -> fail("unexpected Invalid"), Function.identity());

        assertEquals(2_000, values.size());
        assertEquals(0, values.get(0));
        assertEquals(1_999, values.get(1_999));
    }
}
