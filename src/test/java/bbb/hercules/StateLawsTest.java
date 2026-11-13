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

/** Observational State laws: compare results under multiple starting states. */
@ExtendWith(InstancioExtension.class)
class StateLawsTest {

    record Counter(int count, List<String> history) {
        Counter increment(String event) {
            return new Counter(count + 1,
                    java.util.stream.Stream.concat(history.stream(),
                            java.util.stream.Stream.of(event)).toList());
        }
    }

    @RepeatedTest(64)
    void functorIdentityAndCompositionPreserveResultState(
            @Given int initial, @Given String event) {
        State<Counter, Integer> action = state ->
                new State.Result<>(state.increment(event), state.count());
        Function<Integer, Integer> f = n -> n * 2;
        Function<Integer, String> g = n -> "value=" + n;

        for (Counter seed : List.of(
                new Counter(initial, List.of()),
                new Counter(initial + 3, List.of("older")))) {
            assertEquals(action.run(seed),
                    action.map(Function.identity()).run(seed));
            assertEquals(action.map(f).map(g).run(seed),
                    action.map(f.andThen(g)).run(seed));
            assertEquals(new State.Result<>(
                            seed.increment(event), "value=" + (seed.count() * 2)),
                    action.map(f).map(g).run(seed));
            assertEquals(seed.count(), action.eval(seed));
            assertEquals(seed.increment(event), action.exec(seed));
        }
    }

    @RepeatedTest(64)
    void monadLeftAndRightIdentity(
            @Given int number, @Given int initial, @Given String event) {
        Function<Integer, State<Counter, String>> next = n -> state ->
                new State.Result<>(state.increment(event), "computed=" + (n + state.count()));
        State<Counter, Integer> input = state ->
                new State.Result<>(state.increment("first"), number + state.count());

        for (Counter seed : List.of(
                new Counter(initial, List.of()),
                new Counter(initial - 3, List.of("existing")))) {
            assertEquals(next.apply(number).run(seed),
                    State.<Counter, Integer>pure(number).flatMap(next).run(seed));
            assertEquals(input.run(seed),
                    input.flatMap(State::pure).run(seed));
        }
    }

    @RepeatedTest(64)
    void monadAssociativityThreadsUpdatedStateAcrossBothBranches(
            @Given int initial, @Given int number,
            @Given String a, @Given String b, @Given String c) {
        State<Counter, Integer> start = state ->
                new State.Result<>(state.increment(a), number + state.count());
        Function<Integer, State<Counter, Integer>> f = n -> state ->
                new State.Result<>(state.increment(b), n + state.count());
        Function<Integer, State<Counter, String>> g = n -> state ->
                new State.Result<>(state.increment(c), "computed:" + n + "/" + state.count());

        State<Counter, String> left = start.flatMap(f).flatMap(g);
        State<Counter, String> right =
                start.flatMap(x -> f.apply(x).flatMap(g));

        for (Counter seed : List.of(
                new Counter(initial, List.of()),
                new Counter(initial + 2, List.of("prior")))) {
            Counter snapshot = new Counter(seed.count(), List.copyOf(seed.history()));
            assertEquals(left.run(seed), right.run(seed));
            assertEquals(List.of(a, b, c),
                    left.run(seed).state().history().subList(
                            seed.history().size(), seed.history().size() + 3));
            assertEquals(seed.history().size() + 3, left.run(seed).state().history().size());
            assertEquals(snapshot, seed,
                    "the caller-supplied immutable record remains unchanged");
        }
    }

    @RepeatedTest(64)
    void getPutModifyGetsAndPureObserveAndChangeOnlyExplicitState(
            @Given int number, @Given String label) {
        Counter original = new Counter(number, List.of(label));
        Counter replacement = new Counter(number + 10, List.of("replacement"));

        assertEquals(new State.Result<>(original, original),
                State.<Counter>get().run(original));
        assertEquals(new State.Result<>(replacement, State.Unit.INSTANCE),
                State.put(replacement).run(original));
        assertEquals(new State.Result<>(original.increment("step"), State.Unit.INSTANCE),
                State.<Counter>modify(s -> s.increment("step")).run(original));
        assertEquals(new State.Result<>(original, number),
                State.<Counter, Integer>gets(Counter::count).run(original));
        assertEquals(new State.Result<>(original, label),
                State.<Counter, String>pure(label).run(original));
        assertEquals(original,
                State.<Counter, Integer>pure(number).exec(original));
        assertEquals(number, State.<Counter, Integer>pure(number).eval(original));
    }

