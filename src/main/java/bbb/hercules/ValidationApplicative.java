package bbb.hercules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
}
