package bbb.hercules.consumer;

import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationTraversalPublicApiTest {

    record Row(String symbol, int quantity) {
    }

    private static Validation<List<String>, Integer> positive(int quantity) {
        return quantity > 0
                ? Validation.valid(quantity)
                : Validation.invalid(List.of("quantity must be positive"));
    }

    @Test
    void consumerTraversesAndAccumulatesValidationErrors() {
        var app = new ValidationApplicative<List<String>>((a, b) ->
                Stream.concat(a.stream(), b.stream()).toList());
        var input = List.of(2, -1, 3, 0);

        Validation<List<String>, List<Integer>> invalid =
                app.traverse(input, ValidationTraversalPublicApiTest::positive);
        assertEquals(Validation.invalid(List.of(
                "quantity must be positive", "quantity must be positive")), invalid);

        Validation<List<String>, List<Integer>> valid =
                app.traverse(List.of(2, 3), ValidationTraversalPublicApiTest::positive);
        assertEquals(Validation.valid(List.of(2, 3)), valid);

        Validation<List<String>, List<Row>> rows = app.sequence(
                List.of(Validation.valid(new Row("BTC", 1)),
                        Validation.valid(new Row("ETH", 2))));
        assertEquals(Validation.valid(List.of(new Row("BTC", 1), new Row("ETH", 2))), rows);
    }
}
