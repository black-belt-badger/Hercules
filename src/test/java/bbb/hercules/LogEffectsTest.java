package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class LogEffectsTest {

    private static final Instant WHEN = Instant.parse("2026-11-14T07:20:30Z");
    private static final UUID CORRELATION =
            UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CAUSATION =
            UUID.fromString("abcdef12-1234-1234-1234-123456789abc");

    private static LogEvent event(String name, Map<String, LogField> fields) {
        return new LogEvent(LogEvent.Level.INFO, name, WHEN, "MarketScanner",
                Optional.of(CORRELATION), Optional.of(CAUSATION), fields);
    }

    private static LogEvent countEvent(int number) {
        return event("row.processed", Map.of(
                "sequence", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Count(number))));
    }

    @Test
    void renderingIsDeterministicSortedAndValueTyped() {
        Map<String, LogField> unordered = new LinkedHashMap<>();
        unordered.put("z", new LogField(LogField.Visibility.PUBLIC, new LogValue.Flag(true)));
        unordered.put("a", new LogField(LogField.Visibility.PUBLIC, new LogValue.Text("BTC/USD")));
        unordered.put("money", new LogField(LogField.Visibility.PUBLIC,
                new LogValue.Decimal(new BigDecimal("10.500"))));
        unordered.put("size", new LogField(LogField.Visibility.PUBLIC, new LogValue.Count(42)));

        var one = event("price.validated", unordered);
        Map<String, LogField> reverse = new LinkedHashMap<>();
        reverse.put("size", unordered.get("size"));
        reverse.put("money", unordered.get("money"));
        reverse.put("a", unordered.get("a"));
        reverse.put("z", unordered.get("z"));
        var two = event("price.validated", reverse);

        String expected = "at=2026-11-14T07:20:30Z level=INFO"
                + " name=price.validated source=MarketScanner"
                + " correlation=12345678-1234-1234-1234-123456789abc"
                + " causation=abcdef12-1234-1234-1234-123456789abc"
                + " field.a=\"BTC/USD\" field.money=10.500 field.size=42 field.z=true";

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
        assertEquals(expected, LogFormatter.format(one));
        assertEquals(expected, one.toString());
        unordered.put("added", new LogField(LogField.Visibility.PUBLIC,
                new LogValue.Text("late")));
        assertEquals(4, one.fields().size(), "constructor must snapshot input map");
        assertThrows(UnsupportedOperationException.class, () ->
                one.fields().put("mutate",
                        new LogField(LogField.Visibility.PUBLIC, new LogValue.Flag(true))));
    }

    @Test
    void textEscapingPreventsMultilineDiagnosticInjection() {
        var source = event("payload.parsed", Map.of(
                "text", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Text("first\nsecond\r\t\"quoted\"\\next\u0001"))));
        String rendered = LogFormatter.format(source);
        assertTrue(rendered.contains(
                "field.text=\"first\\nsecond\\r\\t\\\"quoted\\\"\\\\next\\u0001\""));
        assertFalse(rendered.contains("\n"));
        assertFalse(rendered.contains("\r"));
        assertFalse(rendered.contains("\t"));
        assertFalse(rendered.contains("\u0001"));
    }

    @Test
    void bothPersonalAndSecretPayloadsAreRedactedBeforeSinks() {
        String secret = "SECRET-ACCESS-TOKEN";
        String person = "person@example.test";
        var source = event("authentication.failed", Map.of(
                "email", new LogField(LogField.Visibility.PERSONAL, new LogValue.Text(person)),
                "token", new LogField(LogField.Visibility.SECRET, new LogValue.Text(secret)),
                "attempts", new LogField(LogField.Visibility.PUBLIC, new LogValue.Count(4)),
                "secret_amount", new LogField(LogField.Visibility.SECRET,
                        new LogValue.Decimal(new BigDecimal("123456789.1234")))));

        // The pure producer still owns the raw data. Nothing leaves the
        // interpreter until an external sink has been deliberately supplied.
        assertEquals(secret, ((LogValue.Text) source.fields().get("token").value()).value());
        assertFalse(source.toString().contains(secret));
        assertFalse(source.toString().contains(person));
        assertFalse(source.toString().contains("123456789.1234"));
        assertFalse(source.fields().get("token").toString().contains(secret));
        assertFalse(source.fields().get("email").toString().contains(person));
        assertFalse(new LogValue.Text(secret).toString().contains(secret));

        var sent = new ArrayList<LogEvent>();
        LogInterpreter.Report report = LogInterpreter.emit(List.of(source), sent::add,
                LogPolicy.strict(10));
        assertEquals(new LogInterpreter.Report(1, 1, 1, 0, false), report);
        assertEquals(1, sent.size());
        LogEvent safe = sent.get(0);
        assertNotSame(source, safe);
        assertEquals("[REDACTED]",
                ((LogValue.Text) safe.fields().get("email").value()).value());
        assertEquals("[REDACTED]",
                ((LogValue.Text) safe.fields().get("token").value()).value());
        assertEquals("[REDACTED]",
                ((LogValue.Text) safe.fields().get("secret_amount").value()).value());
        assertEquals(4, ((LogValue.Count) safe.fields().get("attempts").value()).value());

        String rendered = LogFormatter.format(safe);
        assertFalse(rendered.contains(secret));
        assertFalse(rendered.contains(person));
        assertFalse(rendered.contains("123456789.1234"));
        assertTrue(rendered.contains("field.email=\"[REDACTED]\""));
        assertTrue(rendered.contains("field.token=\"[REDACTED]\""));
        assertTrue(rendered.contains("field.attempts=4"));
        assertEquals(source, event("authentication.failed", source.fields()),
                "redaction must not mutate the original event");
    }

    @RepeatedTest(64)
    void formattingAndCollectionAreDeterministicAcrossInputOrder(
            @Given String publicValue, @Given Long count) {
        var first = event("sample.completed", Map.of(
                "z", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Text(publicValue)),
                "a", new LogField(LogField.Visibility.PUBLIC,
                        new LogValue.Count(count))));
        var second = event("sample.completed", new LinkedHashMap<>(Map.of(
                "a", first.fields().get("a"), "z", first.fields().get("z"))));

        assertEquals(first, second);
        assertEquals(LogFormatter.format(first), LogFormatter.format(second));
        assertEquals(LogInterpreter.collect(List.of(first), LogPolicy.strict(2)),
                LogInterpreter.collect(List.of(second), LogPolicy.strict(2)));
    }

    @Test
    void boundedSamplingPreservesEncounterOrderAndReturnsExactCounts() {
        var input = IntStream.range(0, 12).mapToObj(LogEffectsTest::countEvent).toList();
        var report = LogInterpreter.collect(input, new LogPolicy(10, 3,
                LogPolicy.FailureMode.PROPAGATE));
        assertEquals(new LogInterpreter.Report(10, 4, 4, 0, true), report.report());
        assertEquals(List.of(0L, 3L, 6L, 9L), report.events().stream()
                .map(e -> ((LogValue.Count) e.fields().get("sequence").value()).value())
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> report.events().clear());

        assertEquals(new LogInterpreter.Report(12, 4, 4, 0, false),
                LogInterpreter.silent(input, new LogPolicy(12, 3,
                        LogPolicy.FailureMode.PROPAGATE)));
    }

    @Test
    void hardBoundTerminatesEvenWithUnboundedSource() {
        var nextCalls = new AtomicInteger();
        Iterable<LogEvent> infinite = () -> new Iterator<>() {
            @Override public boolean hasNext() { return true; }
            @Override public LogEvent next() {
                return countEvent(nextCalls.getAndIncrement());
            }
        };
        LogInterpreter.Collected result = LogInterpreter.collect(infinite,
                new LogPolicy(10, 4, LogPolicy.FailureMode.PROPAGATE));
        assertEquals(10, nextCalls.get());
        assertEquals(new LogInterpreter.Report(10, 3, 3, 0, true), result.report());
        assertEquals(List.of(0L, 4L, 8L), result.events().stream()
                .map(e -> ((LogValue.Count) e.fields().get("sequence").value()).value())
                .toList());
    }

    @Test
    void sinkExceptionsEitherPropagateOrAreCountedExplicitly() {
        var input = List.of(countEvent(1), countEvent(2), countEvent(3));
        var failure = new IllegalArgumentException("backend offline");
        var submitted = new ArrayList<Long>();
        var report = LogInterpreter.emit(input, event -> {
            long number = ((LogValue.Count) event.fields().get("sequence").value()).value();
            if (number == 2) {
                throw failure;
            }
            submitted.add(number);
        }, new LogPolicy(4, 1, LogPolicy.FailureMode.COUNT_AND_CONTINUE));
        assertEquals(new LogInterpreter.Report(3, 3, 2, 1, false), report);
        assertEquals(List.of(1L, 3L), submitted);

        var firstCalls = new AtomicInteger();
        assertSame(failure, assertThrows(IllegalArgumentException.class, () ->
                LogInterpreter.emit(input, event -> {
                    firstCalls.incrementAndGet();
                    throw failure;
                }, LogPolicy.strict(4))));
        assertEquals(1, firstCalls.get(), "strict policy aborts immediately");
    }

    @Test
    void iterationErrorsAreNotTreatedAsBackendFailures() {
        var failure = new IllegalStateException("iterator failed");
        Iterable<LogEvent> broken = () -> new Iterator<>() {
            @Override public boolean hasNext() { throw failure; }
            @Override public LogEvent next() { return countEvent(0); }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                LogInterpreter.silent(broken, new LogPolicy(3, 1,
                        LogPolicy.FailureMode.COUNT_AND_CONTINUE))));
    }

    @Test
    void silentRunsAreBoundedWithoutCallingAnExternalLogger() {
        var input = List.of(countEvent(1), countEvent(2), countEvent(3));
        var sinkCalls = new AtomicInteger();
        // Constructing/holding pure data never invokes an effectful sink.
        assertEquals(0, sinkCalls.get());
        LogInterpreter.Report summary = LogInterpreter.emit(input,
                event -> sinkCalls.incrementAndGet(), LogPolicy.strict(3));
        assertEquals(3, sinkCalls.get());
        assertEquals(new LogInterpreter.Report(3, 3, 3, 0, false), summary);
        assertEquals(new LogInterpreter.Report(3, 3, 3, 0, false),
                LogInterpreter.silent(input, LogPolicy.strict(3)));
        assertEquals(3, sinkCalls.get(), "silent mode cannot call external sink");
    }

    @Test
    void nullnessBoundsAndMetadataAreValidated() {
        var valid = countEvent(1);
        assertThrows(NullPointerException.class, () -> LogFormatter.format(null));
        assertThrows(NullPointerException.class, () ->
                LogInterpreter.emit(null, event -> { }, LogPolicy.strict(1)));
        assertThrows(NullPointerException.class, () ->
                LogInterpreter.emit(List.of(valid), null, LogPolicy.strict(1)));
        assertThrows(NullPointerException.class, () ->
                LogInterpreter.emit(List.of(valid), event -> { }, null));
        assertThrows(NullPointerException.class, () ->
                LogInterpreter.collect(null, LogPolicy.strict(1)));
        assertThrows(NullPointerException.class, () -> LogInterpreter.silent(
                List.of(valid), null));
        assertThrows(NullPointerException.class, () -> new LogValue.Text(null));
        assertThrows(NullPointerException.class, () -> new LogValue.Decimal(null));
        assertThrows(NullPointerException.class, () -> new LogField(null, new LogValue.Flag(true)));
        assertThrows(NullPointerException.class, () -> new LogField(
                LogField.Visibility.PUBLIC, null));
        assertThrows(NullPointerException.class, () -> new LogPolicy(4, 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new LogPolicy(0, 1, LogPolicy.FailureMode.PROPAGATE));
        assertThrows(IllegalArgumentException.class, () ->
                new LogPolicy(1, 0, LogPolicy.FailureMode.PROPAGATE));
        assertThrows(IllegalArgumentException.class, () -> LogPolicy.strict(-1));
        assertThrows(IllegalArgumentException.class, () -> event("bad name", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> event("blank", Map.of(
                "bad key", new LogField(LogField.Visibility.PUBLIC, new LogValue.Flag(true)))));
        Map<String, LogField> badFields = new HashMap<>();
        badFields.put("missing", null);
        assertThrows(NullPointerException.class,
                () -> event("null.field", badFields));
    }

    @Test
    void redactedCollectedEventsRemainImmutableAndIdsSurvive() {
        var original = event("sync.completed", Map.of(
                "account", new LogField(LogField.Visibility.PERSONAL,
                        new LogValue.Text("private person"))));
        var collected = LogInterpreter.collect(List.of(original), LogPolicy.strict(1));
        var safe = collected.events().get(0);
        assertEquals(Optional.of(CORRELATION), safe.correlationId());
        assertEquals(Optional.of(CAUSATION), safe.causationId());
        assertEquals(WHEN, safe.occurredAt());
        assertEquals(LogEvent.Level.INFO, safe.level());
        assertThrows(UnsupportedOperationException.class, () ->
                safe.fields().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                collected.events().add(original));
    }
}