    @Test
    void successiveStateOperationsObserveUpdatedNotOriginalState() {
        Counter start = new Counter(3, List.of("start"));
        State<Counter, Integer> pipeline = State.<Counter>get()
                .flatMap(before -> State.<Counter>modify(
                        state -> state.increment("step-1"))
                        .flatMap(ignored -> State.<Counter>get()
                                .flatMap(after -> State.<Counter>modify(
                                        state -> state.increment("step-2"))
                                        .map(unit -> after.count() - before.count()))));

        assertEquals(new State.Result<>(
                new Counter(5, List.of("start", "step-1", "step-2")), 1),
                pipeline.run(start));
        assertEquals(new Counter(3, List.of("start")), start);
        assertEquals(pipeline.run(start), pipeline.run(start),
                "runs with the same immutable input are independent");
    }

    @Test
    void compositionIsLazyUntilRunAndCallbacksExecuteInOrder() {
        List<String> calls = new ArrayList<>();
        State<Counter, Integer> initial = state -> {
            calls.add("initial");
            return new State.Result<>(state.increment("first"), state.count());
        };
        State<Counter, String> computation = initial.map(n -> {
            calls.add("mapper");
            return n + 10;
        }).flatMap(n -> {
            calls.add("binder");
            return state -> {
                calls.add("dependent");
                return new State.Result<>(state.increment("next"), "" + n + "/" + state.count());
            };
        });

        assertEquals(List.of(), calls);
        Counter input = new Counter(2, List.of());
        assertEquals(new State.Result<>(new Counter(4, List.of("first", "next")), "12/3"),
                computation.run(input));
        assertEquals(List.of("initial", "mapper", "binder", "dependent"), calls);
        assertEquals(2, input.count());
        computation.run(input);
        assertEquals(List.of("initial", "mapper", "binder", "dependent",
                "initial", "mapper", "binder", "dependent"), calls);
    }

    @Test
    void structuralCallbackChecksAndNullReturnedComputationResults() {
        State<Counter, Integer> base = State.pure(1);
        Counter input = new Counter(2, List.of());

        assertThrows(NullPointerException.class, () -> base.map(null));
        assertThrows(NullPointerException.class, () -> base.flatMap(null));
        assertThrows(NullPointerException.class, () -> State.modify(null));
        assertThrows(NullPointerException.class, () -> State.gets(null));

        AtomicInteger executed = new AtomicInteger();
        State<Counter, Integer> returnsNullState =
                base.flatMap(n -> {
                    executed.incrementAndGet();
                    return null;
                });
        assertEquals(0, executed.get());
        assertThrows(NullPointerException.class, () -> returnsNullState.run(input));
        assertEquals(1, executed.get());

        State<Counter, Integer> badResult = state -> null;
        assertThrows(NullPointerException.class, () -> badResult.eval(input));
        assertThrows(NullPointerException.class, () -> badResult.exec(input));
        assertThrows(NullPointerException.class, () -> badResult.map(n -> 1).run(input));
        assertThrows(NullPointerException.class,
                () -> badResult.flatMap(n -> State.pure(n)).run(input));
        assertThrows(NullPointerException.class,
                () -> base.flatMap(n -> (State<Counter, Integer>) state -> null).run(input));
    }

    @Test
    void exceptionsFromUserCodePropagateUnchanged() {
        var problem = new IllegalStateException("user callback");
        Counter input = new Counter(10, List.of());
        State<Counter, Integer> original = State.gets(Counter::count);

        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.map(n -> { throw problem; }).run(input)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.flatMap(n -> { throw problem; }).run(input)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.flatMap(n -> state -> { throw problem; }).run(input)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> State.<Counter>modify(state -> { throw problem; }).run(input)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> State.<Counter, Integer>gets(state -> { throw problem; }).run(input)));
    }

    @Test
    void nullableStateAndPayloadTypesRemainRepresentable() {
        State<@Nullable String, @Nullable Integer> nullable =
                State.gets(s -> s == null ? null : s.length());
        assertEquals(new State.Result<>(null, null), nullable.run(null));
        assertEquals(new State.Result<>("hello", 5), nullable.run("hello"));

        State<@Nullable String, State.Unit> putNull = State.put(null);
        assertEquals(new State.Result<>(null, State.Unit.INSTANCE), putNull.run("prior"));

        State<@Nullable String, State.Unit> modify = State.modify(
                s -> s == null ? "fallback" : s);
        assertEquals(new State.Result<>("fallback", State.Unit.INSTANCE), modify.run(null));

        State<@Nullable String, @Nullable Integer> pure = State.pure(null);
        assertEquals(new State.Result<>("original", null), pure.run("original"));
    }

    @Test
    void constructingHundredThousandDependentTransitionsIsLazy() {
        AtomicInteger calls = new AtomicInteger();
        State<Integer, Integer> chain = State.get();
        for (int n = 0; n < 100_000; n++) {
            chain = chain.flatMap(value -> {
                calls.incrementAndGet();
                return State.pure(value + 1);
            });
        }
        assertNotNull(chain);
        assertEquals(0, calls.get(), "construction never executes the underlying transitions");
        // Deliberately not run: nested Java lambdas are not stack safe.
        // StateDepthProbe demonstrates the failure and documents the limitation.
    }
}
