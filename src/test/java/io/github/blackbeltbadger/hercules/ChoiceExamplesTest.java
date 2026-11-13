package io.github.blackbeltbadger.hercules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChoiceExamplesTest {

    private static Either<List<String>, Integer> parse(String input) {
        try {
            return Either.right(Integer.parseInt(input));
        } catch (NumberFormatException exception) {
            return Either.left(List.of("Invalid integer: " + input));
        }
    }

    @Test
    void firstSuccessfulParseWinsWithExplicitErrorAccumulation() {
        var choice = new EitherChoice<>(
                List.<String>of(),
                (List<String> a, List<String> b) ->
                        Stream.concat(a.stream(), b.stream()).toList());

        var found = choice.choose(parse("bad"), () -> parse("42"));
        assertEquals(Either.right(42), found);

        var missing = choice.choose(parse("bad"), () -> parse("also bad"));
        assertEquals(Either.left(List.of(
                "Invalid integer: bad",
                "Invalid integer: also bad")), missing);

        assertEquals(parse("42"), choice.choose(choice.empty(), parse("42")));
    }

    @Test
    void jdkOptionalAndListAlreadyHaveTheirOwnChoiceOperations() {
        assertEquals(Optional.of(42), Optional.<Integer>empty().or(() -> Optional.of(42)));
        assertEquals(Optional.of(7), Optional.of(7).or(() -> Optional.of(42)));
        assertEquals(List.of(1, 2, 3),
                Stream.concat(List.of(1, 2).stream(), List.of(3).stream()).toList());
        assertEquals(List.of(1, 2), Stream.concat(
                List.<Integer>of().stream(), List.of(1, 2).stream()).toList());
    }
}
