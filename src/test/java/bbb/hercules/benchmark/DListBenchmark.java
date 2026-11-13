package bbb.hercules.benchmark;

import bbb.hercules.DList;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manual benchmark; no timing assertions in CI.
 *
 * <p>Run after test compilation with:
 * {@code java -cp target/classes:target/test-classes bbb.hercules.benchmark.DListBenchmark [N]}.
 * This is a single-threaded diagnostic, not a substitute for JMH.
 */
public final class DListBenchmark {

    private static volatile long consumed;
    private static final ThreadMXBean MX = allocator();

    private DListBenchmark() {
    }

    public static void main(String[] args) {
        int count = args.length == 0 ? 100_000 : Integer.parseInt(args[0]);
        if (count < 1) {
            throw new IllegalArgumentException("Count must be positive");
        }
        System.out.println("JVM=" + System.getProperty("java.version")
                + "; workload=" + count + " ordered integers");
        System.out.println("Times are environment-dependent; lower is better.");
        System.out.printf("%-22s %8s %11s %11s %13s %13s%n",
                "Strategy", "elements", "build ms", "freeze ms", "build bytes", "freeze bytes");

        // Small warm-up for each strategy.
        sampleDList(1_000);
        sampleArrayList(1_000);
        sampleStreamBuilder(1_000);
        sampleCopyImmutable(500);

        print("DList (persistent)", count, sampleDList(count));
        print("ArrayList (mutable)", count, sampleArrayList(count));
        print("Stream.Builder", count, sampleStreamBuilder(count));

        int small = Math.min(count, 3_000);
        System.out.println("Same-size comparison (copy-each-append capped due to quadratic cost):");
        print("DList (small)", small, sampleDList(small));
        print("ArrayList (small)", small, sampleArrayList(small));
        print("Stream.Builder (small)", small, sampleStreamBuilder(small));
        print("Copy each append", small, sampleCopyImmutable(small));
        System.out.println("Allocation counters are approximate; -1 means unsupported.");
        System.out.println("DList is for persistent forks/concatenation; for one mutable builder "
                + "ArrayList is simpler and typically faster. Checksum=" + consumed);
    }

    private static Snapshot sampleDList(int n) {
        long before = allocated();
        long start = System.nanoTime();
        DList<Integer> data = DList.empty();
        for (int i = 0; i < n; i++) {
            data = data.snoc(i);
        }
        long buildNanos = System.nanoTime() - start;
        long buildBytes = delta(before);

        before = allocated();
        start = System.nanoTime();
        List<Integer> frozen = data.toList();
        long freezeNanos = System.nanoTime() - start;
        long freezeBytes = delta(before);
        verify(n, frozen);
        return new Snapshot(buildNanos, freezeNanos, buildBytes, freezeBytes);
    }

    private static Snapshot sampleArrayList(int n) {
        long before = allocated();
        long start = System.nanoTime();
        ArrayList<Integer> data = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            data.add(i);
        }
        long buildNanos = System.nanoTime() - start;
        long buildBytes = delta(before);

        before = allocated();
        start = System.nanoTime();
        List<Integer> frozen = Collections.unmodifiableList(new ArrayList<>(data));
        long freezeNanos = System.nanoTime() - start;
        long freezeBytes = delta(before);
        verify(n, frozen);
        return new Snapshot(buildNanos, freezeNanos, buildBytes, freezeBytes);
    }

    private static Snapshot sampleStreamBuilder(int n) {
        long before = allocated();
        long start = System.nanoTime();
        Stream.Builder<Integer> data = Stream.builder();
        for (int i = 0; i < n; i++) {
            data.add(i);
        }
        long buildNanos = System.nanoTime() - start;
        long buildBytes = delta(before);

        before = allocated();
        start = System.nanoTime();
        List<Integer> frozen = data.build().toList();
        long freezeNanos = System.nanoTime() - start;
        long freezeBytes = delta(before);
        verify(n, frozen);
        return new Snapshot(buildNanos, freezeNanos, buildBytes, freezeBytes);
    }

    private static Snapshot sampleCopyImmutable(int n) {
        long before = allocated();
        long start = System.nanoTime();
        List<Integer> data = List.of();
        for (int i = 0; i < n; i++) {
            ArrayList<Integer> copy = new ArrayList<>(data);
            copy.add(i);
            data = Collections.unmodifiableList(copy);
        }
        long buildNanos = System.nanoTime() - start;
        long buildBytes = delta(before);

        before = allocated();
        start = System.nanoTime();
        List<Integer> frozen = data;
        long freezeNanos = System.nanoTime() - start;
        long freezeBytes = delta(before);
        verify(n, frozen);
        return new Snapshot(buildNanos, freezeNanos, buildBytes, freezeBytes);
    }

    private static void verify(int n, List<Integer> values) {
        if (values.size() != n || values.get(0) != 0 || values.get(n - 1) != n - 1) {
            throw new AssertionError("Bad size or element ordering");
        }
        consumed += values.get(0) + values.get(n - 1);
    }

    private static ThreadMXBean allocator() {
        var jdkBean = ManagementFactory.getThreadMXBean();
        if (!(jdkBean instanceof ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            return null;
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocated() {
        return MX == null ? -1 : MX.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long delta(long before) {
        return before < 0 ? -1 : allocated() - before;
    }

    private static void print(String name, int count, Snapshot result) {
        System.out.printf("%-22s %8d %11.3f %11.3f %13d %13d%n",
                name, count,
                result.buildNanos / 1_000_000.0,
                result.freezeNanos / 1_000_000.0,
                result.buildBytes, result.freezeBytes);
    }

    private record Snapshot(long buildNanos, long freezeNanos,
                            long buildBytes, long freezeBytes) {
    }
}
