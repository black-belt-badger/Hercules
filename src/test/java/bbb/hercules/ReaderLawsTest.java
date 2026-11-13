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

/** Reader laws are verified observationally by running against environments. */
@ExtendWith(InstancioExtension.class)
class ReaderLawsTest {

    record Environment(int offset, int factor, String label) {
    }

    @RepeatedTest(64)
    void functorIdentityAndCompositionAcrossDifferentEnvironments(
            @Given Integer offset, @Given Integer factor, @Given Integer number,
            @Given String label) {
        Reader<Environment, Integer> value =
                environment -> number + environment.offset();
        Function<Integer, Integer> f = n -> n * 2;
        Function<Integer, String> g = n -> "n=" + n;
        List<Environment> environments = List.of(
                new Environment(offset, factor, label),
                new Environment(offset + 1, factor + 1, label));

        for (Environment environment : environments) {
            assertEquals(value.run(environment),
                    value.map(Function.identity()).run(environment));
            assertEquals(value.map(f).map(g).run(environment),
                    value.map(f.andThen(g)).run(environment));
            assertEquals("n=" + ((number + environment.offset()) * 2),
                    value.map(f).map(g).run(environment));
        }
    }

    @RepeatedTest(64)
    void monadLeftAndRightIdentity(
            @Given Integer number, @Given Integer offset, @Given Integer factor,
            @Given String label) {
        Function<Integer, Reader<Environment, String>> next =
                n -> environment -> environment.label() + ":" +
                        (n + environment.offset() * 3);
        Reader<Environment, Integer> input =
                environment -> number * environment.factor();
        Environment environment = new Environment(offset, factor, label);

        assertEquals(next.apply(number).run(environment),
                Reader.<Environment, Integer>pure(number)
                        .flatMap(next).run(environment));
        assertEquals(input.run(environment),
                input.flatMap(Reader::pure).run(environment));
    }

    @RepeatedTest(64)
    void monadAssociativitySharesTheSameEnvironment(
            @Given Integer number, @Given Integer offset, @Given Integer factor,
            @Given String label) {
        Reader<Environment, Integer> start =
                environment -> number + environment.offset();
        Function<Integer, Reader<Environment, Integer>> f =
                value -> environment -> value * 2 + environment.factor();
        Function<Integer, Reader<Environment, String>> g =
                value -> environment -> environment.label() + "/" + value;

        Reader<Environment, String> left = start.flatMap(f).flatMap(g);
        Reader<Environment, String> right =
                start.flatMap(value -> f.apply(value).flatMap(g));

        for (Environment environment : List.of(
                new Environment(offset, factor, label),
                new Environment(offset + 2, factor - 2, label + "-later"))) {
            assertEquals(left.run(environment), right.run(environment));
            assertEquals(environment.label() + "/" +
                    ((number + environment.offset()) * 2 + environment.factor()),
                    left.run(environment));
        }
    }

    @RepeatedTest(64)
    void askAsksAndLocalHaveCorrectScoping(
            @Given Integer offset, @Given Integer factor, @Given String label) {
        Environment original = new Environment(offset, factor, label);
        Reader<Environment, Environment> ask = Reader.ask();
        Reader<Environment, String> asks = Reader.asks(Environment::label);
        Reader<Environment, Integer> computation =
                Reader.<Environment>ask().map(e -> e.offset() + e.factor());
        // Note: local scopes the whole selected computation, not the caller's object.
        Reader<Environment, Integer> local = computation.local(
                environment -> new Environment(
                        environment.offset() + 10,
                        environment.factor() + 5,
                        "local"));

        assertSame(original, ask.run(original));
        assertEquals(label, asks.run(original));
        assertEquals(offset + factor, computation.run(original));
        assertEquals(offset + factor + 15, local.run(original));
        assertEquals(original, ask.run(original));
        assertEquals(offset + factor, computation.run(original));
    }

