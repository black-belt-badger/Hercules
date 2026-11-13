package bbb.hercules.consumer;

import bbb.hercules.DList;
import bbb.hercules.Writer;
import bbb.hercules.WriterPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** An independent consumer using Writer to accumulate diagnostic data. */
class WriterPublicApiTest {

    enum Severity { INFO, WARNING }

    record Diagnostic(Severity severity, String step, String message) {
    }

    record Order(int quantity, BigDecimal price) {
    }

    private static final WriterPolicy<DList<Diagnostic>> POLICY =
            new WriterPolicy<>(DList.empty(), DList::append);

    private static Writer<DList<Diagnostic>, Integer> checkQuantity(int quantity) {
        return new Writer<>(quantity, DList.singleton(new Diagnostic(
                quantity > 0 ? Severity.INFO : Severity.WARNING,
                "quantity", quantity > 0 ? "accepted" : "must be positive")));
    }

    private static Writer<DList<Diagnostic>, BigDecimal> checkPrice(
            BigDecimal price) {
        return new Writer<>(price, DList.singleton(new Diagnostic(
                price.signum() > 0 ? Severity.INFO : Severity.WARNING,
                "price", price.signum() > 0 ? "accepted" : "must be positive")));
    }

    @Test
    void collectsDiagnosticsWithoutPerformingLoggingIo() {
        Writer<DList<Diagnostic>, Order> result =
                checkQuantity(4).flatMap(
                        qty -> checkPrice(new BigDecimal("12.50"))
                                .map(price -> new Order(qty, price)),
                        POLICY);

        assertEquals(new Order(4, new BigDecimal("12.50")), result.value());
        assertEquals(List.of(
                new Diagnostic(Severity.INFO, "quantity", "accepted"),
                new Diagnostic(Severity.INFO, "price", "accepted")),
                result.output().toList());

        assertThrows(UnsupportedOperationException.class,
                () -> result.output().toList().add(
                        new Diagnostic(Severity.INFO, "late", "not allowed")));
    }

    @Test
    void warningsDoNotImplicitlyMeanFailureOrException() {
        Writer<DList<Diagnostic>, Order> result =
                checkQuantity(-2).flatMap(
                        qty -> checkPrice(new BigDecimal("12.50"))
                                .map(price -> new Order(qty, price)), POLICY);
        assertEquals(-2, result.value().quantity());
        assertEquals(Severity.WARNING, result.output().toList().get(0).severity());
        assertEquals("accepted", result.output().toList().get(1).message());
    }

    @Test
    void pureHasNoRecordedOutputAndCanBeComposed() {
        Writer<DList<Diagnostic>, Integer> initial = POLICY.pure(10);
        Writer<DList<Diagnostic>, Integer> incremented = initial.flatMap(
                number -> new Writer<>(number + 1,
                        DList.singleton(new Diagnostic(
                                Severity.INFO, "calculation", "incremented"))),
                POLICY);
        assertEquals(List.of(), initial.output().toList());
        assertEquals(11, incremented.value());
        assertEquals(1, incremented.output().toList().size());
    }
}
