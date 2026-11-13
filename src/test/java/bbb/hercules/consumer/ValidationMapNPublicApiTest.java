package bbb.hercules.consumer;

import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compiles heterogeneous public mapN API calls outside the implementation package. */
class ValidationMapNPublicApiTest {

    record Order(String symbol, int quantity, BigDecimal price) {
    }

    record Submission(String symbol, int quantity, BigDecimal price, String venue) {
    }

    private static final ValidationApplicative<List<String>> APP =
            new ValidationApplicative<>((a, b) ->
                    Stream.concat(a.stream(), b.stream()).toList());

    @Test
    void map3ValidatesIndependentHeterogeneousFields() {
        Validation<List<String>, String> symbol = Validation.valid("BTC");
        Validation<List<String>, Integer> quantity =
                Validation.invalid(List.of("quantity must be positive"));
        Validation<List<String>, BigDecimal> price =
                Validation.invalid(List.of("price must be positive"));

        assertEquals(Validation.invalid(List.of(
                "quantity must be positive", "price must be positive")),
                APP.map3(symbol, quantity, price, Order::new));

        assertEquals(
                Validation.valid(new Order("BTC", 2, new BigDecimal("12.50"))),
                APP.map3(symbol, Validation.valid(2),
                        Validation.valid(new BigDecimal("12.50")), Order::new));
    }

    @Test
    void map4CanBuildRecordsWithoutTuplesOrReflection() {
        Validation<List<String>, Submission> result = APP.map4(
                Validation.valid("BTC"),
                Validation.valid(5),
                Validation.valid(new BigDecimal("100.00")),
                Validation.valid("US"),
                Submission::new);

        assertEquals(Validation.valid(
                new Submission("BTC", 5, new BigDecimal("100.00"), "US")), result);
    }

    @Test
    void map10UsesExistingF10AndAccumulatesAllErrors() {
        var invalid = Validation.<List<String>, Integer>invalid(List.of("first"));
        var valid = Validation.<List<String>, Integer>valid(1);
        var anotherInvalid = Validation.<List<String>, Integer>invalid(List.of("last"));

        assertEquals(Validation.invalid(List.of("first", "last")),
                APP.map10(invalid, valid, valid, valid, valid, valid, valid, valid, valid,
                        anotherInvalid, (a, b, c, d, f, g, h, i, j, k) ->
                                a + b + c + d + f + g + h + i + j + k));
    }
}
