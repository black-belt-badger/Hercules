package bbb.hercules;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BinaryOperator;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Explicit, provenance-aware aggregation of {@link Those} evidence.
 *
 * <p>The four-case {@code Those<L,R,E>} cannot represent a {@code Neither(E)}
 * explanation alongside any later evidence. Instead, this policy aggregates
 * in the richer, seven-case representation {@code These<E, These<L,R>>}:
 * the outer left channel records absence explanations, and its right
 * channel records whichever evidence kinds are present. Both channels may
 * be present simultaneously; the nested {@code These<L,R>} retains
 * left-only, right-only or both evidence kinds.
 *
 * <p>Each supplied combiner must be associative. Combining two nonempty
 * aggregates is then associative and order-sensitive; neither an identity
 * nor commutativity nor idempotence is assumed. The combiner's own semantics
 * determine how much individual payload detail survives. In particular,
 * use list concatenation when every timeout reason must remain recoverable.
 *
 * <p>All methods are synchronous and strict. There is deliberately no
 * implicit {@code Those -> Those} aggregation or dependent {@code flatMap},
 * because either could silently drop typed explanations.
 *
 * @param <L> left evidence payload type
 * @param <R> right evidence payload type
 * @param <E> absence explanation/provenance type
 * @param combineLeft associative combination of two left payloads
 * @param combineRight associative combination of two right payloads
 * @param combineExplanation associative combination of two explanations
 */
public record ThoseAggregation<
        L extends @Nullable Object,
        R extends @Nullable Object,
        E extends @Nullable Object>(
        @NonNull BinaryOperator<L> combineLeft,
        @NonNull BinaryOperator<R> combineRight,
        @NonNull BinaryOperator<E> combineExplanation) {

    /**
     * Embed one four-case value into a richer aggregate without loss.
     *
     * <p>The explanation is present only when the original value is Neither.
     * Later combinations can retain that explanation alongside new evidence.
     *
     * @param value the source to lift, not null
     * @return an outer These holding either the explanation or nested evidence
     */
    public These<E, These<L, R>> lift(@NonNull Those<L, R, E> value) {
        return value.toEither().fold(These::left, These::right);
    }

    /**
     * Combine two four-case values without losing the explanation channel.
     *
     * @param first first input
     * @param second second input
     * @return the richer result, potentially containing both E and evidence
     */
    public These<E, These<L, R>> combine(
            @NonNull Those<L, R, E> first,
            @NonNull Those<L, R, E> second) {
        return combine(lift(first), lift(second));
    }

    /**
     * Combine two already-aggregated values, retaining all present channels.
     *
     * <p>The result stays in this richer representation so aggregation is
     * closed and can be regrouped. Its associativity follows from the
     * associativity of all three supplied combiners, and assumes they do
     * not mutate their arguments.
     *
     * @param first earlier aggregate in encounter order
     * @param second later aggregate in encounter order
     * @return a provenance-aware aggregate
     */
    public These<E, These<L, R>> combine(
            @NonNull These<E, These<L, R>> first,
            @NonNull These<E, These<L, R>> second) {
        return mergeThese(requireEvidence(first), requireEvidence(second),
                combineExplanation,
                (left, right) -> mergeThese(left, right, combineLeft, combineRight));
    }

    /**
     * Aggregate a finite, nonempty iterable of four-case values.
     *
     * <p>No identity is invented for an empty iterable: it is rejected.
     * Inputs are visited exactly once in encounter order, including after
     * a Neither; no part of a Neither explanation is dropped implicitly.
     * Null input objects fail when reached; iteration and combiner
     * exceptions propagate without conversion.
     *
     * @param values nonempty finite sequence of evidence/absence values
     * @return a richer seven-case aggregate preserving all channels
     * @throws NoSuchElementException if the iterable is empty
     */
    public These<E, These<L, R>> combineAll(
            @NonNull Iterable<? extends Those<L, R, E>> values) {
        Iterator<? extends Those<L, R, E>> iterator = values.iterator();
        if (!iterator.hasNext()) {
            throw new NoSuchElementException("Cannot combine empty Those without an identity");
        }

        These<E, These<L, R>> result =
                lift(Objects.requireNonNull(iterator.next(), "null Those in iterable"));
        while (iterator.hasNext()) {
            result = combine(result,
                    lift(Objects.requireNonNull(iterator.next(), "null Those in iterable")));
        }
        return result;
    }

    private static <A extends @Nullable Object, B extends @Nullable Object>
    These<A, B> mergeThese(
            These<A, B> first,
            These<A, B> second,
            BinaryOperator<A> combineA,
            BinaryOperator<B> combineB) {
        return first.fold(
                a -> second.fold(
                        otherA -> These.left(combineA.apply(a, otherA)),
                        b -> These.both(a, b),
                        (otherA, b) -> These.both(combineA.apply(a, otherA), b)),
                b -> second.fold(
                        a -> These.both(a, b),
                        otherB -> These.right(combineB.apply(b, otherB)),
                        (a, otherB) -> These.both(a, combineB.apply(b, otherB))),
                (a, b) -> second.fold(
                        otherA -> These.both(combineA.apply(a, otherA), b),
                        otherB -> These.both(a, combineB.apply(b, otherB)),
                        (otherA, otherB) -> These.both(
                                combineA.apply(a, otherA),
                                combineB.apply(b, otherB))));
    }

    private static <L extends @Nullable Object, R extends @Nullable Object,
            E extends @Nullable Object>
    These<E, These<L, R>> requireEvidence(These<E, These<L, R>> aggregate) {
        aggregate.fold(
                ignored -> Boolean.TRUE,
                evidence -> {
                    Objects.requireNonNull(evidence, "null evidence structure");
                    return Boolean.TRUE;
                },
                (ignored, evidence) -> {
                    Objects.requireNonNull(evidence, "null evidence structure");
                    return Boolean.TRUE;
                });
        return aggregate;
    }
}
