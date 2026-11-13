package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * An exclusive choice between a left value and a right value.
 *
 * <p>The right side is conventionally the success branch for {@link #map}
 * and {@link #flatMap}, but a left value is not necessarily an exception or
 * even an error. Both variants are ordinary immutable data.
 *
 * <p>Unlike the lazy F3-F10 adapters, transformations here are strict: the
 * appropriate callback runs when the operation is invoked. User exceptions
 * propagate unchanged. Explicitly nullable type arguments remain supported;
 * the type does not impose unconditional runtime null checks on payloads.
 *
 * @param <L> left-hand value type
 * @param <R> right-hand value type
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

    /**
     * Eliminate the choice by handling both variants.
     * Both handlers are required, even though only one is invoked.
     */
    <T> T fold(Function<? super L, ? extends T> onLeft,
               Function<? super R, ? extends T> onRight);

    /** Construct a left value. */
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    /** Construct a right value. */
    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    /** Transform only the right value (right-biased functor mapping). */
    default <T> Either<L, T> map(Function<? super R, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                left -> Either.<L, T>left(left),
                right -> Either.<L, T>right(mapper.apply(right)));
    }

    /** Transform only the left value. */
    default <T> Either<T, R> mapLeft(Function<? super L, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                left -> Either.<T, R>left(mapper.apply(left)),
                right -> Either.<T, R>right(right));
    }

    /** Transform both sides, invoking only the callback for the present variant. */
    default <A, B> Either<A, B> bimap(Function<? super L, ? extends A> leftMapper,
                                     Function<? super R, ? extends B> rightMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        Objects.requireNonNull(rightMapper, "rightMapper");
        return fold(
                left -> Either.<A, B>left(leftMapper.apply(left)),
                right -> Either.<A, B>right(rightMapper.apply(right)));
    }

    /**
     * Chain a computation on the right; propagate left values unchanged.
     * A callback that returns a null Either is rejected on evaluation.
     */
    default <T> Either<L, T> flatMap(Function<? super R, ? extends Either<L, T>> binder) {
        Objects.requireNonNull(binder, "binder");
        return fold(
                left -> Either.<L, T>left(left),
                right -> Objects.requireNonNull(binder.apply(right), "flatMap returned null"));
    }

    /** Reverse the two alternatives. Applying twice restores the original value. */
    default Either<R, L> swap() {
        return fold(Either::right, Either::left);
    }

    /** Return the right value, or the supplied fallback if left. */
    default R getOrElse(R fallback) {
        return fold(left -> fallback, Function.identity());
    }

    /** Convert a left value into a right value by applying a recovery function. */
    default Either<L, R> recover(Function<? super L, ? extends R> recovery) {
        Objects.requireNonNull(recovery, "recovery");
        return fold(
                left -> Either.<L, R>right(recovery.apply(left)),
                right -> Either.<L, R>right(right));
    }

    /**
     * Keep the right value if present, discarding the left value.
     * A nullable Right payload also becomes Optional.empty().
     */
    default Optional<R> toOptional() {
        return fold(left -> Optional.empty(), Optional::ofNullable);
    }

    /** The left alternative. Its payload is not implicitly an error. */
    record Left<L, R>(L value) implements Either<L, R> {

        @Override
        public <T> T fold(Function<? super L, ? extends T> onLeft,
                          Function<? super R, ? extends T> onRight) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            return onLeft.apply(value);
        }
    }

    /** The right alternative. */
    record Right<L, R>(R value) implements Either<L, R> {

        @Override
        public <T> T fold(Function<? super L, ? extends T> onLeft,
                          Function<? super R, ? extends T> onRight) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            return onRight.apply(value);
        }
    }
}
