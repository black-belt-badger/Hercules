package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.NonNull;

/**
 * Two possible kinds of evidence, together with an explanation when neither is present.
 *
 * <p>This is the four-way extension of {@link These}: {@link This} contains only
 * left evidence, {@link That} only right evidence, {@link Both} both, and
 * {@link Neither} an explanation for total absence. Neither does not imply
 * an exception, failure, or neutral identity.
 *
 * <p>Operations are strict and preserve the active variant. Callback references
 * are checked for null even when their branch is absent. Payload nullness follows
 * JSpecify type uses; callers may opt into explicitly nullable payload types.
 *
 * <p>No generic {@code flatMap} or {@code combine} is provided: combining
 * a Neither with partial evidence can otherwise discard an explanation.
 *
 * @param <L> left evidence type
 * @param <R> right evidence type
 * @param <E> explanation type for neither kind of evidence
 */
public sealed interface Those<L, R, E>
        permits Those.This, Those.That, Those.Both, Those.Neither {

    /**
     * Handle all four variants, invoking only the handler for the present one.
     *
     * @param <T> result type
     * @param onLeft handler for the left-only variant
     * @param onRight handler for the right-only variant
     * @param onBoth handler for the both-values variant
     * @param onNeither handler for the neither variant
     * @return the result of the handler for the present variant
     */
    <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
               @NonNull Function<? super R, ? extends T> onRight,
               @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth,
               @NonNull Function<? super E, ? extends T> onNeither);

    /**
     * Construct left-only evidence.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param value value to wrap in the selected variant
     * @return a left-only value
     */
    static <L, R, E> Those<L, R, E> left(L value) {
        return new This<>(value);
    }

    /**
     * Construct right-only evidence.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param value value to wrap in the selected variant
     * @return a right-only value
     */
    static <L, R, E> Those<L, R, E> right(R value) {
        return new That<>(value);
    }

    /**
     * Construct both kinds of evidence.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param left left payload
     * @param right right payload
     * @return a value containing both payloads
     */
    static <L, R, E> Those<L, R, E> both(L left, R right) {
        return new Both<>(left, right);
    }

    /**
     * Construct typed absence; the explanation is not automatically an error.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param explanation typed explanation for the absence of both evidence types
     * @return a value representing neither evidence type
     */
    static <L, R, E> Those<L, R, E> neither(E explanation) {
        return new Neither<>(explanation);
    }

    /**
     * Transform right evidence (the right-biased mapping convention from These).
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the right payload transformed if present
     */
    default <T> Those<L, T, E> map(@NonNull Function<? super R, ? extends T> mapper) {
        return fold(
                Those::left,
                right -> Those.right(mapper.apply(right)),
                (left, right) -> Those.both(left, mapper.apply(right)),
                Those::neither);
    }

    /**
     * Transform left evidence, preserving right evidence and absence explanations.
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the left payload transformed if present
     */
    default <T> Those<T, R, E> mapLeft(@NonNull Function<? super L, ? extends T> mapper) {
        return fold(
                left -> Those.left(mapper.apply(left)),
                Those::right,
                (left, right) -> Those.both(mapper.apply(left), right),
                Those::neither);
    }

    /**
     * Transform the explanation only when neither evidence type is present.
     *
     * @param <T> result type
     * @param mapper payload mapping function; must not be null
     * @return a value with the explanation transformed if present
     */
    default <T> Those<L, R, T> mapNeither(@NonNull Function<? super E, ? extends T> mapper) {
        return fold(
                Those::left,
                Those::right,
                Those::both,
                explanation -> Those.neither(mapper.apply(explanation)));
    }

    /**
     * Transform both evidence types, leaving the absence explanation unchanged.
     *
     * @param <C> transformed left payload type
     * @param <D> transformed right payload type
     * @param leftMapper mapping function for left values; must not be null
     * @param rightMapper mapping function for right values; must not be null
     * @return a value with whichever evidence payloads are present transformed
     */
    default <C, D> Those<C, D, E> bimap(
            @NonNull Function<? super L, ? extends C> leftMapper,
            @NonNull Function<? super R, ? extends D> rightMapper) {
        return fold(
                left -> Those.left(leftMapper.apply(left)),
                right -> Those.right(rightMapper.apply(right)),
                (left, right) -> Those.both(leftMapper.apply(left), rightMapper.apply(right)),
                Those::neither);
    }

    /**
     * Transform all three payload types, running only the applicable callbacks.
     *
     * @param <C> transformed left payload type
     * @param <D> transformed right payload type
     * @param <F> transformed absence explanation type
     * @param leftMapper mapping function for left values; must not be null
     * @param rightMapper mapping function for right values; must not be null
     * @param neitherMapper mapping function for absence explanations; must not be null
     * @return a value with all applicable payloads transformed
     */
    default <C, D, F> Those<C, D, F> trimap(
            @NonNull Function<? super L, ? extends C> leftMapper,
            @NonNull Function<? super R, ? extends D> rightMapper,
            @NonNull Function<? super E, ? extends F> neitherMapper) {
        return fold(
                left -> Those.left(leftMapper.apply(left)),
                right -> Those.right(rightMapper.apply(right)),
                (left, right) -> Those.both(leftMapper.apply(left), rightMapper.apply(right)),
                explanation -> Those.neither(neitherMapper.apply(explanation)));
    }

    /**
     * Exchange the two kinds of evidence; leave the absence explanation intact.
     *
     * @return the value with left and right alternatives exchanged
     */
    default Those<R, L, E> swap() {
        return fold(
                Those::right,
                Those::left,
                (left, right) -> Those.both(right, left),
                Those::neither);
    }

    /**
     * A lossless representation as Either(explanation, These(evidence)).
     *
     * @return the equivalent nested Either and These representation
     */
    default Either<E, These<L, R>> toEither() {
        return fold(
                left -> Either.right(These.left(left)),
                right -> Either.right(These.right(right)),
                (left, right) -> Either.right(These.both(left, right)),
                Either::left);
    }

    /**
     * Reconstruct the same variant from the equivalent nested representation.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param value value to wrap in the selected variant
     * @return the matching four-way value
     */
    static <L, R, E> Those<L, R, E> fromEither(@NonNull Either<E, These<L, R>> value) {
        return value.fold(
                Those::neither,
                evidence -> Objects.requireNonNull(evidence, "evidence").fold(
                        Those::left,
                        Those::right,
                        Those::both));
    }

    /**
     * Left-only evidence.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param value value to wrap in the selected variant
     */
    record This<L, R, E>(L value) implements Those<L, R, E> {
        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth,
                          @NonNull Function<? super E, ? extends T> onNeither) {
            return onLeft.apply(value);
        }
    }

    /**
     * Right-only evidence.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param value value to wrap in the selected variant
     */
    record That<L, R, E>(R value) implements Those<L, R, E> {
        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth,
                          @NonNull Function<? super E, ? extends T> onNeither) {
            return onRight.apply(value);
        }
    }

    /**
     * Both kinds of evidence together.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param left left payload
     * @param right right payload
     */
    record Both<L, R, E>(L left, R right) implements Those<L, R, E> {
        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth,
                          @NonNull Function<? super E, ? extends T> onNeither) {
            return onBoth.apply(left, right);
        }
    }

    /**
     * Explanation of why neither kind of evidence is present.
     *
     * @param <L> left payload type
     * @param <R> right payload type
     * @param <E> typed explanation for absence
     * @param explanation typed explanation for the absence of both evidence types
     */
    record Neither<L, R, E>(E explanation) implements Those<L, R, E> {
        @Override
        public <T> T fold(@NonNull Function<? super L, ? extends T> onLeft,
                          @NonNull Function<? super R, ? extends T> onRight,
                          @NonNull BiFunction<? super L, ? super R, ? extends T> onBoth,
                          @NonNull Function<? super E, ? extends T> onNeither) {
            return onNeither.apply(explanation);
        }
    }
}
