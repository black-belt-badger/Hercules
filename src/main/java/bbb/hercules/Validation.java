package bbb.hercules;

import java.util.function.Function;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A successful value or accumulated validation errors.
 *
 * <p>Unlike {@link Either}, this type is intended for combining independent
 * validation checks with {@link ValidationApplicative}. It does not define
 * dependent {@code flatMap}: such sequencing would have different, fail-fast
 * semantics from independent error accumulation.
 *
 * <p>{@link Valid} and {@link Invalid} are shallowly immutable records. The
 * payloads themselves are not copied. JSpecify is non-null by default;
 * callers may explicitly select nullable payload type arguments.
 *
 * @param <E> validation error payload type, often an immutable list
 * @param <A> successful value type
 */
public sealed interface Validation<E extends @Nullable Object, A extends @Nullable Object>
        permits Validation.Invalid, Validation.Valid {

    /**
     * Eliminate either case, checking both callbacks for null even if unused.
     *
     * @param <R> fold result type
     * @param onInvalid handler for accumulated validation errors
     * @param onValid handler for a valid value
     * @return the result from the active case
     */
    <R extends @Nullable Object> R fold(@NonNull Function<? super E, ? extends R> onInvalid,
               @NonNull Function<? super A, ? extends R> onValid);

    /**
     * Construct an invalid result.
     *
     * @param <E> validation error type
     * @param <A> successful value type
     * @param errors the error payload
     * @return an invalid validation
     */
    static <E extends @Nullable Object, A extends @Nullable Object> Validation<E, A> invalid(E errors) {
        return new Invalid<>(errors);
    }

    /**
     * Construct a valid result.
     *
     * @param <E> validation error type
     * @param <A> successful value type
     * @param value the successful payload
     * @return a valid validation
     */
    static <E extends @Nullable Object, A extends @Nullable Object> Validation<E, A> valid(A value) {
        return new Valid<>(value);
    }

    /**
     * Transform a valid payload, retaining invalid errors unchanged.
     *
     * @param <B> new valid payload type
     * @param mapper transformation applied only when valid
     * @return a mapped validation with the same case
     */
    default <B extends @Nullable Object> Validation<E, B> map(@NonNull Function<? super A, ? extends B> mapper) {
        return fold(Validation::invalid, value -> Validation.valid(mapper.apply(value)));
    }

    /**
     * Transform errors, retaining a valid payload unchanged.
     *
     * @param <F> new error payload type
     * @param mapper transformation applied only when invalid
     * @return a validation with transformed errors where present
     */
    default <F extends @Nullable Object> Validation<F, A> mapLeft(@NonNull Function<? super E, ? extends F> mapper) {
        return fold(errors -> Validation.invalid(mapper.apply(errors)), Validation::valid);
    }

    /**
     * Transform each payload with the appropriate callback.
     *
     * @param <F> new error payload type
     * @param <B> new valid payload type
     * @param errorMapper mapping function for errors
     * @param valueMapper mapping function for valid results
     * @return a validation with the active payload transformed
     */
    default <F extends @Nullable Object, B extends @Nullable Object> Validation<F, B> bimap(
            @NonNull Function<? super E, ? extends F> errorMapper,
            @NonNull Function<? super A, ? extends B> valueMapper) {
        return fold(errors -> Validation.invalid(errorMapper.apply(errors)),
                    value -> Validation.valid(valueMapper.apply(value)));
    }

    /**
     * Convert structurally to Either, preserving the variant and payload.
     * This is a representation conversion, not an equivalence between
     * fail-fast and accumulating composition semantics.
     *
     * @return the equivalent Left or Right value
     */
    default Either<E, A> toEither() {
        return fold(Either::left, Either::right);
    }

    /**
     * Reconstruct the matching validation variant from Either.
     *
     * @param <E> error type
     * @param <A> valid value type
     * @param source an Either to convert, not null
     * @return an equivalent invalid or valid value
     */
    static <E extends @Nullable Object, A extends @Nullable Object> Validation<E, A> fromEither(@NonNull Either<E, A> source) {
        return source.fold(Validation::invalid, Validation::valid);
    }

    /**
     * Validation errors; no successful value is retained.
     *
     * @param <E> error payload type
     * @param <A> successful value type
     * @param errors accumulated validation errors
     */
    record Invalid<E extends @Nullable Object, A extends @Nullable Object>(E errors) implements Validation<E, A> {

        @Override
        public <R extends @Nullable Object> R fold(@NonNull Function<? super E, ? extends R> onInvalid,
                          @NonNull Function<? super A, ? extends R> onValid) {
            return onInvalid.apply(errors);
        }
    }

    /**
     * Successful validation; no errors are present.
     *
     * @param <E> error payload type
     * @param <A> successful value type
     * @param value the valid payload
     */
    record Valid<E extends @Nullable Object, A extends @Nullable Object>(A value) implements Validation<E, A> {

        @Override
        public <R extends @Nullable Object> R fold(@NonNull Function<? super E, ? extends R> onInvalid,
                          @NonNull Function<? super A, ? extends R> onValid) {
            return onValid.apply(value);
        }
    }
}
