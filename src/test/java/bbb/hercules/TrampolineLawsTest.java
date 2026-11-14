package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class TrampolineLawsTest {

    private static Trampoline<Long> sum(long remaining, long accumulated) {
        return remaining == 0
                ? Trampoline.done(accumulated)
                : Trampoline.more(() -> sum(remaining - 1, accumulated + remaining));
    }

    private static Trampoline<Boolean> isEven(int count) {
        return count == 0
                ? Trampoline.done(true)
                : Trampoline.more(() -> isOdd(count - 1));
    }

    private static Trampoline<Boolean> isOdd(int count) {
        return count == 0
                ? Trampoline.done(false)
                : Trampoline.more(() -> isEven(count - 1));
    }

    @RepeatedTest(64)
    void functorIdentityAndComposition(@Given Integer value, @Given Integer offset) {
        Trampoline<Integer> original = Trampoline.more(() -> Trampoline.done(value));
        Function<Integer, Integer> f = n -> n * 2 + offset;
        Function<Integer, String> g = n -> "number:" + n;

        assertEquals(original.run(), original.map(Function.identity()).run());
        assertEquals(original.map(f).map(g).run(),
                original.map(f.andThen(g)).run());
        assertEquals("number:" + f.apply(value), original.map(f).map(g).run());
    }

    @RepeatedTest(64)
    void monadLeftAndRightIdentity(@Given Integer value, @Given Integer offset) {
        Function<Integer, Trampoline<String>> next =
                n -> Trampoline.more(() -> Trampoline.done("value:" + (n + offset)));
        Trampoline<Integer> original = Trampoline.more(() -> Trampoline.done(value));

        assertEquals(next.apply(value).run(),
                Trampoline.done(value).flatMap(next).run());
        assertEquals(original.run(), original.flatMap(Trampoline::done).run());
    }

    @RepeatedTest(64)
    void monadAssociativityIncludingHeterogeneousSteps(
            @Given Integer value, @Given Integer offset,
            @Given String text) {
        Trampoline<Integer> original = Trampoline.more(() -> Trampoline.done(value));
        Function<Integer, Trampoline<String>> first = n ->
                Trampoline.more(() -> Trampoline.done(text + (n + offset)));
        Function<String, Trampoline<Integer>> second = s ->
                Trampoline.more(() -> Trampoline.done(s.length()));

        assertEquals(original.flatMap(first).flatMap(second).run(),
                original.flatMap(n -> first.apply(n).flatMap(second)).run());
    }

    @RepeatedTest(64)
    void nestedMapAndBindAgreeWithAListOracle(
            @Given int seed, @Given int initial) {
        int count = 30 + Math.floorMod(seed, 70);
        int expected = initial;
        Trampoline<Integer> computation = Trampoline.done(initial);
        for (int i = 0; i < count; i++) {
            if ((i & 1) == 0) {
                computation = computation.map(value -> value + 1);
            } else {
                computation = computation.flatMap(value ->
                        Trampoline.more(() -> Trampoline.done(value * 2)));
            }
            expected = (i & 1) == 0 ? expected + 1 : expected * 2;
        }
        assertEquals(expected, computation.run());
    }

    @Test
    void lazyEvaluationIsOrderedAndRepeatsWithoutMemoization() {
        List<String> events = new ArrayList<>();
        Trampoline<Integer> program = Trampoline.<Integer>more(() -> {
            events.add("suspend");
            return Trampoline.done(3);
        }).map(value -> {
            events.add("map");
            return value + 1;
        }).flatMap(value -> {
            events.add("bind");
            return Trampoline.more(() -> {
                events.add("dependent");
                return Trampoline.done(value * 2);
            });
        });

        assertEquals(List.of(), events, "neither suppliers nor continuations run during build");
        assertEquals(8, program.run());
        assertEquals(List.of("suspend", "map", "bind", "dependent"), events);
        assertEquals(8, program.run());
        assertEquals(List.of("suspend", "map", "bind", "dependent",
                "suspend", "map", "bind", "dependent"), events);
    }

    @Test
    void doneCompletesImmediatelyAndDoesNotExecuteUnrelatedSuspensions() {
        var calls = new AtomicInteger();
        Trampoline<Integer> unused = Trampoline.more(() -> {
            calls.incrementAndGet();
            return Trampoline.done(9);
        });
        assertEquals(7, Trampoline.done(7).run());
        assertEquals(0, calls.get());
        assertEquals(9, unused.run());
        assertEquals(1, calls.get());
    }

    @Test
    void requiredStructuralReferencesAreCheckedEagerly() {
        assertThrows(NullPointerException.class, () -> Trampoline.more(null));
        assertThrows(NullPointerException.class, () -> new Trampoline.More<>(null));
        assertThrows(NullPointerException.class, () -> Trampoline.done(1).map(null));
        assertThrows(NullPointerException.class, () -> Trampoline.done(1).flatMap(null));
    }

    @Test
    void nullNodesAreRejectedWhenEvaluatedNotWhenBuilt() {
        var suspended = Trampoline.<Integer>more(() -> null);
        var dependent = Trampoline.done(1).flatMap(value -> null);
        assertThrows(NullPointerException.class, suspended::run);
        assertThrows(NullPointerException.class, dependent::run);
        var deep = Trampoline.more(() -> Trampoline.<Integer>more(() -> null))
                .flatMap(value -> Trampoline.done(value + 1));
        assertThrows(NullPointerException.class, deep::run);
    }

    @Test
    void callbackExceptionsPropagateUnchangedWithoutRecovery() {
        var failure = new IllegalStateException("from caller");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Trampoline.<Integer>more(() -> { throw failure; }).run()));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Trampoline.done(1).map(value -> { throw failure; }).run()));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Trampoline.done(1).flatMap(value -> { throw failure; }).run()));
    }

    @Test
    void explicitlyNullableResultTypesAreNotStructuralErrors() {
        Trampoline<@Nullable Integer> emptyValue = Trampoline.done(null);
        assertNull(emptyValue.run());
        assertEquals(4, emptyValue.map(value -> value == null ? 4 : value).run());
        assertNull(emptyValue.flatMap(value -> Trampoline.done(value)).run());
        Trampoline<@Nullable String> another = Trampoline.more(() -> Trampoline.done(null));
        assertNull(another.run());
    }

    @Test
    void oneMillionDeferredTailStepsCompleteWithoutRecursion() {
        long count = 1_000_000L;
        assertEquals(count * (count + 1) / 2, sum(count, 0).run());
    }

    @Test
    void mutualRecursionOverOneMillionSuspensionsIsStackSafe() {
        assertTrue(isEven(1_000_000).run());
        assertFalse(isOdd(1_000_000).run());
    }

    @Test
    void oneMillionLeftAssociatedFlatMapsEvaluateWithoutStackOverflow() {
        int count = 1_000_000;
        Trampoline<Integer> computation = Trampoline.done(0);
        for (int i = 0; i < count; i++) {
            computation = computation.flatMap(value -> Trampoline.done(value + 1));
        }
        assertEquals(count, computation.run());
    }

    @Test
    void rightAssociatedCompositionsAndMixedSuspensionsAreStackSafe() {
        Trampoline<Integer> computation = Trampoline.done(0);
        for (int i = 0; i < 50_000; i++) {
            computation = computation.flatMap(value ->
                    Trampoline.more(() -> Trampoline.done(value + 1)));
        }
        assertEquals(50_000, computation.run());
    }

    @Test
    void publicVariantsCanBeInspectedAsImmutableData() {
        Trampoline.Done<Integer> terminal = new Trampoline.Done<>(7);
        assertEquals(7, terminal.value());
        assertEquals(7, terminal.run());
        Trampoline.More<Integer> suspended = new Trampoline.More<>(() -> terminal);
        assertEquals(7, suspended.run());
    }
}
