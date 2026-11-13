package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class WriterLawsTest {

    private static final WriterPolicy<List<String>> POLICY =
            new WriterPolicy<>(List.of(),
                    (first, second) -> Stream.concat(first.stream(), second.stream()).toList());

    @RepeatedTest(64)
    void functorIdentityAndComposition(
            @Given Integer value, @Given String event) {
        Writer<List<String>, Integer> writer = new Writer<>(value, List.of(event));
        Function<Integer, Integer> f = n -> n * 2;
        Function<Integer, String> g = n -> "v" + n;

        assertEquals(writer, writer.map(Function.identity()));
        assertEquals(writer.map(f).map(g), writer.map(f.andThen(g)));
        assertEquals(writer.map(f), writer.flatMap(n -> POLICY.pure(f.apply(n)), POLICY));
        assertEquals(List.of(event), writer.map(f).output());
    }

    @RepeatedTest(64)
    void monadLeftAndRightIdentity(
            @Given Integer value, @Given String event) {
        Function<Integer, Writer<List<String>, String>> next =
                n -> new Writer<>("computed: " + n, List.of(event, "second"));
        Writer<List<String>, Integer> writer =
                new Writer<>(value, List.of("initial", event));

        assertEquals(next.apply(value), POLICY.pure(value).flatMap(next, POLICY));
        assertEquals(writer, writer.flatMap(POLICY::pure, POLICY));
        assertEquals(POLICY.pure(value), POLICY.pure(value).flatMap(POLICY::pure, POLICY));
    }

    @RepeatedTest(64)
    void monadAssociativityPreservesNonCommutativeOutputOrder(
            @Given Integer value, @Given String a, @Given String b,
            @Given String c) {
        Writer<List<String>, Integer> start = new Writer<>(value, List.of(a));
        Function<Integer, Writer<List<String>, Integer>> f =
                n -> new Writer<>(n + 2, List.of(b));
        Function<Integer, Writer<List<String>, String>> g =
                n -> new Writer<>("result:" + n, List.of(c));

        Writer<List<String>, String> left = start.flatMap(f, POLICY).flatMap(g, POLICY);
        Writer<List<String>, String> right =
                start.flatMap(n -> f.apply(n).flatMap(g, POLICY), POLICY);

        assertEquals(left, right);
        assertEquals("result:" + (value + 2), left.value());
        assertEquals(List.of(a, b, c), left.output());
        assertEquals(List.of(a), start.output());
    }

    @RepeatedTest(64)
    void compositionalOutputMatchesListOracle(
            @Given Integer seed, @Given String prefix) {
        Writer<List<String>, Integer> writer = POLICY.pure(seed);
        List<String> expected = new ArrayList<>();
        int count = 10 + Math.floorMod(seed, 90);
        for (int i = 0; i < count; i++) {
            String event = prefix + ":" + i;
            expected.add(event);
            writer = writer.flatMap(n -> new Writer<>(n + 1, List.of(event)), POLICY);
        }

        assertEquals(seed + count, writer.value());
        assertEquals(expected, writer.output());
    }

    @Test
    void eagerEvaluationChecksCallbacksAndRunsBinderThenCombinerExactlyOnce() {
        List<String> order = new ArrayList<>();
        WriterPolicy<List<String>> tracingPolicy = new WriterPolicy<>(List.of(),
                (first, second) -> {
                    order.add("combine");
                    return Stream.concat(first.stream(), second.stream()).toList();
                });
        Writer<List<String>, Integer> original = new Writer<>(12, List.of("before"));
        Writer<List<String>, Integer> result = original.flatMap(n -> {
            order.add("binder");
            return new Writer<>(n + 1, List.of("after"));
        }, tracingPolicy);

        assertEquals(List.of("binder", "combine"), order);
        assertEquals(new Writer<>(13, List.of("before", "after")), result);
        assertEquals(new Writer<>(12, List.of("before")), original);
        assertThrows(NullPointerException.class,
                () -> original.map(null));
        assertThrows(NullPointerException.class,
                () -> original.flatMap(null, tracingPolicy));
        assertThrows(NullPointerException.class,
                () -> original.flatMap(POLICY::pure, null));
    }

    @Test
    void nullWriterFromBinderIsRejectedOnlyWhenBinderRuns() {
        Writer<List<String>, Integer> input = POLICY.pure(3);
        AtomicInteger calls = new AtomicInteger();
        NullPointerException problem = assertThrows(NullPointerException.class,
                () -> input.flatMap(n -> {
                    calls.incrementAndGet();
                    return null;
                }, POLICY));
        assertTrue(problem.getMessage().contains("flatMap"));
        assertEquals(1, calls.get());
    }

    @Test
    void userExceptionsPropagateUnchanged() {
        IllegalStateException failure = new IllegalStateException("user callback");
        Writer<List<String>, Integer> input = POLICY.pure(3);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> input.map(n -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> input.flatMap(n -> { throw failure; }, POLICY)));

        WriterPolicy<List<String>> throwing = new WriterPolicy<>(List.of(),
                (left, right) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> input.flatMap(n -> new Writer<>(n, List.of("written")), throwing)));
        assertThrows(NullPointerException.class, () -> new WriterPolicy<>(List.of(), null));
    }

    @Test
    void explicitlyNullableValueAndOutputSpecializationsRemainValid() {
        WriterPolicy<@Nullable String> policy =
                new WriterPolicy<>(null,
                        (first, second) -> first == null ? second
                                : second == null ? first : first + ":" + second);
        Writer<@Nullable String, @Nullable Integer> first = policy.pure(null);
        assertEquals(new Writer<>(null, null), first);

        Writer<@Nullable String, @Nullable String> mapped =
                first.map(n -> n == null ? null : n.toString());
        assertEquals(new Writer<>(null, null), mapped);

        Writer<@Nullable String, @Nullable Integer> combined =
                first.flatMap(n -> new Writer<>(n, "event"), policy);
        assertEquals(new Writer<>(null, "event"), combined);
        assertEquals(new Writer<>(null, "event"),
                new Writer<@Nullable String, @Nullable Integer>(null, "event")
                        .flatMap(policy::pure, policy));
    }

    @Test
    void persistentOutputAndBranchesAreNeverMutated() {
        WriterPolicy<DList<String>> policy =
                new WriterPolicy<>(DList.empty(), DList::append);
        Writer<DList<String>, Integer> initial =
                new Writer<>(10, DList.singleton("initial"));
        Writer<DList<String>, Integer> first =
                initial.flatMap(n -> new Writer<>(n + 1, DList.singleton("branch A")), policy);
        Writer<DList<String>, Integer> second =
                initial.flatMap(n -> new Writer<>(n + 2, DList.singleton("branch B")), policy);

        assertEquals(List.of("initial"), initial.output().toList());
        assertEquals(List.of("initial", "branch A"), first.output().toList());
        assertEquals(List.of("initial", "branch B"), second.output().toList());
        assertEquals(11, first.value());
        assertEquals(12, second.value());
    }

    @Test
    void hundredThousandEagerBindsWithDListStayStackSafe() {
        WriterPolicy<DList<Integer>> policy =
                new WriterPolicy<>(DList.empty(), DList::append);
        Writer<DList<Integer>, Integer> writer = policy.pure(0);
        int count = 120_000;
        for (int i = 0; i < count; i++) {
            writer = writer.flatMap(n ->
                    new Writer<>(n + 1, DList.singleton(n)), policy);
        }

        assertEquals(count, writer.value());
        List<Integer> events = writer.output().toList();
        assertEquals(count, events.size());
        assertEquals(0, events.get(0));
        assertEquals(count / 2, events.get(count / 2));
        assertEquals(count - 1, events.get(count - 1));
    }

    @Test
    void identityAndAssociativityLawsAreRequiredNotSilentlyImposed() {
        WriterPolicy<String> notAssociative =
                new WriterPolicy<>("", (a, b) -> "(" + a + "+" + b + ")");
        Writer<String, Integer> start = new Writer<>(0, "a");
        Function<Integer, Writer<String, Integer>> f = n -> new Writer<>(n + 1, "b");
        Function<Integer, Writer<String, Integer>> g = n -> new Writer<>(n + 1, "c");

        assertNotEquals(
                start.flatMap(f, notAssociative).flatMap(g, notAssociative),
                start.flatMap(n -> f.apply(n).flatMap(g, notAssociative), notAssociative));
    }
}
