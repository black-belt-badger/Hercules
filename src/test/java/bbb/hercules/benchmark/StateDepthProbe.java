package bbb.hercules.benchmark;

import bbb.hercules.State;

/**
 * Manual, non-JUnit JVM stack-depth experiment for nested State binds.
 *
 * <p>After test compilation run with
 * {@code java -Xss512k -cp target/classes:target/test-classes
 * bbb.hercules.benchmark.StateDepthProbe 100000}.
 *
 * <p>The intentionally recursive composition is only used to demonstrate
 * the JVM stack limit. Hercules does not promise stack-safe evaluation of
 * arbitrary nested State (or Reader) lambda chains.
 */
public final class StateDepthProbe {

    private StateDepthProbe() {
    }

    public static void main(String[] args) {
        int depth = args.length == 0 ? 100_000 : Integer.parseInt(args[0]);
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }

        State<Integer, Integer> chain = State.get();
        for (int index = 0; index < depth; index++) {
            chain = chain.flatMap(n -> State.pure(n + 1));
        }

        System.out.println("Constructed lazy State with " + depth + " binds.");
        try {
            State.Result<Integer, Integer> result = chain.run(0);
            System.out.println("Result: state=" + result.state()
                    + ", value=" + result.value());
        } catch (StackOverflowError overflow) {
            System.out.println("StackOverflowError during run: nested State flatMap "
                    + "composition is not stack-safe on this JVM.");
        }
    }
}
