package bbb.hercules;

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import lombok.NonNull;

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
public record ValidationApplicative<E>(@NonNull BinaryOperator<E> combineErrors) {

    /**
     * Lift a value into the valid case.
     *
     * @param <A> value type
     * @param value successful payload
     * @return a valid validation
     */
    public <A> Validation<E, A> pure(A value) {
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
    public <A, B, R> Validation<E, R> map2(
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
    public <A, B> Validation<E, B> ap(
            @NonNull Validation<E, ? extends Function<? super A, ? extends B>> function,
            @NonNull Validation<E, A> value) {
        return map2(function, value, (mapper, input) -> mapper.apply(input));
    }
}
