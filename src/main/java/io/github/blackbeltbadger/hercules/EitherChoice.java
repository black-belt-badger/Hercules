package io.github.blackbeltbadger.hercules;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

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
public record EitherChoice<L>(L emptyLeft, BinaryOperator<L> combineLeft) {

    public EitherChoice {
        Objects.requireNonNull(combineLeft, "combineLeft");
    }

    /** Return the identity for choice. */
    public <R> Either<L, R> empty() {
        return Either.left(emptyLeft);
    }

    /**
     * Choose the first right value, or the second right value if the first is
     * left. When both are left, combine the left payloads in encounter order.
     * Both arguments are already evaluated by the caller.
     */
    public <R> Either<L, R> choose(Either<L, R> first, Either<L, R> second) {
        Objects.requireNonNull(second, "second");
        return choose(first, () -> second);
    }

    /**
     * As {@link #choose(Either, Either)} with a lazy second alternative.
     * The supplier is validated eagerly but invoked only if the first is
     * left. A null result from the supplier is rejected when evaluated.
     */
    public <R> Either<L, R> choose(
            Either<L, R> first, Supplier<? extends Either<L, R>> second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return first.fold(
                left -> Objects.requireNonNull(second.get(), "choice returned null").fold(
                        nextLeft -> Either.left(combineLeft.apply(left, nextLeft)),
                        Either::right),
                right -> first);
    }
}
