package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationTraversalBehaviorTest {

    @RepeatedTest(64)
    void traverseExecutesOncePerElementAndContinuesAfterFailures(
            @Given String prefix, @Given Integer original) {
        var calls = new ArrayList<Integer>();
        var combined = new ArrayList<String>();
        var app = new ValidationApplicative<String>((a, b) -> {
            combined.add(a + "|" + b);
            return a + "|" + b;
        });

        var input = List.of(1, 2, 3, 4, 5);
        var result = app.traverse(input, number -> {
            calls.add(number);
            return number % 2 == 0
                    ? Validation.<String, Integer>valid(number + original)
                    : Validation.<String, Integer>invalid(prefix + number);
        });

        assertEquals(input, calls);
        assertEquals(Validation.invalid(prefix + "1|" + prefix + "3|" + prefix + "5"), result);
        assertEquals(List.of(prefix + "1|" + prefix + "3",
                prefix + "1|" + prefix + "3|" + prefix + "5"), combined);
    }

    @Test
    void sequenceReadsIterableOnceInOrderAndCombinesEveryFailure() {
        var iteratorCalls = new AtomicInteger();
        var nextCalls = new AtomicInteger();
        var combinationCalls = new AtomicInteger();

        var app = new ValidationApplicative<String>((a, b) -> {
            combinationCalls.incrementAndGet();
            return a + " then " + b;
        });

        var values = List.<Validation<String, Integer>>of(
                Validation.valid(1),
                Validation.invalid("A"),
                Validation.valid(2),
                Validation.invalid("B"),
                Validation.invalid("C"));

        Iterable<Validation<String, Integer>> once = () -> {
            assertEquals(1, iteratorCalls.incrementAndGet());
            Iterator<Validation<String, Integer>> delegate = values.iterator();
            return new Iterator<>() {
                @Override public boolean hasNext() { return delegate.hasNext(); }
                @Override public Validation<String, Integer> next() {
                    nextCalls.incrementAndGet();
                    return delegate.next();
                }
            };
        };

        assertEquals(Validation.invalid("A then B then C"), app.sequence(once));
        assertEquals(1, iteratorCalls.get());
        assertEquals(values.size(), nextCalls.get());
        assertEquals(2, combinationCalls.get());
    }

    @Test
    void allSuccessfulMapperResultsAreStoredInEncounterOrderAndCopied() {
        var app = new ValidationApplicative<String>(String::concat);
        var mapped = new ArrayList<String>();
        var inputs = new ArrayList<>(List.of(7, 3, 9));

        var result = app.traverse(inputs, n -> {
            mapped.add("visited " + n);
            return Validation.<String, Integer>valid(n * n);
        });

        assertEquals(Validation.valid(List.of(49, 9, 81)), result);
        assertEquals(List.of("visited 7", "visited 3", "visited 9"), mapped);

        inputs.clear();
        List<Integer> values = result.fold(e -> fail("unexpected invalid"), Function.identity());
        assertEquals(List.of(49, 9, 81), values);
        assertThrows(UnsupportedOperationException.class, () -> values.add(2));
    }

    @Test
    void nullContainersAndMapperReferencesFailEagerlyAndStructuralNullsWhenEncountered() {
        var app = new ValidationApplicative<String>(String::concat);

        assertThrows(NullPointerException.class,
                () -> app.sequence(null));
        assertThrows(NullPointerException.class,
                () -> app.traverse(null, x -> Validation.valid(x)));
        assertThrows(NullPointerException.class,
                () -> app.traverse(List.<Integer>of(), null));

        var steps = new AtomicInteger();
        var sequence = Arrays.<Validation<String, Integer>>asList(
                Validation.valid(1), null, Validation.valid(3));
        assertThrows(NullPointerException.class, () -> app.sequence(sequence));
        assertThrows(NullPointerException.class,
                () -> app.traverse(List.of(1, 2, 3), n -> {
                    steps.incrementAndGet();
                    return n == 2 ? null : Validation.valid(n);
                }));
        assertEquals(2, steps.get(), "fail on evaluated structural null");
    }

    @Test
    void iteratorMapperAndCombinerExceptionsPropagateAsTheOriginalThrowable() {
        var app = new ValidationApplicative<String>(String::concat);
        var fromIterator = new IllegalStateException("iterator");
        Iterable<Integer> broken = () -> new Iterator<>() {
            @Override public boolean hasNext() { throw fromIterator; }
            @Override public Integer next() { return 0; }
        };
        assertSame(fromIterator, assertThrows(IllegalStateException.class,
                () -> app.traverse(broken, Validation::valid)));

        var fromMapper = new IllegalStateException("mapper");
        assertSame(fromMapper, assertThrows(IllegalStateException.class,
                () -> app.traverse(List.of(1, 2), n -> { throw fromMapper; })));

        var fromCombiner = new IllegalStateException("combiner");
        var failing = new ValidationApplicative<String>(
                (a, b) -> { throw fromCombiner; });
        assertSame(fromCombiner, assertThrows(IllegalStateException.class,
                () -> failing.sequence(List.of(
                        Validation.<String, Integer>invalid("a"),
                        Validation.<String, Integer>invalid("b")))));
    }

    @Test
    void noShortCircuitEvenWhenAllElementsFail() {
        var app = new ValidationApplicative<String>((a, b) -> a + b);
        var visited = new AtomicInteger();

        var result = app.traverse(List.of("a", "b", "c"), s -> {
            visited.incrementAndGet();
            return Validation.<String, String>invalid(s);
        });

        assertEquals(3, visited.get());
        assertEquals(Validation.invalid("abc"), result);
    }
}
