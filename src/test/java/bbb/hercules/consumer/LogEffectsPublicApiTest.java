package bbb.hercules.consumer;

import bbb.hercules.DList;
import bbb.hercules.LogEvent;
import bbb.hercules.LogField;
import bbb.hercules.LogFormatter;
import bbb.hercules.LogInterpreter;
import bbb.hercules.LogPolicy;
import bbb.hercules.LogValue;
import bbb.hercules.Reader;
import bbb.hercules.State;
import bbb.hercules.Writer;
import bbb.hercules.WriterPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Consumer-side examples: no logging backend dependency in Hercules. */
class LogEffectsPublicApiTest {

    record Context(Instant at, UUID correlation, String source) {
    }

    record Cursor(int position, int processed) {
        Cursor advance() {
            return new Cursor(position + 1, processed + 1);
        }
    }

    private static final WriterPolicy<DList<LogEvent>> POLICY =
            new WriterPolicy<>(DList.empty(), DList::append);

    private static LogEvent event(Context ctx, String name, String symbol) {
        return new LogEvent(LogEvent.Level.INFO, name, ctx.at(), ctx.source(),
                Optional.of(ctx.correlation()), Optional.empty(), Map.of(
                        "symbol", new LogField(LogField.Visibility.PUBLIC,
                                new LogValue.Text(symbol)),
                        "customer", new LogField(LogField.Visibility.PERSONAL,
                                new LogValue.Text("Private Person"))));
    }

    @Test
    void composesReaderWriterAndStateWithoutTriggeringAnyBackend() {
        Context context = new Context(Instant.parse("2026-11-14T09:30:00Z"),
                UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                "PriceScanner");
        var backendCalls = new AtomicInteger();

        Reader<Context, Writer<DList<LogEvent>, State.Result<Cursor, Integer>>> pipeline =
                Reader.asks(ctx -> {
                    State<Cursor, Integer> step = State.<Cursor>get()
                            .flatMap(before -> State.<Cursor>modify(Cursor::advance)
                                    .map(ignored -> before.position()));
                    State.Result<Cursor, Integer> transition =
                            step.run(new Cursor(10, 0));
                    return new Writer<>(transition,
                            DList.singleton(event(ctx, "cursor.advanced", "BTCUSD")));
                });

        // Construction of the reader has no effects.
        assertEquals(0, backendCalls.get());
        Writer<DList<LogEvent>, State.Result<Cursor, Integer>> first = pipeline.run(context);
        Writer<DList<LogEvent>, Integer> withAnotherEvent = first.flatMap(result ->
                new Writer<>(result.value(), DList.singleton(
                        event(context, "cursor.checked", "ETHUSD"))), POLICY);

        assertEquals(10, withAnotherEvent.value());
        assertEquals(new Cursor(11, 1), first.value().state());
        assertEquals(2, withAnotherEvent.output().toList().size());
        assertEquals(0, backendCalls.get(), "pure description is not output delivery");

        List<String> lines = new ArrayList<>();
        var summary = LogInterpreter.emit(withAnotherEvent.output().toList(), safe -> {
            backendCalls.incrementAndGet();
            lines.add(LogFormatter.format(safe));
        }, LogPolicy.strict(10));

        assertEquals(new LogInterpreter.Report(2, 2, 2, 0, false), summary);
        assertEquals(2, backendCalls.get());
        assertTrue(lines.get(0).contains("name=cursor.advanced"));
        assertTrue(lines.get(1).contains("name=cursor.checked"));
        assertTrue(lines.get(0).contains("field.customer=\"[REDACTED]\""));
        assertTrue(lines.get(0).contains("field.symbol=\"BTCUSD\""));
        assertFalse(lines.get(0).contains("Private Person"));
        assertEquals(1, first.output().toList().size(),
                "Writer's earlier branch was not changed");
    }

    @Test
    void optionalLoggerBackendCanUseJdkConsumerOnly() {
        Context context = new Context(Instant.parse("2026-11-14T08:00:00Z"),
                UUID.randomUUID(), "ExecutionAdapter");
        var delivered = new ArrayList<String>();
        // A real caller can use: safe -> logger.info("{}", LogFormatter.format(safe)).
        // Here we demonstrate the same boundary without pulling SLF4J into Hercules.
        java.util.function.Consumer<LogEvent> backend =
                safe -> delivered.add(LogFormatter.format(safe));

        LogInterpreter.emit(List.of(event(context, "order.validated", "SOLUSD")),
                backend, LogPolicy.strict(1));
        assertEquals(1, delivered.size());
        assertTrue(delivered.get(0).contains("name=order.validated"));
    }

    @Test
    void structuredNumericValuesRemainTypedUntilInterpretation() {
        var ctx = new Context(Instant.parse("2026-11-14T08:00:00Z"),
                UUID.randomUUID(), "Analytics");
        var event = new LogEvent(LogEvent.Level.INFO, "pnl.calculated",
                ctx.at(), ctx.source(), Optional.empty(), Optional.empty(),
                Map.of("pnl", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Decimal(new BigDecimal("-12.40")))));
        var safe = LogInterpreter.collect(List.of(event), LogPolicy.strict(1)).events().get(0);
        assertEquals(new BigDecimal("-12.40"),
                ((LogValue.Decimal) safe.fields().get("pnl").value()).value());
        assertTrue(LogFormatter.format(safe).contains("field.pnl=-12.40"));
    }
}
