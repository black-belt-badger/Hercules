package bbb.hercules;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An immutable description of a state-dependent computation {@code S -> (S,A)}.
 *
 * <p>The input state is supplied explicitly to {@link #run(Object)}.
 * The result contains a <em>new state</em> and a computed value, in that
 * order. {@link #flatMap(Function)} runs its dependent computation with
 * the state produced by the first computation, never the original state.
 * {@link #map(Function)} changes only the value, preserving the new state.
 *
 * <p>Construction and composition are lazy. No operation owns a mutable
 * state cell, modifies an input, or performs I/O. For referential
 * transparency, callers must supply immutable state objects and
 * deterministic, side-effect-free functions. Java cannot enforce this.
 * In particular, a user function that mutates its state object breaks
 * isolation even though this interface never mutates one itself.
 *
 * <p>Like {@link Reader}, nested Java-lambda composition is not stack-safe
 * at arbitrary depths. Construction of deep chains is lazy, but evaluating
 * 100,000 nested binds can overflow the JVM stack. See docs/state.md and
 * the dedicated Trampoline proposal (#4); no hidden interpreter or
 * unchecked casts are introduced.
 *
 * <p>Non-null structural callbacks are validated eagerly via Lombok.
 * A null {@link Result} or dependent State returned by user code is
 * rejected when evaluated. The state and value payload types may be
 * explicitly nullable using JSpecify.
 *
 * @param <S> state type, normally an immutable record
 * @param <A> computed value type
 */
@FunctionalInterface
public interface State<S extends @Nullable Object, A extends @Nullable Object> {

    /**
     * A single completed state transition: next state first, computed value second.
     *
     * <p>Shallow immutability does not clone state/value payload objects.
     *
     * @param <S> state type
     * @param <A> computed value type
     * @param state the resulting state
     * @param value the computed value
     */
    record Result<S extends @Nullable Object, A extends @Nullable Object>(S state, A value) {
    }

    /**
     * Explicit non-null value indicating that a transition has no interesting
     * value, such as {@link #put(Object)} or {@link #modify(UnaryOperator)}.
     *
     * <p>This avoids using an implicit null as a Unit result. State still
     * supports intentionally nullable value payloads via type-use annotations.
     */
    enum Unit {
        /** The sole inhabitant of Unit. */
        INSTANCE
    }

    /**
     * Evaluate a State computation from the supplied starting state.
     *
     * @param initial explicit starting state
     * @return next state paired with computed value; never a null container
     */
    Result<S, A> run(S initial);

    /**
     * Evaluate and extract only the computed value.
     *
     * @param initial starting state
     * @return computed value
     */
    default A eval(S initial) {
        return Objects.requireNonNull(run(initial), "State returned null Result").value();
    }

    /**
     * Evaluate and extract only the next state.
     *
     * @param initial starting state
     * @return resulting state
     */
    default S exec(S initial) {
        return Objects.requireNonNull(run(initial), "State returned null Result").state();
    }

    /**
     * Lazily transform the value without changing the resulting state.
     *
     * @param <B> mapped value type
     * @param mapper required value transformation
     * @return a lazily mapped State
     */
    default <B extends @Nullable Object> State<S, B> map(
            @NonNull Function<? super A, ? extends B> mapper) {
        return initial -> {
            Result<S, A> previous = Objects.requireNonNull(
                    run(initial), "State returned null Result");
            return new Result<>(previous.state(), mapper.apply(previous.value()));
        };
    }

    /**
     * Lazily compose a dependent computation, threading state left to right.
     *
     * <p>When evaluated: run this computation once, call the binder once
     * with the computed value, then run the returned State exactly once
     * using the <em>resulting</em> state from the first step. The final
     * state and value come from that second computation. A null resulting
     * State or Result is rejected at evaluation; callback exceptions
     * propagate without being converted to a domain-level error.
     *
     * @param <B> dependent value type
     * @param binder required dependent State builder
     * @return a lazily composed State
     */
    default <B extends @Nullable Object> State<S, B> flatMap(
            @NonNull Function<? super A, ? extends State<S, B>> binder) {
        return initial -> {
            Result<S, A> previous = Objects.requireNonNull(
                    run(initial), "State returned null Result");
            State<S, B> next = Objects.requireNonNull(
                    binder.apply(previous.value()), "flatMap returned null State");
            return Objects.requireNonNull(next.run(previous.state()),
                    "flatMap returned a State producing null Result");
        };
    }

    /**
     * Read the current state without changing it.
     *
     * @param <S> state type
     * @return a State returning the original state both as state and value
     */
    static <S extends @Nullable Object> State<S, S> get() {
        return state -> new Result<>(state, state);
    }

    /**
     * Replace the current state and return a typed Unit value.
     *
     * @param <S> state type
     * @param replacement new state, allowed to be null only for an explicitly
     *                    nullable state type
     * @return a State setting the replacement state when evaluated
     */
    static <S extends @Nullable Object> State<S, Unit> put(S replacement) {
        return ignored -> new Result<>(replacement, Unit.INSTANCE);
    }

    /**
     * Transform the state once when the returned computation is evaluated.
     *
     * @param <S> state type
     * @param updater required state transformation
     * @return a State with the updated state and typed Unit value
     */
    static <S extends @Nullable Object> State<S, Unit> modify(
            @NonNull UnaryOperator<S> updater) {
        return state -> new Result<>(updater.apply(state), Unit.INSTANCE);
    }

    /**
     * Read a projection of the state while leaving state unchanged.
     *
     * @param <S> state type
     * @param <A> projected value type
     * @param selector required state projection
     * @return a State carrying the original state and projected value
     */
    static <S extends @Nullable Object, A extends @Nullable Object> State<S, A> gets(
            @NonNull Function<? super S, ? extends A> selector) {
        return state -> new Result<>(state, selector.apply(state));
    }

    /**
     * Lift a constant value into a State computation that preserves input state.
     *
     * @param <S> state type
     * @param <A> value type
     * @param value constant value
     * @return State that returns supplied state unchanged and constant value
     */
    static <S extends @Nullable Object, A extends @Nullable Object> State<S, A> pure(A value) {
        return state -> new Result<>(state, value);
    }
}
