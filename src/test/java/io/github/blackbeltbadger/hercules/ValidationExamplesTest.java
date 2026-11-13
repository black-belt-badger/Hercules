package io.github.blackbeltbadger.hercules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationExamplesTest {

    private static final ValidationApplicative<List<String>> AP =
            new ValidationApplicative<>((first, second) ->
                    Stream.concat(first.stream(), second.stream()).toList());

    record Registration(String username, int age) {
    }

    private static Validation<List<String>, String> username(String username) {
        return username.isBlank()
                ? Validation.invalid(List.of("username must not be blank"))
                : Validation.valid(username);
    }

    private static Validation<List<String>, Integer> age(int age) {
        return age < 18
                ? Validation.invalid(List.of("age must be at least 18"))
                : Validation.valid(age);
    }

    @Test
    void independentRegistrationFieldsAccumulateBothErrors() {
        Validation<List<String>, Registration> invalid =
                AP.map2(username(""), age(16), Registration::new);
        assertEquals(Validation.invalid(List.of(
                "username must not be blank",
                "age must be at least 18")), invalid);

        Validation<List<String>, Registration> valid =
                AP.map2(username("Ada"), age(26), Registration::new);
        assertEquals(Validation.valid(new Registration("Ada", 26)), valid);
    }

    record Item(String sku, int quantity) {
    }

    private static Validation<List<String>, String> sku(String sku) {
        return sku.startsWith("SKU-")
                ? Validation.valid(sku)
                : Validation.invalid(List.of("SKU must start with SKU-"));
    }

    private static Validation<List<String>, Integer> quantity(int quantity) {
        return quantity > 0
                ? Validation.valid(quantity)
                : Validation.invalid(List.of("quantity must be positive"));
    }

    @Test
    void applicativeApSupportsValidatedConstructors() {
        // Ordinary pure Java functions compose with the same accumulating policy.
        var createItem = AP.pure((java.util.function.Function<String,
                java.util.function.Function<Integer, Item>>) name -> qty -> new Item(name, qty));

        var invalid = AP.ap(AP.ap(createItem, sku("missing")), quantity(0));
        assertEquals(Validation.invalid(List.of(
                "SKU must start with SKU-",
                "quantity must be positive")), invalid);

        var valid = AP.ap(AP.ap(createItem, sku("SKU-17")), quantity(3));
        assertEquals(Validation.valid(new Item("SKU-17", 3)), valid);
    }

    @Test
    void validationConvertsToEitherWithoutChangingPayload() {
        Validation<List<String>, Integer> invalid =
                Validation.invalid(List.of("quantity must be positive"));
        assertEquals(Either.left(List.of("quantity must be positive")), invalid.toEither());
        assertEquals(invalid, Validation.fromEither(invalid.toEither()));
    }
}
