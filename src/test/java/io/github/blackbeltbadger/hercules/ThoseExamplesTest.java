package io.github.blackbeltbadger.hercules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ThoseExamplesTest {

    private static String describe(Those<String, Integer, String> evidence) {
        return evidence.fold(
                left -> "Only A: " + left,
                right -> "Only B: " + right,
                (left, right) -> "Both: " + left + " and " + right,
                reason -> "Neither: " + reason);
    }

    @Test
    void describesAllFourPossibleEvidenceStates() {
        assertEquals("Only A: sensor A", describe(Those.left("sensor A")));
        assertEquals("Only B: 42", describe(Those.right(42)));
        assertEquals("Both: sensor A and 42", describe(Those.both("sensor A", 42)));
        assertEquals("Neither: not observed yet", describe(Those.neither("not observed yet")));
    }

    @Test
    void mappingsPreserveInformationAndRoundTripViaExistingTypes() {
        Those<String, Integer, String> evidence = Those.both("sensor A", 42);

        var modified = evidence
                .mapLeft(String::toUpperCase)
                .map(n -> n * 2)
                .mapNeither(String::toUpperCase);

        assertEquals(Those.both("SENSOR A", 84), modified);

        Either<String, These<String, Integer>> nested = evidence.toEither();
        assertInstanceOf(Either.Right.class, nested);
        assertEquals(Either.right(These.both("sensor A", 42)), nested);
        assertEquals(evidence, Those.fromEither(nested));

        Either<String, These<String, Integer>> noEvidence =
                Those.<String, Integer, String>neither("not sampled").toEither();
        assertEquals(Either.left("not sampled"), noEvidence);
        assertEquals(Those.neither("not sampled"), Those.fromEither(noEvidence));
    }
}
