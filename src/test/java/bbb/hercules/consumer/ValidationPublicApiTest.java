package bbb.hercules.consumer;

import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the released API surface from outside its implementation package. */
class ValidationPublicApiTest {

    @Test
    void consumerUsesMap2AndApAndEitherConversion() {
        var policy = new ValidationApplicative<List<String>>(
                (first, second) -> Stream.concat(first.stream(), second.stream()).toList());

        Validation<List<String>, Integer> first = Validation.valid(10);
        Validation<List<String>, Integer> second = Validation.invalid(List.of("missing field"));

        assertEquals(Validation.invalid(List.of("missing field")),
                policy.map2(first, second, Integer::sum));

        Function<Integer, String> format = value -> "value=" + value;
        assertEquals(Validation.valid("value=10"),
                policy.ap(policy.pure(format), first));
        assertEquals(first, Validation.fromEither(first.toEither()));
    }
}
