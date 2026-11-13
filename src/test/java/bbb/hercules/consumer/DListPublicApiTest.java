package bbb.hercules.consumer;

import bbb.hercules.DList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DListPublicApiTest {

    enum Severity { INFO, WARNING }

    /** Diagnostic is pure data, not a side effect or I/O command. */
    record Diagnostic(Severity level, String subject, String detail) {
    }

    private static DList<Diagnostic> validateQuantity(int quantity) {
        return quantity > 0
                ? DList.singleton(new Diagnostic(
                        Severity.INFO, "quantity", "positive"))
                : DList.singleton(new Diagnostic(
                        Severity.WARNING, "quantity", "must be positive"));
    }

    private static DList<Diagnostic> validatePrice(BigDecimal price) {
        return price.signum() > 0
                ? DList.singleton(new Diagnostic(
                        Severity.INFO, "price", "positive"))
                : DList.singleton(new Diagnostic(
                        Severity.WARNING, "price", "must be positive"));
    }

    @Test
    void validationStagesAccumulateDiagnosticsAsDataWithoutLogging() {
        DList<Diagnostic> beginning = DList.singleton(
                new Diagnostic(Severity.INFO, "request", "received"));
        DList<Diagnostic> checks = validateQuantity(-1)
                .append(validatePrice(new BigDecimal("12.50")));
        DList<Diagnostic> result = beginning.append(checks)
                .snoc(new Diagnostic(Severity.INFO, "request", "checks completed"));

        assertEquals(List.of(
                new Diagnostic(Severity.INFO, "request", "received"),
                new Diagnostic(Severity.WARNING, "quantity", "must be positive"),
                new Diagnostic(Severity.INFO, "price", "positive"),
                new Diagnostic(Severity.INFO, "request", "checks completed")),
                result.toList());

        // A caller chooses whether to interpret/render diagnostics.
        assertEquals(List.of("received", "must be positive", "positive", "checks completed"),
                result.toList().stream().map(Diagnostic::detail).toList());
        assertEquals(3, beginning.append(checks).toList().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.toList().clear());
    }

    @Test
    void inputCollectionsDoNotBecomeMutableBackdoors() {
        var events = new java.util.ArrayList<>(List.of(
                new Diagnostic(Severity.INFO, "a", "first")));
        DList<Diagnostic> captured = DList.fromList(events);
        events.add(new Diagnostic(Severity.INFO, "b", "added later"));
        assertEquals(1, captured.toList().size());
    }
}
