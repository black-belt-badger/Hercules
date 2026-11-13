package bbb.hercules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TheseExamplesTest {

    @Test
    void batchValidationCanReturnDataAndDiagnosticsTogether() {
        These<List<String>, List<Integer>> clean = These.right(List.of(1, 2));
        These<List<String>, List<Integer>> partial =
                These.both(List.of("Skipped invalid row 2"), List.of(1, 3));
        These<List<String>, List<Integer>> invalid =
                These.left(List.of("Invalid row 1", "Invalid row 2"));

        assertEquals("2 valid", clean.fold(
                errors -> "0 valid, " + errors.size() + " problems",
                records -> records.size() + " valid",
                (errors, records) -> records.size() + " valid, " + errors.size() + " problems"));

        assertEquals("2 valid, 1 problems", partial.fold(
                errors -> "0 valid, " + errors.size() + " problems",
                records -> records.size() + " valid",
                (errors, records) -> records.size() + " valid, " + errors.size() + " problems"));

        assertEquals("0 valid, 2 problems", invalid.fold(
                errors -> "0 valid, " + errors.size() + " problems",
                records -> records.size() + " valid",
                (errors, records) -> records.size() + " valid, " + errors.size() + " problems"));
    }

    @Test
    void subsequentProcessingPreservesAndAccumulatesDiagnostics() {
        BinaryOperator<List<String>> append = (first, second) ->
                Stream.concat(first.stream(), second.stream()).toList();

        These<List<String>, Integer> initial =
                These.both(List.of("Noncritical warning"), 21);

        These<List<String>, Integer> processed =
                initial.flatMap(number -> These.both(List.of("Normalized input"), number * 2), append);

        assertEquals(These.both(
                List.of("Noncritical warning", "Normalized input"), 42), processed);
    }
}
