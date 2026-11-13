package bbb.hercules.consumer;

import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Exercises CompletionStage composition from outside the implementation package. */
class ValidationStagesPublicApiTest {

    record Order(String symbol, BigDecimal amount) {
    }

    private static final ValidationApplicative<List<String>> APP =
            new ValidationApplicative<>((a, b) ->
                    Stream.concat(a.stream(), b.stream()).toList());

    @Test
    void heterogeneousPreExistingStagesCombineIndependentChecks() {
        var symbol = new CompletableFuture<Validation<List<String>, String>>();
        var amount = new CompletableFuture<Validation<List<String>, BigDecimal>>();

        CompletionStage<Validation<List<String>, Order>> combined =
                APP.map2Stages(symbol, amount, Order::new);

        amount.complete(Validation.valid(new BigDecimal("10.25")));
        assertFalse(combined.toCompletableFuture().isDone());
        symbol.complete(Validation.valid("BTC"));

        assertEquals(
                Validation.valid(new Order("BTC", new BigDecimal("10.25"))),
                combined.toCompletableFuture().join());
    }

    @Test
    void sequenceStageResultsPreservesDeclaredOrderAndErrors() {
        var first = new CompletableFuture<Validation<List<String>, Integer>>();
        var second = new CompletableFuture<Validation<List<String>, Integer>>();
        var third = new CompletableFuture<Validation<List<String>, Integer>>();
        CompletionStage<Validation<List<String>, List<Integer>>> combined =
                APP.sequenceStages(List.of(first, second, third));

        third.complete(Validation.invalid(List.of("bad third")));
        first.complete(Validation.invalid(List.of("bad first")));
        second.complete(Validation.valid(7));

        assertEquals(Validation.invalid(List.of("bad first", "bad third")),
                combined.toCompletableFuture().join());
    }
}
