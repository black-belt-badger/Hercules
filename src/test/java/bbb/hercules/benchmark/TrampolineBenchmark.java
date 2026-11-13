package bbb.hercules.benchmark;

import bbb.hercules.Trampoline;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

/**
 * Reproducible opt-in, single-threaded benchmark comparing Trampoline
 * with explicit loops. No timing assertions run during JUnit/CI.
 *
 * <p>Run after test compilation:
 * {@code java -cp target/classes:target/test-classes
 * bbb.hercules.benchmark.TrampolineBenchmark 1000000}.
 *
 * <p>Reports elapsed milliseconds and thread-allocated bytes. Results
 * depend on JIT warmup, heap, machine load and garbage collection; for
 * statistically rigorous measurements use JMH.
 */
public final class TrampolineBenchmark {

    private static final ThreadMXBean ALLOCATION = allocator();
    private static volatile long consumed;

    private TrampolineBenchmark() {
    }

    public static void main(String[] args) {
        int count = args.length == 0 ? 1_000_000 : Integer.parseInt(args[0]);
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }

        // Warmup, deliberately below the expensive full workload.
        tail(20_000, 0).run();
        imperativeSum(20_000);
        bind(20_000).run();
        imperativeCount(20_000);

        System.out.println("JVM=" + System.getProperty("java.version")
                + ", steps=" + count + ", thread allocation enabled=" + (ALLOCATION != null));
        System.out.printf("%-22s %12s %16s %18s%n",
                "Strategy", "time ms", "allocated bytes", "value");
        show("Explicit sum loop", measure(() -> imperativeSum(count)));
        show("Trampoline More", measure(() -> tail(count, 0).run()));
        show("Explicit count loop", measure(() -> imperativeCount(count)));
        show("Trampoline binds", measure(() -> bind(count).run()));
        System.out.println("Checksum=" + consumed);
        System.out.println("These are diagnostic single-run measurements, not JMH throughput results.");
    }

    private static Trampoline<Long> tail(long n, long total) {
        return n == 0 ? Trampoline.done(total)
                : Trampoline.more(() -> tail(n - 1, total + n));
    }

    private static long imperativeSum(long n) {
        long total = 0;
        for (long i = n; i > 0; i--) {
            total += i;
        }
        return total;
    }

    private static Trampoline<Long> bind(int count) {
        Trampoline<Long> state = Trampoline.done(0L);
        for (int i = 0; i < count; i++) {
            state = state.flatMap(x -> Trampoline.done(x + 1));
        }
        return state;
    }

    private static long imperativeCount(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) {
            total += 1;
        }
        return total;
    }

    private static Result measure(java.util.function.LongSupplier body) {
        long beforeBytes = allocated();
        long start = System.nanoTime();
        long value = body.getAsLong();
        long time = System.nanoTime() - start;
        long bytes = allocated() - beforeBytes;
        consumed += value;
        return new Result(time, bytes, value);
    }

    private static ThreadMXBean allocator() {
        var platform = ManagementFactory.getThreadMXBean();
        if (!(platform instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            return null;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocated() {
        return ALLOCATION == null ? 0 : ALLOCATION.getThreadAllocatedBytes(
                Thread.currentThread().getId());
    }

    private static void show(String name, Result result) {
        System.out.printf("%-22s %12.3f %16d %18d%n",
                name, result.nanos() / 1_000_000.0, result.bytes(), result.value());
    }

    private record Result(long nanos, long bytes, long value) {
    }
}
