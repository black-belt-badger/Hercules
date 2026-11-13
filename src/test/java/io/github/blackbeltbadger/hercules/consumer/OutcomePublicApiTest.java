package io.github.blackbeltbadger.hercules.consumer;

import io.github.blackbeltbadger.hercules.Either;
import io.github.blackbeltbadger.hercules.EitherChoice;
import io.github.blackbeltbadger.hercules.These;
import io.github.blackbeltbadger.hercules.Those;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Compiles and uses the public APIs from outside the implementation package. */
class OutcomePublicApiTest {

    @Test
    void consumerCanWidenAcrossAllThreeOutcomeTypes() {
        Either<String, Integer> source = Either.left("partial result");
        These<String, Integer> inclusive = source.toThese();
        Those<String, Integer, Long> evidence = inclusive.toThose();

        assertInstanceOf(These.This.class, inclusive);
        assertInstanceOf(Those.This.class, evidence);
        assertEquals(Those.left("partial result"), evidence);
        assertEquals(Either.right(These.left("partial result")), evidence.toEither());
        assertEquals(evidence, Those.fromEither(evidence.toEither()));
    }

    @Test
    void consumerCanComposeExistingOutcomesAndChoice() {
        EitherChoice<String> choice = new EitherChoice<>("", String::concat);
        Either<String, Integer> selected = choice.choose(
                Either.left("unavailable"), () -> Either.right(7));

        String result = selected
                .toThese()
                .<String>toThose()
                .fold(
                        left -> "left: " + left,
                        right -> "right: " + right,
                        (left, right) -> "both: " + left + right,
                        reason -> "neither: " + reason);

        assertEquals("right: 7", result);
    }

    @Test
    void mappingFromConsumerPackageKeepsExpectedBehavior() {
        Either<String, Integer> source = Either.right(42);
        Those<Integer, String, Long> widened = source
                .mapLeft(String::length)
                .map(Object::toString)
                .toThese()
                .<Long>toThose();

        assertEquals(Those.right("42"), widened);
        assertEquals(Either.right(These.right("42")), widened.toEither());
        assertEquals("42", widened.fold(
                value -> "unexpected",
                Function.identity(),
                (left, right) -> "unexpected",
                reason -> "unexpected"));
    }
}
