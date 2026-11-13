package bbb.hercules.benchmark;

import bbb.hercules.Reader;

/**
 * Manual JVM stack-limit experiment; intentionally not a JUnit test.
 *
 * <p>Run after test compilation with a small fixed thread stack:
 * {@code java -Xss512k -cp target/classes:target/test-classes
 * bbb.hercules.benchmark.ReaderDepthProbe 100000}.
 *
 * <p>This specifically demonstrates the Java nested-lambda limitation;
 * it does not affect the safety of constructing an unexecuted Reader.
 */
public final class ReaderDepthProbe {

    private ReaderDepthProbe() {
    }

    public static void main(String[] args) {
        int depth = args.length == 0 ? 100_000 : Integer.parseInt(args[0]);
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }

        Reader<Integer, Integer> chain = Reader.ask();
        for (int step = 0; step < depth; step++) {
            chain = chain.flatMap(n -> Reader.pure(n + 1));
        }

        System.out.println("Constructed lazy Reader with " + depth + " binds.");
        try {
            System.out.println("Result: " + chain.run(0));
        } catch (StackOverflowError error) {
            System.out.println("StackOverflowError during run: naive nested Reader "
                    + "flatMap composition is not stack-safe on this JVM.");
        }
    }
}
