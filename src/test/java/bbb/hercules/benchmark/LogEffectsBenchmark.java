package bbb.hercules.benchmark;

import bbb.hercules.LogEvent;
import bbb.hercules.LogField;
import bbb.hercules.LogFormatter;
import bbb.hercules.LogInterpreter;
import bbb.hercules.LogPolicy;
import bbb.hercules.LogValue;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Manual benchmark of log description and interpretation overhead.
 *
 * <p>Run after compiling tests:
 * {@code java -cp target/classes:target/test-classes
 * bbb.hercules.benchmark.LogEffectsBenchmark 100000}.
 * This is not a statistical JMH benchmark; no thresholds run in CI.
 */
public final class LogEffectsBenchmark {
    private static volatile long checksum;
    private static final ThreadMXBean MX = allocator();

    private LogEffectsBenchmark() {
    }

    public static void main(String[] args) {
        int count = args.length == 0 ? 100_000 : Integer.parseInt(args[0]);
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }

        // Warm up startup-dependent bytecode paths.
        List<LogEvent> warmup = build(1_000);
        direct(warmup);
        LogInterpreter.silent(warmup, LogPolicy.strict(1_000));
        LogInterpreter.emit(warmup, event ->
                checksum += LogFormatter.format(event).length(), LogPolicy.strict(1_000));

        System.out.println("JVM=" + System.getProperty("java.version")
                + ", entries=" + count);
        System.out.printf("%-26s %12s %17s %14s%n",
                "Operation", "time ms", "allocated bytes", "result");
        show("Construct immutable events", measured(() -> build(count).size()));

        List<LogEvent> events = build(count);
        show("Direct loop (no log)", measured(() -> direct(events)));
        show("Silent bounded interpreter", measured(() ->
                LogInterpreter.silent(events, LogPolicy.strict(count)).inspected()));
        show("Emit (redact + format)", measured(() -> {
            LogInterpreter.Report report = LogInterpreter.emit(events, safe ->
                    checksum += LogFormatter.format(safe).length(),
                    LogPolicy.strict(count));
            return report.accepted();
        }));
        show("Collect sample (1 in 10)", measured(() ->
                LogInterpreter.collect(events, new LogPolicy(count, 10,
                        LogPolicy.FailureMode.PROPAGATE)).events().size()));
        System.out.println("Checksummed=" + checksum);
        System.out.println("All timings are single-run, load/JIT/GC dependent diagnostics.");
    }

    private static List<LogEvent> build(int size) {
        ArrayList<LogEvent> result = new ArrayList<>(size);
        Instant time = Instant.parse("2026-11-14T08:00:00Z");
        for (int i = 0; i < size; i++) {
            result.add(new LogEvent(LogEvent.Level.INFO, "bar.received", time,
                    "HistoryScanner", Optional.empty(), Optional.empty(), Map.of(
                            "symbol", new LogField(LogField.Visibility.PUBLIC,
                                    new LogValue.Text("BTCUSD")),
                            "offset", new LogField(LogField.Visibility.PUBLIC,
                                    new LogValue.Count(i)),
                            "token", new LogField(LogField.Visibility.SECRET,
                                    new LogValue.Text("simulated-sensitive-data")))));
        }
        return result;
    }

    private static long direct(List<LogEvent> input) {
        long result = 0;
        for (LogEvent event : input) {
            result += event.fields().size();
        }
        return result;
    }

    private static Sample measured(LongSupplier work) {
        long before = allocated();
        long start = System.nanoTime();
        long value = work.getAsLong();
        return new Sample(System.nanoTime() - start, allocated() - before, value);
    }

    private static void show(String description, Sample data) {
        System.out.printf("%-26s %12.3f %17d %14d%n",
                description, data.nanos() / 1_000_000.0, data.bytes(), data.value());
        checksum += data.value();
    }

    private static ThreadMXBean allocator() {
        var original = ManagementFactory.getThreadMXBean();
        if (!(original instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            return null;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocated() {
        return MX == null ? 0 : MX.getThreadAllocatedBytes(
                Thread.currentThread().getId());
    }

    private record Sample(long nanos, long bytes, long value) {
    }
}
