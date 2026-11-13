package bbb.hercules;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A deferred computation evaluated by an iterative, stack-safe interpreter.
 *
 * <p>{@code Trampoline<A>} has two public, immutable variants:
 * {@link Done}, containing a completed value, and {@link More}, containing
 * one suspended step. A private, typed bind node represents dependent
 * composition. Bind nodes are reassociated one step at a time, so a
 * deeply left-associated {@link #flatMap(Function)} chain never requires
 * nested Java calls when {@link #run()} evaluates it.
 *
 * <p>Constructors and combinators do not execute suppliers or mappers.
 * Calling {@code run()} repeatedly evaluates the description repeatedly;
 * neither values nor user-supplied effects are memoized. Exceptions thrown
 * by suppliers and callbacks propagate unchanged, and null structural
 * Trampoline results fail with {@link NullPointerException} when evaluated.
 *
 * <p>No scheduler, virtual thread, concurrency, asynchronous callback,
 * cancellation protocol or network access is involved. Stack safety is
 * about removing Java call-stack growth, not reducing the number of steps.
 * An infinite suspended computation does not terminate.
 *
 * <p>JSpecify explicitly nullable result payloads are supported; node,
 * callback and supplier references must be non-null. Java cannot prove
 * user callbacks pure. External mutations or user-managed recursive
 * calls can still have effects or overflow independent of Trampoline.
 *
 * @param <A> result value type
 */
public abstract sealed class Trampoline<A extends @Nullable Object> {

    private Trampoline() {
    }

    /**
     * Evaluate the computation on the calling thread using an iterative loop.
     *
     * <p>Every suspended step and pending bind is processed without growing
     * the Java stack. Does not cache a result, start tasks, or catch failures.
     *
     * @return the completed payload, which may be explicitly nullable
     */
    public final A run() {
        Trampoline<A> current = this;
        while (!current.complete()) {
            current = current.advance();
        }
        return current.completedValue();
    }

    /**
     * Transform the completed value without executing anything yet.
     *
     * @param <B> mapped value type
     * @param mapper callback applied exactly once for a successful run
     * @return a lazily composed computation
     */
    public final <B extends @Nullable Object> Trampoline<B> map(
            @NonNull Function<? super A, ? extends B> mapper) {
        return flatMap(value -> done(mapper.apply(value)));
    }

    /**
     * Build a dependent computation without forcing this one.
     *
     * <p>When evaluated, the continuation runs exactly once after the
     * previous result is available. Nested bind nodes are reassociated
     * iteratively, not executed by recursively invoking {@code run()}.
     *
     * @param <B> next value type
     * @param binder produces the next, non-null Trampoline
     * @return a lazy dependent computation
     */
    public final <B extends @Nullable Object> Trampoline<B> flatMap(
            @NonNull Function<? super A, ? extends Trampoline<B>> binder) {
        return new Bind<>(this, binder);
    }

    /**
     * Construct a completed computation, including an explicitly nullable
     * payload when permitted by the type argument.
     *
     * @param <A> value type
     * @param value completed value
     * @return an immutable completed node
     */
    public static <A extends @Nullable Object> Trampoline<A> done(A value) {
        return new Done<>(value);
    }

    /**
     * Suspend exactly one step; do not evaluate the supplier now.
     *
     * @param <A> result type
     * @param next supplier producing the next non-null computation node
     * @return an immutable suspended node
     */
    public static <A extends @Nullable Object> Trampoline<A> more(
            @NonNull Supplier<? extends Trampoline<A>> next) {
        return new More<>(next);
    }

    abstract boolean complete();

    abstract A completedValue();

    abstract Trampoline<A> advance();

    abstract <B extends @Nullable Object> Trampoline<B> continueWith(
            Function<? super A, ? extends Trampoline<B>> next);

    /** Completed terminal value. */
    public static final class Done<A extends @Nullable Object> extends Trampoline<A> {

        private final A value;

        /**
         * Construct a terminal result.
         *
         * @param value completed value
         */
        public Done(A value) {
            this.value = value;
        }

        /** @return completed payload */
        public A value() {
            return value;
        }

        @Override
        boolean complete() {
            return true;
        }

        @Override
        A completedValue() {
            return value;
        }

        @Override
        Trampoline<A> advance() {
            throw new IllegalStateException("Cannot advance Done");
        }

        @Override
        <B extends @Nullable Object> Trampoline<B> continueWith(
                Function<? super A, ? extends Trampoline<B>> next) {
            return Objects.requireNonNull(next.apply(value),
                    "flatMap returned null Trampoline");
        }
    }

    /** Suspended step which executes its supplier only when interpreted. */
    public static final class More<A extends @Nullable Object> extends Trampoline<A> {

        private final Supplier<? extends Trampoline<A>> next;

        /**
         * Construct a suspended step without evaluating it.
         *
         * @param next supplier returning the next non-null computation node
         */
        public More(@NonNull Supplier<? extends Trampoline<A>> next) {
            this.next = next;
        }

        @Override
        boolean complete() {
            return false;
        }

        @Override
        A completedValue() {
            throw new IllegalStateException("More has no completed value");
        }

        @Override
        Trampoline<A> advance() {
            return Objects.requireNonNull(next.get(), "More returned null Trampoline");
        }

        @Override
        <B extends @Nullable Object> Trampoline<B> continueWith(
                Function<? super A, ? extends Trampoline<B>> binder) {
            return new Bind<>(advance(), binder);
        }
    }

    /**
     * The existential intermediate type is captured by this generic class,
     * so reassociation requires no unchecked casts or erased continuations.
     */
    private static final class Bind<S extends @Nullable Object, B extends @Nullable Object>
            extends Trampoline<B> {

        private final Trampoline<S> source;
        private final Function<? super S, ? extends Trampoline<B>> binder;

        private Bind(Trampoline<S> source,
                     Function<? super S, ? extends Trampoline<B>> binder) {
            this.source = source;
            this.binder = binder;
        }

        @Override
        boolean complete() {
            return false;
        }

        @Override
        B completedValue() {
            throw new IllegalStateException("Bind has no completed value");
        }

        @Override
        Trampoline<B> advance() {
            return source.continueWith(binder);
        }

        @Override
        <C extends @Nullable Object> Trampoline<C> continueWith(
                Function<? super B, ? extends Trampoline<C>> outer) {
            // (source >>= binder) >>= outer
            // = source >>= (value -> binder(value) >>= outer)
            // Reassociate one node, never recursively descend source.
            return new Bind<>(source, value -> Objects.requireNonNull(
                    binder.apply(value), "flatMap returned null Trampoline")
                    .flatMap(outer));
        }
    }
}
