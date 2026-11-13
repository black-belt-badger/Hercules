package bbb.hercules.consumer;

import bbb.hercules.Either;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EitherFilterPublicApiTest {

    @Test
    void rejectedPositiveQuantityProducesAConcreteDomainReason() {
        Either<String, Integer> quantity = Either.right(-2);

        assertEquals(Either.left("quantity must be positive: -2"),
                quantity.filterOrElse(
                        n -> n > 0,
                        n -> "quantity must be positive: " + n));
    }

    @Test
    void alreadyInvalidInputRetainsItsOriginalLeft() {
        Either<String, Integer> missing = Either.left("quantity not supplied");
        assertEquals(missing, missing.filterOrElse(
                n -> n > 0,
                n -> "must be positive"));
    }

    @Test
    void passingRightCanContinueInTheSamePipeline() {
        Either<String, String> result = Either.<String, Integer>right(5)
                .filterOrElse(n -> n > 0, n -> "invalid: " + n)
                .map(n -> "quantity=" + n);

        assertEquals(Either.right("quantity=5"), result);
    }
}
