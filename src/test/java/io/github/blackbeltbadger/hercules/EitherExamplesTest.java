package io.github.blackbeltbadger.hercules;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EitherExamplesTest {

    static Either<String, Integer> parsePositive(String input) {
        final int number;
        try {
            number = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return Either.left("Not a number: " + input);
        }
        return number > 0 ? Either.right(number) : Either.left("Must be positive");
    }

    @Test
    void parseValidateAndFormat() {
        Either<String, String> result = parsePositive("21")
                .flatMap(n -> Either.right(n * 2))
                .map(n -> "Answer: " + n);

        String message = result.fold(
                error -> "Invalid input: " + error,
                Function.identity());

        assertEquals("Answer: 42", message);
        assertEquals(Either.left("Must be positive"), parsePositive("-2"));
        assertEquals(Either.left("Not a number: nope"), parsePositive("nope"));
    }

    @Test
    void conversionsMakeInformationLossExplicit() {
        Either<String, Integer> missing = Either.left("unavailable");
        Either<String, Integer> present = Either.right(7);

        assertEquals(0, missing.getOrElse(0));
        assertEquals(Either.right(11), missing.recover(String::length));
        assertEquals(Optional.of(7), present.toOptional());
        assertEquals(Optional.empty(), missing.toOptional());
        assertEquals(Either.left(7), present.swap());
        assertEquals(Either.right(7), present.mapLeft(String::length));
        assertEquals(Either.right(70), present.bimap(String::length, n -> n * 10));
    }
}
