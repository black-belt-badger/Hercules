package bbb.hercules.consumer;

import bbb.hercules.These;
import bbb.hercules.Those;
import bbb.hercules.ThoseAggregation;
import bbb.hercules.ThoseShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThoseAggregationPublicApiTest {

    private static <A> List<A> append(List<A> first, List<A> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    @Test
    void combiningTimedOutAndSuccessfulMarketDataRetainsBoth() {
        var aggregation =
                new ThoseAggregation<List<String>, List<String>, List<String>>(
                        ThoseAggregationPublicApiTest::append,
                        ThoseAggregationPublicApiTest::append,
                        ThoseAggregationPublicApiTest::append);

        Those<List<String>, List<String>, List<String>> sourceA =
                Those.neither(List.of("feed A timed out"));
        Those<List<String>, List<String>, List<String>> sourceB =
                Those.right(List.of("BTC 100.00"));
        Those<List<String>, List<String>, List<String>> sourceC =
                Those.left(List.of("ETH 200.00"));

        These<List<String>, These<List<String>, List<String>>> aggregate =
                aggregation.combineAll(List.of(sourceA, sourceB, sourceC));

        assertEquals(These.both(List.of("feed A timed out"),
                These.both(List.of("ETH 200.00"), List.of("BTC 100.00"))),
                aggregate);

        assertEquals(ThoseShape.BOTH,
                ThoseShape.of(sourceA).join(ThoseShape.of(sourceB))
                        .join(ThoseShape.of(sourceC)));
        assertEquals(aggregation.combine(aggregation.combine(sourceA, sourceB),
                aggregation.lift(sourceC)), aggregate);
    }
}
