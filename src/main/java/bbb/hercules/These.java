package bbb.hercules;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import lombok.NonNull;

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
     *
     * @param <T> result type
     * @param onLeft handler for the left-only variant
     * @param onRight handler for the right-only variant
     * @param onBoth handler for the both-values variant
     * @return the result of the handler for the present variant
     */
    <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
               @NonNull Function<? super R, ? extends T> onRight,
               @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth);

    /**
     * Construct a left-only value.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     * @return a left-only value
     */
    static <L, R> These<L, R> left(L value) {
        return new This<>(value);
    }

    /**
     * Construct a right-only value.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     * @return a right-only value
     */
    static <L, R> These<L, R> right(R value) {
        return new That<>(value);
    }

    /**
     * Construct a value containing both left and right information.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param left left payload
     * @param right right payload
     * @return a value containing both payloads
     */
    static <L, R> These<L, R> both(L left, R right) {
        return new Both<>(left, right);
    }

    /**
     * Transform the right value, including the right side of {@link Both}.
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the right payload transformed if present
     */
    default <T> These<L, T> map(@NonNull Function<? super R, ? extends T> mapper) {
        return fold(
                These::left,
                value -> These.right(mapper.apply(value)),
                (left, right) -> These.both(left, mapper.apply(right)));
    }

    /**
     * Transform the left value, including the left side of {@link Both}.
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the left payload transformed if present
     */
    default <T> These<T, R> mapLeft(@NonNull Function<? super L, ? extends T> mapper) {
        return fold(
                value -> These.left(mapper.apply(value)),
                These::right,
                (left, right) -> These.both(mapper.apply(left), right));
    }

    /**
     * Transform whichever values are present. Both callbacks are required.
     *
     * @param <A> transformed left payload type
     * @param <B> transformed right payload type
     * @param leftMapper mapping function for left values; must not be null
     * @param rightMapper mapping function for right values; must not be null
     * @return a value with whichever evidence payloads are present transformed
     */
    default <A, B> These<A, B> bimap(
            @NonNull Function<? super L, ? extends A> leftMapper,
            @NonNull Function<? super R, ? extends B> rightMapper) {
        return fold(
                left -> These.left(leftMapper.apply(left)),
                right -> These.right(rightMapper.apply(right)),
                (left, right) -> These.both(leftMapper.apply(left), rightMapper.apply(right)));
    }

    /**
     * Exchange left and right; applying twice restores the original value.
     *
     * @return the value with left and right alternatives exchanged
     */
    default These<R, L> swap() {
        return fold(These::right, These::left, (left, right) -> These.both(right, left));
    }

    /**
     * Embed this inclusive choice in Those without losing evidence.
     * None of the three variants becomes {@link Those.Neither};
     * the type parameter E is for a potential later absence explanation.
     *
     * @param <E> explanation type of the target Those; no explanation is created
     * @return the corresponding This, That or Both variant
     */
    default <E> Those<L, R, E> toThose() {
        return fold(Those::left, Those::right, Those::both);
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
     *
     * @param <T> result type
     * @param binder right-biased dependent function; must not be null
     * @param combineLeft associative left-value combiner; must not be null
     * @return the result of dependent right-biased computation
     */
    default <T> These<L, T> flatMap(
            @NonNull Function<? super R, ? extends These<L, T>> binder,
            @NonNull BinaryOperator<L> combineLeft) {
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

    /**
     * Left-only alternative (traditional `This`).
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     */
    record This<L, R>(L value) implements These<L, R> {

        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth) {
            return onLeft.apply(value);
        }
    }

    /**
     * Right-only alternative (traditional `That`).
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param value value to wrap in the selected variant
     */
    record That<L, R>(R value) implements These<L, R> {

        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth) {
            return onRight.apply(value);
        }
    }

    /**
     * Inclusive alternative holding both left and right values.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param left left payload
     * @param right right payload
     */
    record Both<L, R>(L left, R right) implements These<L, R> {

        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth) {
            return onBoth.apply(left, right);
        }
    }
}
