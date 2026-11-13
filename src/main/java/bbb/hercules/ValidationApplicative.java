package bbb.hercules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An explicit error-accumulation policy for independent {@link Validation}
 * computations.
 *
 * <p>The caller supplies an associative {@code combineErrors} operation.
 * Associativity is a law expected of the supplied function, not something
 * an arbitrary Java {@link BinaryOperator} guarantees. An identity is not
 * required. Error combination runs in left-to-right argument order; no
 * commutativity is assumed.
 *
 * <p>Inputs are already computed, so this policy cannot short-circuit their
 * evaluation. Error accumulation differs from the dependent fail-fast
 * sequencing of {@link Either#flatMap(Function)}.
 *
 * @param <E> validation error payload type
 * @param combineErrors associative error-combination operation
 */
public record ValidationApplicative<E extends @Nullable Object>(@NonNull BinaryOperator<E> combineErrors) {

    /**
     * Lift a value into the valid case.
     *
     * @param <A> value type
     * @param value successful payload
     * @return a valid validation
     */
    public <A extends @Nullable Object> Validation<E, A> pure(A value) {
        return Validation.valid(value);
    }

    /**
     * Combine two independent validations.
     *
     * <p>Both Valid invokes the mapper once; one Invalid returns its errors;
     * two Invalid combines the errors left first, right second. The mapper
     * reference is checked even when either input is invalid, but only
     * invoked when both are valid. User exceptions propagate unchanged.
     *
     * @param <A> type of the left valid value
     * @param <B> type of the right valid value
     * @param <R> combined result type
     * @param left first validation, not null
     * @param right second validation, not null
     * @param mapper function applied only when both validations succeed
     * @return Valid of the mapped result, or Invalid of accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map2(
            @NonNull Validation<E, A> left,
            @NonNull Validation<E, B> right,
            @NonNull BiFunction<? super A, ? super B, ? extends R> mapper) {
        return left.fold(
                leftError -> right.fold(
                        rightError -> Validation.invalid(combineErrors.apply(leftError, rightError)),
                        ignored -> Validation.invalid(leftError)),
                leftValue -> right.fold(
                        Validation::invalid,
                        rightValue -> Validation.valid(mapper.apply(leftValue, rightValue))));
    }

    /**
     * Apply a validated function to a validated value, accumulating errors
     * from the function validation before those from the value validation.
     *
     * @param <A> function input type
     * @param <B> function result type
     * @param function validated transformation
     * @param value validated input
     * @return a valid mapped result, or accumulated invalid errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object> Validation<E, B> ap(
            @NonNull Validation<E, ? extends Function<? super A, ? extends B>> function,
            @NonNull Validation<E, A> value) {
        return map2(function, value, (mapper, input) -> mapper.apply(input));
    }

    /**
     * Collect an ordered, finite sequence of independently computed validations.
     *
     * <p>Every element is visited, even after an invalid result. Errors are
     * accumulated in encounter order using {@code combineErrors}; no identity
     * is required. An empty iterable produces {@code Valid(List.of())}.
     * Successful payloads are returned in an unmodifiable defensive snapshot,
     * including explicitly nullable payloads.
     *
     * <p>A null validation element is rejected when encountered. Iterator and
     * error-combiner exceptions propagate; infinite iterables never terminate.
     *
     * @param <A> successful element type
     * @param values finite sequence of non-null Validation containers
     * @return Valid of immutable ordered values, or Invalid of all accumulated errors
     */
    public <A extends @Nullable Object> Validation<E, List<A>> sequence(
            @NonNull Iterable<? extends Validation<E, ? extends A>> values) {
        return traverse(values, Function.identity());
    }

    /**
     * Apply a validation-producing mapper to every element of a finite iterable.
     *
     * <p>Both iteration and mapping are eager and synchronous. The mapper is
     * invoked exactly once per element, in encounter order, even if earlier
     * elements were invalid. Only invalid payloads are combined; successful
     * payloads are returned in an unmodifiable defensive snapshot. Inputs may
     * be null when their generic type use is explicitly nullable.
     *
     * <p>The iterable and mapper references are checked eagerly. A null
     * Validation returned by the mapper is rejected when encountered.
     * Exceptions from iteration, mapping, or combining propagate unchanged.
     * This operation does not schedule work, cancel checks, or support
     * infinite iterables.
     *
     * @param <A> input element type
     * @param <B> successful mapped element type
     * @param values finite iterable visited in encounter order
     * @param mapper independent validation for each input element
     * @return Valid of immutable ordered mapped values, or Invalid of all errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object> Validation<E, List<B>> traverse(
            @NonNull Iterable<? extends A> values,
            @NonNull Function<? super A, ? extends Validation<E, ? extends B>> mapper) {
        Validation<E, ArrayList<B>> accumulated = pure(new ArrayList<>());

        for (A input : values) {
            Validation<E, ? extends B> current =
                    Objects.requireNonNull(mapper.apply(input), "traverse returned null Validation");
            accumulated = map2(accumulated, current, (successes, value) -> {
                successes.add(value);
                return successes;
            });
        }

        return accumulated.map(successes ->
                Collections.unmodifiableList(new ArrayList<>(successes)));
    }

    /**
     * Combine the normal results of two independently supplied asynchronous stages.
     *
     * <p>This method starts no work and never blocks. It registers a JDK
     * {@link CompletionStage#thenCombine(CompletionStage, BiFunction)}
     * continuation: when both supplied stages complete normally with non-null
     * Validation objects, the existing {@link #map2(Validation, Validation, BiFunction)}
     * accumulates their errors in <em>argument order</em>, regardless of
     * completion order. The mapper runs only when both values are Valid.
     *
     * <p>A null stage reference or mapper is rejected immediately. A null
     * Validation stage result or exception thrown by the mapper/combiner
     * completes the dependent stage exceptionally. Source stage exceptions
     * and cancellation stay exceptional; none become an Invalid payload.
     * No cancellation propagation or executor policy is introduced.
     *
     * @param <A> successful left value type
     * @param <B> successful right value type
     * @param <R> successful combined value type
     * @param left already-created left stage
     * @param right already-created right stage
     * @param mapper function invoked only when both validations are Valid
     * @return a dependent stage with accumulated Validation results
     */
    public <A extends @Nullable Object, B extends @Nullable Object, R extends @Nullable Object>
    CompletionStage<Validation<E, R>> map2Stages(
            @NonNull CompletionStage<? extends Validation<E, A>> left,
            @NonNull CompletionStage<? extends Validation<E, B>> right,
            @NonNull BiFunction<? super A, ? super B, ? extends R> mapper) {
        return left.thenCombine(right, (first, second) -> map2(
                Objects.requireNonNull(first, "left stage returned null Validation"),
                Objects.requireNonNull(second, "right stage returned null Validation"),
                mapper));
    }

    /**
     * Combine the normal results of a finite ordered iterable of stages.
     *
     * <p>The iterable is consumed once at registration time, without waiting
     * for any stages and without starting them. Every supplied stage is
     * registered, even if earlier results are Invalid. When all stages
     * complete normally, their validation errors are combined in iterable
     * order, not completion order. An empty iterable returns an already
     * completed Valid containing an empty immutable list. Successful
     * payloads retain their order and are exposed through an immutable
     * defensive list copy, including explicitly nullable payloads.
     *
     * <p>Null iterable and stage references fail synchronously when
     * encountered. Null Validation results, or exceptions from combining
     * errors, complete the dependent stage exceptionally. Exceptional or
     * cancelled source stages do not become Invalid and are not recovered;
     * cancellation of the result does not cancel inputs. Neither work
     * scheduling nor an executor/cancellation policy is implied. Infinite
     * input iterables do not terminate registration.
     *
     * @param <A> successful element type
     * @param stages finite iterable of independently supplied stages
     * @return a dependent stage with an immutable list of valid payloads
     *         or all normally produced validation errors
     */
    public <A extends @Nullable Object> CompletionStage<Validation<E, List<A>>> sequenceStages(
            @NonNull Iterable<? extends CompletionStage<? extends Validation<E, ? extends A>>> stages) {
        CompletionStage<Validation<E, ArrayList<A>>> accumulated =
                CompletableFuture.completedFuture(pure(new ArrayList<>()));

        for (CompletionStage<? extends Validation<E, ? extends A>> stage : stages) {
            CompletionStage<? extends Validation<E, ? extends A>> current =
                    Objects.requireNonNull(stage, "sequenceStages encountered null stage");
            accumulated = accumulated.thenCombine(current, (previous, next) ->
                    map2(previous,
                            Objects.requireNonNull(next, "stage returned null Validation"),
                            (values, value) -> {
                                values.add(value);
                                return values;
                            }));
        }

        return accumulated.thenApply(result ->
                result.map(values -> Collections.unmodifiableList(new ArrayList<>(values))));
    }

    // BEGIN generated map3-map10 (scripts/generate_validation_mapn.py)

    /**
     * Combine 3 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map3(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull F3<? super A, ? super B, ? super C, ? extends R> mapper) {
        return ap(ap(ap(pure(mapper.curried()), a), b), c);
    }

    /**
     * Combine 4 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map4(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull F4<? super A, ? super B, ? super C, ? super D, ? extends R> mapper) {
        return ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d);
    }

    /**
     * Combine 5 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map5(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull F5<? super A, ? super B, ? super C, ? super D, ? super F, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f);
    }

    /**
     * Combine 6 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <G> successful value type of argument 6
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param g independent validation 6
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, G extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map6(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull Validation<E, G> g,
            @NonNull F6<? super A, ? super B, ? super C, ? super D, ? super F, ? super G, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f), g);
    }

    /**
     * Combine 7 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <G> successful value type of argument 6
     * @param <H> successful value type of argument 7
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param g independent validation 6
     * @param h independent validation 7
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, G extends @Nullable Object, H extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map7(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull Validation<E, G> g,
            @NonNull Validation<E, H> h,
            @NonNull F7<? super A, ? super B, ? super C, ? super D, ? super F, ? super G, ? super H, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f), g), h);
    }

    /**
     * Combine 8 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <G> successful value type of argument 6
     * @param <H> successful value type of argument 7
     * @param <I> successful value type of argument 8
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param g independent validation 6
     * @param h independent validation 7
     * @param i independent validation 8
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, G extends @Nullable Object, H extends @Nullable Object, I extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map8(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull Validation<E, G> g,
            @NonNull Validation<E, H> h,
            @NonNull Validation<E, I> i,
            @NonNull F8<? super A, ? super B, ? super C, ? super D, ? super F, ? super G, ? super H, ? super I, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f), g), h), i);
    }

    /**
     * Combine 9 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <G> successful value type of argument 6
     * @param <H> successful value type of argument 7
     * @param <I> successful value type of argument 8
     * @param <J> successful value type of argument 9
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param g independent validation 6
     * @param h independent validation 7
     * @param i independent validation 8
     * @param j independent validation 9
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, G extends @Nullable Object, H extends @Nullable Object, I extends @Nullable Object, J extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map9(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull Validation<E, G> g,
            @NonNull Validation<E, H> h,
            @NonNull Validation<E, I> i,
            @NonNull Validation<E, J> j,
            @NonNull F9<? super A, ? super B, ? super C, ? super D, ? super F, ? super G, ? super H, ? super I, ? super J, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f), g), h), i), j);
    }

    /**
     * Combine 10 independent validations, accumulating all errors in input order.
     *
     * <p>Input validations are already evaluated. Errors combine left to right,
     * with no identity or commutativity requirement. The mapper runs exactly
     * once only when all inputs are Valid; errors from callbacks propagate.
     *
     * @param <A> successful value type of argument 1
     * @param <B> successful value type of argument 2
     * @param <C> successful value type of argument 3
     * @param <D> successful value type of argument 4
     * @param <F> successful value type of argument 5
     * @param <G> successful value type of argument 6
     * @param <H> successful value type of argument 7
     * @param <I> successful value type of argument 8
     * @param <J> successful value type of argument 9
     * @param <K> successful value type of argument 10
     * @param <R> mapped result type
     * @param a independent validation 1
     * @param b independent validation 2
     * @param c independent validation 3
     * @param d independent validation 4
     * @param f independent validation 5
     * @param g independent validation 6
     * @param h independent validation 7
     * @param i independent validation 8
     * @param j independent validation 9
     * @param k independent validation 10
     * @param mapper function applied when all arguments are valid
     * @return a mapped successful value, or accumulated errors
     */
    public <A extends @Nullable Object, B extends @Nullable Object, C extends @Nullable Object, D extends @Nullable Object, F extends @Nullable Object, G extends @Nullable Object, H extends @Nullable Object, I extends @Nullable Object, J extends @Nullable Object, K extends @Nullable Object, R extends @Nullable Object> Validation<E, R> map10(
            @NonNull Validation<E, A> a,
            @NonNull Validation<E, B> b,
            @NonNull Validation<E, C> c,
            @NonNull Validation<E, D> d,
            @NonNull Validation<E, F> f,
            @NonNull Validation<E, G> g,
            @NonNull Validation<E, H> h,
            @NonNull Validation<E, I> i,
            @NonNull Validation<E, J> j,
            @NonNull Validation<E, K> k,
            @NonNull F10<? super A, ? super B, ? super C, ? super D, ? super F, ? super G, ? super H, ? super I, ? super J, ? super K, ? extends R> mapper) {
        return ap(ap(ap(ap(ap(ap(ap(ap(ap(ap(pure(mapper.curried()), a), b), c), d), f), g), h), i), j), k);
    }

    // END generated map3-map10
}
