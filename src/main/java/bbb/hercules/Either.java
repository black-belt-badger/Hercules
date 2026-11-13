package bbb.hercules;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import lombok.NonNull;

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
     *
     * @param <T> result type
     * @param onLeft handler for the left-only variant
     * @param onRight handler for the right-only variant
     * @return the result of the handler for the present variant
     */
    <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
               @NonNull Function<? super R, ? extends T> onRight);

    /**
     * Construct a left value.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     * @return a left-only value
     */
    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    /**
     * Construct a right value.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     * @return a right-only value
     */
    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    /**
     * Transform only the right value (right-biased functor mapping).
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the right payload transformed if present
     */
    default <T> Either<L, T> map(@NonNull Function<? super R, ? extends T> mapper) {
        return fold(
                left -> Either.<L, T>left(left),
                right -> Either.<L, T>right(mapper.apply(right)));
    }

    /**
     * Transform only the left value.
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the left payload transformed if present
     */
    default <T> Either<T, R> mapLeft(@NonNull Function<? super L, ? extends T> mapper) {
        return fold(
                left -> Either.<T, R>left(mapper.apply(left)),
                right -> Either.<T, R>right(right));
    }

    /**
     * Transform both sides, invoking only the callback for the present variant.
     *
     * @param <A> transformed left payload type
     * @param <B> transformed right payload type
     * @param leftMapper mapping function for left values; must not be null
     * @param rightMapper mapping function for right values; must not be null
     * @return a value with whichever evidence payloads are present transformed
     */
    default <A, B> Either<A, B> bimap(@NonNull Function<? super L, ? extends A> leftMapper,
                                     @NonNull Function<? super R, ? extends B> rightMapper) {
        return fold(
                left -> Either.<A, B>left(leftMapper.apply(left)),
                right -> Either.<A, B>right(rightMapper.apply(right)));
    }

    /**
     * Chain a computation on the right; propagate left values unchanged.
     * A callback that returns a null Either is rejected on evaluation.
     *
     * @param <T> result type
     * @param binder right-biased dependent function; must not be null
     * @return the result of dependent right-biased computation
     */
    default <T> Either<L, T> flatMap(@NonNull Function<? super R, ? extends Either<L, T>> binder) {
        return fold(
                left -> Either.<L, T>left(left),
                right -> Objects.requireNonNull(binder.apply(right), "flatMap returned null"));
    }

    /**
     * Reverse the two alternatives. Applying twice restores the original value.
     *
     * @return the value with left and right alternatives exchanged
     */
    default Either<R, L> swap() {
        return fold(Either::right, Either::left);
    }

    /**
     * Embed either alternative in the inclusive-or type without losing information.
     * Left becomes {@link These.This}; Right becomes {@link These.That}.
     * This conversion does not manufacture a {@link These.Both} value.
     *
     * @return the corresponding left-only or right-only These value
     */
    default These<L, R> toThese() {
        return fold(These::left, These::right);
    }

    /**
     * Return the right value, or the supplied fallback if left.
     *
     * @param fallback replacement value for a left variant
     * @return the right value if present, otherwise the fallback
     */
    default R getOrElse(R fallback) {
        return fold(left -> fallback, Function.identity());
    }

    /**
     * Convert a left value into a right value by applying a recovery function.
     *
     * @param recovery function converting a left value to a right value
     * @return the original right value or the recovered right value
     */
    default Either<L, R> recover(@NonNull Function<? super L, ? extends R> recovery) {
        return fold(
                left -> Either.<L, R>right(recovery.apply(left)),
                right -> Either.<L, R>right(right));
    }

    /**
     * Keep the right value if present, discarding the left value.
     * A nullable Right payload also becomes Optional.empty().
     *
     * @return an Optional containing the right value when non-null
     */
    default Optional<R> toOptional() {
        return fold(left -> Optional.empty(), Optional::ofNullable);
    }

    /**
     * The left alternative. Its payload is not implicitly an error.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     */
    record Left<L, R>(L value) implements Either<L, R> {

        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight) {
            return onLeft.apply(value);
        }
    }

    /**
     * The right alternative.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     */
    record Right<L, R>(R value) implements Either<L, R> {

        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight) {
            return onRight.apply(value);
        }
    }
}