    @Test
    void compositionIsLazyUntilRunAndInvokesCallbacksInOrderOncePerRun() {
        List<String> calls = new ArrayList<>();
        Reader<Environment, Integer> base = environment -> {
            calls.add("base");
            return environment.offset();
        };
        Reader<Environment, String> composed = base
                .map(value -> {
                    calls.add("map");
                    return value * 2;
                })
                .flatMap(value -> {
                    calls.add("binder");
                    return environment -> {
                        calls.add("next");
                        return environment.label() + "=" + value;
                    };
                })
                .local(environment -> {
                    calls.add("local");
                    return new Environment(
                            environment.offset() + 1,
                            environment.factor(),
                            environment.label());
                });

        assertEquals(List.of(), calls, "building the pipeline must not evaluate");
        assertEquals("test=8", composed.run(new Environment(3, 1, "test")));
        assertEquals(List.of("local", "base", "map", "binder", "next"), calls);
        assertEquals("test=8", composed.run(new Environment(3, 1, "test")));
        assertEquals(List.of("local", "base", "map", "binder", "next",
                "local", "base", "map", "binder", "next"), calls);
    }

    @Test
    void binderAndMapperStructuralNullContractsAreEager() {
        Reader<Environment, Integer> base = Reader.asks(Environment::offset);
        assertThrows(NullPointerException.class, () -> base.map(null));
        assertThrows(NullPointerException.class, () -> base.flatMap(null));
        assertThrows(NullPointerException.class, () -> base.local(null));
        assertThrows(NullPointerException.class, () -> Reader.asks(null));
    }

    @Test
    void nullReaderProducedByBinderFailsOnlyAtEvaluation() {
        var calls = new AtomicInteger();
        Reader<Environment, Integer> pending =
                Reader.<Environment, Integer>pure(12)
                        .flatMap(value -> {
                            calls.incrementAndGet();
                            return null;
                        });

        assertEquals(0, calls.get());
        assertThrows(NullPointerException.class,
                () -> pending.run(new Environment(1, 2, "x")));
        assertEquals(1, calls.get());
    }

    @Test
    void userExceptionsAreNotCaughtOrConverted() {
        var problem = new IllegalStateException("user callback failed");
        Reader<Environment, Integer> original = Reader.asks(Environment::offset);
        Environment environment = new Environment(10, 20, "x");

        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.map(value -> { throw problem; }).run(environment)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.flatMap(value -> { throw problem; }).run(environment)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.flatMap(value -> ignored -> { throw problem; })
                        .run(environment)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> original.local(value -> { throw problem; }).run(environment)));
        assertSame(problem, assertThrows(IllegalStateException.class,
                () -> Reader.<Environment, Integer>asks(value -> { throw problem; })
                        .run(environment)));
    }

    @Test
    void nullableEnvironmentAndPayloadSpecializationsAreSupported() {
        Reader<@Nullable String, @Nullable Integer> reader =
                Reader.asks(environment -> environment == null ? null : environment.length());
        assertNull(reader.run(null));
        assertEquals(5, reader.run("hello"));

        Reader<@Nullable String, @Nullable String> ask = Reader.ask();
        assertNull(ask.run(null));
        assertEquals("before", ask.run("before"));
        assertEquals("fallback", ask.local(
                environment -> environment == null ? "fallback" : environment).run(null));

        Reader<@Nullable String, @Nullable String> pure = Reader.pure(null);
        assertNull(pure.run("ignored"));
    }

    @Test
    void evaluatedReadersDoNotStoreOrModifyAnEnvironment() {
        record Config(List<String> names, int limit) {}
        Reader<Config, Integer> getLimit = Reader.asks(Config::limit);
        Config first = new Config(List.of("btc"), 10);
        Config second = new Config(List.of("eth"), 20);

        assertEquals(10, getLimit.run(first));
        assertEquals(20, getLimit.run(second));
        assertEquals(10, getLimit.run(first));
        assertEquals(new Config(List.of("btc"), 10), first);
        assertEquals(new Config(List.of("eth"), 20), second);
    }

    @Test
    void hundredThousandNestedBindsCanBeConstructedLazily() {
        AtomicInteger binderCalls = new AtomicInteger();
        Reader<Integer, Integer> pipeline = Reader.ask();
        for (int index = 0; index < 100_000; index++) {
            pipeline = pipeline.flatMap(value -> {
                binderCalls.incrementAndGet();
                return Reader.pure(value + 1);
            });
        }
        assertNotNull(pipeline);
        assertEquals(0, binderCalls.get(),
                "building a Reader chain must never execute dependent computations");
        // Intentionally do not call run: nested Java lambda execution can exhaust
        // the JVM stack. See ReaderDepthProbe and docs/reader.md.
    }

    @Test
    void pureCanHoldNullableValuesAndIgnoresItsEnvironment() {
        Reader<Environment, @Nullable Integer> reader = Reader.pure(null);
        assertNull(reader.run(new Environment(10, 20, "x")));
    }
}
