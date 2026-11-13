package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

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
 * @param <A> left evidence type
 * @param <B> right evidence type
 * @param <E> explanation type for neither kind of evidence
 */
public sealed interface Those<A, B, E>
        permits Those.This, Those.That, Those.Both, Those.Neither {

    /** Handle all four variants, invoking only the handler for the present one. */
    <T> T fold(Function<? super A, ? extends T> onLeft,
               Function<? super B, ? extends T> onRight,
               BiFunction<? super A, ? super B, ? extends T> onBoth,
               Function<? super E, ? extends T> onNeither);

    /** Construct left-only evidence. */
    static <A, B, E> Those<A, B, E> left(A value) {
        return new This<>(value);
    }

    /** Construct right-only evidence. */
    static <A, B, E> Those<A, B, E> right(B value) {
        return new That<>(value);
    }

    /** Construct both kinds of evidence. */
    static <A, B, E> Those<A, B, E> both(A left, B right) {
        return new Both<>(left, right);
    }

    /** Construct typed absence; the explanation is not automatically an error. */
    static <A, B, E> Those<A, B, E> neither(E explanation) {
        return new Neither<>(explanation);
    }

    /** Transform right evidence (the right-biased mapping convention from These). */
    default <T> Those<A, T, E> map(Function<? super B, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                Those::left,
                right -> Those.right(mapper.apply(right)),
                (left, right) -> Those.both(left, mapper.apply(right)),
                Those::neither);
    }

    /** Transform left evidence, preserving right evidence and absence explanations. */
    default <T> Those<T, B, E> mapLeft(Function<? super A, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                left -> Those.left(mapper.apply(left)),
                Those::right,
                (left, right) -> Those.both(mapper.apply(left), right),
                Those::neither);
    }

    /** Transform the explanation only when neither evidence type is present. */
    default <T> Those<A, B, T> mapNeither(Function<? super E, ? extends T> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return fold(
                Those::left,
                Those::right,
                Those::both,
                explanation -> Those.neither(mapper.apply(explanation)));
    }

    /** Transform both evidence types, leaving the absence explanation unchanged. */
    default <C, D> Those<C, D, E> bimap(
            Function<? super A, ? extends C> leftMapper,
            Function<? super B, ? extends D> rightMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        Objects.requireNonNull(rightMapper, "rightMapper");
        return fold(
                left -> Those.left(leftMapper.apply(left)),
                right -> Those.right(rightMapper.apply(right)),
                (left, right) -> Those.both(leftMapper.apply(left), rightMapper.apply(right)),
                Those::neither);
    }

    /** Transform all three payload types, running only the applicable callbacks. */
    default <C, D, F> Those<C, D, F> trimap(
            Function<? super A, ? extends C> leftMapper,
            Function<? super B, ? extends D> rightMapper,
            Function<? super E, ? extends F> neitherMapper) {
        Objects.requireNonNull(leftMapper, "leftMapper");
        Objects.requireNonNull(rightMapper, "rightMapper");
        Objects.requireNonNull(neitherMapper, "neitherMapper");
        return fold(
                left -> Those.left(leftMapper.apply(left)),
                right -> Those.right(rightMapper.apply(right)),
                (left, right) -> Those.both(leftMapper.apply(left), rightMapper.apply(right)),
                explanation -> Those.neither(neitherMapper.apply(explanation)));
    }

    /** Exchange the two kinds of evidence; leave the absence explanation intact. */
    default Those<B, A, E> swap() {
        return fold(
                Those::right,
                Those::left,
                (left, right) -> Those.both(right, left),
                Those::neither);
    }

    /** A lossless representation as Either(explanation, These(evidence)). */
    default Either<E, These<A, B>> toEither() {
        return fold(
                left -> Either.right(These.left(left)),
                right -> Either.right(These.right(right)),
                (left, right) -> Either.right(These.both(left, right)),
                Either::left);
    }

    /** Reconstruct the same variant from the equivalent nested representation. */
    static <A, B, E> Those<A, B, E> fromEither(Either<E, These<A, B>> value) {
        Objects.requireNonNull(value, "value");
        return value.fold(
                Those::neither,
                evidence -> Objects.requireNonNull(evidence, "evidence").fold(
                        Those::left,
                        Those::right,
                        Those::both));
    }

    /** Left-only evidence. */
    record This<A, B, E>(A value) implements Those<A, B, E> {
        @Override
        public <T> T fold(Function<? super A, ? extends T> onLeft,
                          Function<? super B, ? extends T> onRight,
                          BiFunction<? super A, ? super B, ? extends T> onBoth,
                          Function<? super E, ? extends T> onNeither) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            Objects.requireNonNull(onNeither, "onNeither");
            return onLeft.apply(value);
        }
    }

    /** Right-only evidence. */
    record That<A, B, E>(B value) implements Those<A, B, E> {
        @Override
        public <T> T fold(Function<? super A, ? extends T> onLeft,
                          Function<? super B, ? extends T> onRight,
                          BiFunction<? super A, ? super B, ? extends T> onBoth,
                          Function<? super E, ? extends T> onNeither) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            Objects.requireNonNull(onNeither, "onNeither");
            return onRight.apply(value);
        }
    }

    /** Both kinds of evidence together. */
    record Both<A, B, E>(A left, B right) implements Those<A, B, E> {
        @Override
        public <T> T fold(Function<? super A, ? extends T> onLeft,
                          Function<? super B, ? extends T> onRight,
                          BiFunction<? super A, ? super B, ? extends T> onBoth,
                          Function<? super E, ? extends T> onNeither) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            Objects.requireNonNull(onNeither, "onNeither");
            return onBoth.apply(left, right);
        }
    }

    /** Explanation of why neither kind of evidence is present. */
    record Neither<A, B, E>(E explanation) implements Those<A, B, E> {
        @Override
        public <T> T fold(Function<? super A, ? extends T> onLeft,
                          Function<? super B, ? extends T> onRight,
                          BiFunction<? super A, ? super B, ? extends T> onBoth,
                          Function<? super E, ? extends T> onNeither) {
            Objects.requireNonNull(onLeft, "onLeft");
            Objects.requireNonNull(onRight, "onRight");
            Objects.requireNonNull(onBoth, "onBoth");
            Objects.requireNonNull(onNeither, "onNeither");
            return onNeither.apply(explanation);
        }
    }
}
