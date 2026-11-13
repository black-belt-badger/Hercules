package io.github.blackbeltbadger.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ValidationLawsTest {

    private static <E, A> List<Validation<E, A>> variants(E error, A value) {
        return List.of(Validation.invalid(error), Validation.valid(value));
    }

    @RepeatedTest(64)
    void functorIdentityCompositionAndBimapCoherence(
            @Given String error, @Given Integer value) {
        Function<Integer, Integer> f = n -> n + 4;
        Function<Integer, String> g = n -> "number=" + n;
        Function<String, String> decorate = s -> "[" + s + "]";
        Function<String, Integer> length = String::length;

        for (Validation<String, Integer> item : variants(error, value)) {
            assertEquals(item, item.map(Function.identity()));
            assertEquals(item, item.mapLeft(Function.identity()));
            assertEquals(item, item.bimap(Function.identity(), Function.identity()));

            assertEquals(item.map(f).map(g), item.map(f.andThen(g)));
            assertEquals(item.mapLeft(decorate).mapLeft(length),
                    item.mapLeft(decorate.andThen(length)));
            assertEquals(item.map(g), item.bimap(Function.identity(), g));
            assertEquals(item.mapLeft(length), item.bimap(length, Function.identity()));
            assertEquals(item.mapLeft(decorate).map(g),
                    item.bimap(decorate, g));
        }
    }

    @RepeatedTest(64)
    void conversionIsAnExactIsomorphism(
            @Given String error, @Given Integer value) {
        for (Validation<String, Integer> item : variants(error, value)) {
            assertEquals(item, Validation.fromEither(item.toEither()));
        }
        for (Either<String, Integer> item : List.<Either<String, Integer>>of(
                Either.left(error), Either.right(value))) {
            assertEquals(item, Validation.fromEither(item).toEither());
        }
    }

    @RepeatedTest(64)
    void foldInvokesExactlyOneBranchAndPreservesPayloadReference(
            @Given String error, @Given Integer value) {
        var errors = new AtomicInteger();
        var values = new AtomicInteger();

        Validation<String, Integer> invalid = Validation.invalid(error);
        Validation<String, Integer> valid = Validation.valid(value);

        assertSame(error, invalid.fold(e -> {
            errors.incrementAndGet();
            return e;
        }, v -> {
            values.incrementAndGet();
            return "unexpected";
        }));
        assertSame(value, valid.fold(e -> {
            errors.incrementAndGet();
            return 0;
        }, v -> {
            values.incrementAndGet();
            return v;
        }));
        assertEquals(1, errors.get());
        assertEquals(1, values.get());
        assertInstanceOf(Validation.Invalid.class, invalid);
        assertInstanceOf(Validation.Valid.class, valid);
    }

    @Test
    void callbacksAreStrictAndValidatedEvenOnUnusedBranches() {
        var errors = new AtomicInteger();
        var values = new AtomicInteger();
        Validation<String, Integer> invalid = Validation.invalid("bad");
        Validation<String, Integer> valid = Validation.valid(5);

        assertEquals(Validation.invalid("bad!"), invalid.bimap(e -> {
            errors.incrementAndGet();
            return e + "!";
        }, v -> {
            values.incrementAndGet();
            return v + 1;
        }));
        assertEquals(1, errors.get());
        assertEquals(0, values.get());

        assertEquals(Validation.valid(6), valid.bimap(e -> {
            errors.incrementAndGet();
            return e + "!";
        }, v -> {
            values.incrementAndGet();
            return v + 1;
        }));
        assertEquals(1, errors.get());
        assertEquals(1, values.get());

        for (Validation<String, Integer> item : variants("bad", 3)) {
            assertThrows(NullPointerException.class, () -> item.map(null));
            assertThrows(NullPointerException.class, () -> item.mapLeft(null));
            assertThrows(NullPointerException.class,
                    () -> item.bimap(null, Function.identity()));
            assertThrows(NullPointerException.class,
                    () -> item.bimap(Function.identity(), null));
            assertThrows(NullPointerException.class,
                    () -> item.fold(null, Function.identity()));
            assertThrows(NullPointerException.class,
                    () -> item.fold(Function.identity(), null));
        }
        assertThrows(NullPointerException.class,
                () -> Validation.<String, Integer>fromEither(null));
    }

    @Test
    void callerExceptionsPropagateUnchanged() {
        var failure = new IllegalStateException("from user");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Validation.<String, Integer>valid(4)
                        .map(n -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Validation.<String, Integer>invalid("bad")
                        .mapLeft(e -> { throw failure; })));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Validation.<String, Integer>valid(4)
                        .fold(e -> "", n -> { throw failure; })));
    }

    @Test
    void nullablePayloadTypeArgumentsRoundTripWithoutChangingCase() {
        Validation<@Nullable String, @Nullable Integer> invalid = Validation.invalid(null);
        Validation<@Nullable String, @Nullable Integer> valid = Validation.valid(null);

        assertInstanceOf(Validation.Invalid.class, invalid);
        assertInstanceOf(Validation.Valid.class, valid);
        assertEquals(invalid, Validation.fromEither(invalid.toEither()));
        assertEquals(valid, Validation.fromEither(valid.toEither()));
        assertNull(invalid.fold(Function.identity(), value -> "unexpected"));
        assertNull(valid.fold(errors -> 1, Function.identity()));
    }
}
