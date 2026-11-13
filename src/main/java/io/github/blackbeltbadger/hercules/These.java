package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * Inclusive choice: only a left value ({@link This}), only a right value
 * ({@link That}), or both ({@link Both}).
 *
 * <p>Right-biased {@code map} and {@code flatMap} also preserve left-hand
 * information when the variant contains both values. There is deliberately
 * no empty variant: {@code These} always carries at least one value.
 *
 * <p>Operations are strict. Callback objects are validated before evaluation,
 * and exceptions thrown by callbacks propagate unchanged. JSpecify describes
 * nullness at type use sites; explicit nullable payloads remain possible.
 *
 * @param <L> the left-hand value type, often diagnostics or warnings
 * @param <R> the right-hand value type, often a computed result
 */
public sealed interface These<L, R> permits These.This, These.That, These.Both {

    /**
     * Eliminate every variant. All three handlers must be non-null; only
     * the handler for the present variant is invoked.
     */
    <T> T fold(Function<? super L, ? extends T> onLeft,
               Function<? super R, ? extends T> onRight,
               BiFunction<? super L, ? super R, ? extends T> onBoth);

    /** Construct a left-only value. */
    static <L, R> These<L, R> left(L value) {
        return new This<>(value);
    }

    /** Construct a right-only value. */
    static <L, R> These<L, R> right(R value) {
        return new That<>(value);
    }

    /** Construct a value containing both left and right information. */
    static <L, R> These<L, R> both(L left, R right) {
        return new Both<>(left, right);
    }

    /** Transform the right value, including the right side of {@link Both}. */
    default <T> These<L, T> map(Function<? super R, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                These::left,
                value -> These.right(mapper.apply(value)),
                (left, right) -> These.both(left, mapper.apply(right)));
    }

    /** Transform the left value, including the left side of {@link Both}. */
    default <T> These<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                value -> These.left(mapper.apply(value)),
                These::right,
                (left, right) -> These.both(mapper.apply(left), right));
    }

    /** Transform whichever values are present. Both callbacks are required. */
    default <A, B> These<A, B> bimap(
            Function<? super L, ? extends A> leftMapper,
            Function<? super R, ? extends B> rightMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        Objects.requireNonNull(rightMapper, "rightMapper");
        return fold(
                left -> These.left(leftMapper.apply(left)),
                right -> These.right(rightMapper.apply(right)),
                (left, right) -> These.both(leftMapper.apply(left), rightMapper.apply(right)));
    }

    /** Exchange left and right; applying twice restores the original value. */
    default These<R, L> swap() {
        return fold(These::right, These::left, (left, right) -> These.both(right, left));
    }

    /**
     * Right-biased, diagnostic-preserving bind using an explicit left combiner.
     *
     * <p>On {@link This}, do not evaluate the binder. On {@link That}, use
     * the binder's result unchanged. On {@link Both}, evaluate the binder
     * and preserve the existing left value: combine it with a new left
     * value (in that order) when the result contains one.
     *
     * <p>Monad laws, especially associativity, require an associative
     * `combineLeft` function. No associative law is asserted for arbitrary
     * Java `BinaryOperator` implementations.
     */
    default <T> These<L, T> flatMap(
            Function<? super R, ? extends These<L, T>> binder,
            BinaryOperator<L> combineLeft) {
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(combineLeft, "combineLeft");

        return fold(
                These::left,
                right -> Objects.requireNonNull(binder.apply(right), "flatMap returned null"),
                (left, right) -> {
                    These<L, T> next = Objects.requireNonNull(
                            binder.apply(right), "flatMap returned null");
                    return next.fold(
                            newLeft -> These.left(combineLeft.apply(left, newLeft)),
                            newRight -> These.both(left, newRight),
                            (newLeft, newRight) ->
                                    These.both(combineLeft.apply(left, newLeft), newRight));
                });
    }

    /** Left-only alternative (traditional `This`). */
    record This<L, R>(L value) implements These<L, R> {

        @Override
        public <T> T fold(Function<? super L, ? extends T> onLeft,
                          Function<? super R, ? extends T> onRight,
                          BiFunction<? super L, ? super R, ? extends T> onBoth) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            return onLeft.apply(value);
        }
    }

    /** Right-only alternative (traditional `That`). */
    record That<L, R>(R value) implements These<L, R> {

        @Override
        public <T> T fold(Function<? super L, ? extends T> onLeft,
                          Function<? super R, ? extends T> onRight,
                          BiFunction<? super L, ? super R, ? extends T> onBoth) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            return onRight.apply(value);
        }
    }

    /** Inclusive alternative holding both left and right values. */
    record Both<L, R>(L left, R right) implements These<L, R> {

        @Override
        public <T> T fold(Function<? super L, ? extends T> onLeft,
                          Function<? super R, ? extends T> onRight,
                          BiFunction<? super L, ? super R, ? extends T> onBoth) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            return onBoth.apply(left, right);
        }
    }
}
