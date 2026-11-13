package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

import lombok.NonNull;

/**
 * An explicit choice policy for {@link Either}: prefer the first successful
 * (right) result and accumulate left values when both alternatives fail.
 *
 * <p>The caller supplies the left-side identity and its associative combiner.
 * When these form a monoid, {@link #empty()} and {@link #choose(Either, Either)}
 * satisfy the left/right identity and associativity laws. Arbitrary Java
 * binary operators do not automatically satisfy those laws.
 *
 * <p>This record defines a concrete choice operation, not a Java emulation
 * of Haskell's higher-kinded {@code Alternative} typeclass. Successful
 * alternatives intentionally discard failed-alternative diagnostics.
 * Use a richer result when preserving previous diagnostics matters.
 *
 * @param emptyLeft left-side identity used as the empty choice
 * @param combineLeft associative operation for two left values, in encounter order
 * @param <L> left-hand information carried by unsuccessful alternatives
 */
public record EitherChoice<L>(L emptyLeft, @NonNull BinaryOperator<L> combineLeft) {

    /**
     * Return the identity for choice.
     *
     * @param <R> right payload type
     * @return the left-only identity of this choice policy
     */
    public <R> Either<L, R> empty() {
        return Either.left(emptyLeft);
    }

    /**
     * Choose the first right value, or the second right value if the first is
     * left. When both are left, combine the left payloads in encounter order.
     * Both arguments are already evaluated by the caller.
     *
     * @param <R> right payload type
     * @param first first alternative, whose right value takes precedence
     * @param second second alternative or lazy fallback
     * @return the first successful alternative, or combined left values
     */
    public <R> Either<L, R> choose(@NonNull Either<L, R> first, @NonNull Either<L, R> second) {
        return choose(first, () -> second);
    }

    /**
     * As {@link #choose(Either, Either)} with a lazy second alternative.
     * The supplier is validated eagerly but invoked only if the first is
     * left. A null result from the supplier is rejected when evaluated.
     *
     * @param <R> right payload type
     * @param first first alternative, whose right value takes precedence
     * @param second second alternative or lazy fallback
     * @return the first successful alternative, or combined left values
     */
    public <R> Either<L, R> choose(
            @NonNull Either<L, R> first, @NonNull Supplier<? extends Either<L, R>> second) {
        return first.fold(
                left -> Objects.requireNonNull(second.get(), "choice returned null").fold(
                        nextLeft -> Either.left(combineLeft.apply(left, nextLeft)),
                        Either::right),
                right -> first);
    }
}
