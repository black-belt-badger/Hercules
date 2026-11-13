package bbb.hercules;

import java.util.Objects;
import java.util.function.Function;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A pure environment-dependent computation, represented by {@code R -> A}.
 *
 * <p>A Reader is a JDK-compatible, single-method functional interface.
 * Calling {@link #run(Object)} evaluates it with an explicit environment.
 * Mapping, dependent binding and local environment transformations are
 * <em>lazy</em>: they construct new Readers without running any user code.
 *
 * <p>A dependent binder receives the value computed by the first Reader;
 * the Reader it returns is evaluated with the <em>same</em> environment.
 * Local transformations affect only the selected Reader's evaluation, not
 * the caller's original environment object or unrelated computations.
 *
 * <p>Neither this interface nor Java's {@link Function} type system can
 * guarantee that user lambdas are pure. Reader's algebraic laws require
 * deterministic and side-effect-free callbacks. Java's nested lambda
 * composition is also <em>not stack-safe</em> for arbitrary depth:
 * hundreds of thousands of nested {@code flatMap} operations may exhaust
 * the JVM stack when finally run. See {@code docs/reader.md} and the
 * independently scoped Trampoline proposal (#4).
 *
 * <p>Required callback references are checked eagerly with Lombok
 * {@code @NonNull}. A null Reader returned by the binder is rejected
 * only when the binder actually runs; exceptions propagate unchanged.
 * Environments and values can be explicitly nullable under JSpecify.
 *
 * @param <R> shared environment type
 * @param <A> computed value type
 */
@FunctionalInterface
public interface Reader<R extends @Nullable Object, A extends @Nullable Object> {

    /**
     * Evaluate this computation with an explicitly supplied environment.
     *
     * @param environment the computation's environment
     * @return computed value
     */
    A run(R environment);

    /**
     * Transform the value yielded by this Reader, without evaluating it now.
     *
     * @param <B> mapped value type
     * @param mapper required value mapping function
     * @return a new lazy Reader with the same environment type
     */
    default <B extends @Nullable Object> Reader<R, B> map(
            @NonNull Function<? super A, ? extends B> mapper) {
        return environment -> mapper.apply(run(environment));
    }

    /**
     * Compose a dependent Reader, using the same environment for both steps.
     *
     * <p>When the resulting Reader is run, this Reader evaluates first.
     * Its value is passed to {@code binder} exactly once, and the returned
     * Reader evaluates using that exact same environment. Both callback and
     * result exceptions propagate; a structural null Reader result is not
     * permitted.
     *
     * @param <B> next computed value type
     * @param binder transforms this value into another Reader
     * @return lazily composed dependent computation
     */
    default <B extends @Nullable Object> Reader<R, B> flatMap(
            @NonNull Function<? super A, ? extends Reader<R, B>> binder) {
        return environment -> Objects.requireNonNull(
                binder.apply(run(environment)), "flatMap returned null Reader")
                .run(environment);
    }

    /**
     * Evaluate this Reader under a transformed local environment.
     *
     * <p>The transformation happens once at evaluation time, before this
     * Reader is run. It cannot mutate the outer environment through
     * Hercules; callers must supply immutable data and pure transformations
     * to preserve that guarantee. No environment is modified in-place.
     *
     * @param transform environment transformation to apply locally
     * @return a Reader which evaluates under the transformed environment
     */
    default Reader<R, A> local(
            @NonNull Function<? super R, ? extends R> transform) {
        return environment -> run(transform.apply(environment));
    }

    /**
     * Read the current environment as the computed value.
     *
     * @param <R> environment type
     * @return Reader that returns its environment unchanged
     */
    static <R extends @Nullable Object> Reader<R, R> ask() {
        return environment -> environment;
    }

    /**
     * Project a value from the environment using a lazy selector.
     *
     * @param <R> environment type
     * @param <A> projected value type
     * @param selector function applied when the Reader is evaluated
     * @return Reader projecting the environment
     */
    static <R extends @Nullable Object, A extends @Nullable Object> Reader<R, A> asks(
            @NonNull Function<? super R, ? extends A> selector) {
        return selector::apply;
    }

    /**
     * Return a fixed value regardless of the supplied environment.
     *
     * <p>The value is stored by reference, not cloned. Pure computation
     * requires the value to behave as immutable data.
     *
     * @param <R> environment type
     * @param <A> value type
     * @param value value to return
     * @return a Reader that ignores the environment
     */
    static <R extends @Nullable Object, A extends @Nullable Object> Reader<R, A> pure(
            A value) {
        return ignored -> value;
    }
}
