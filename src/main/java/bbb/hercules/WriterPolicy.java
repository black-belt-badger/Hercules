package bbb.hercules;

import java.util.function.BinaryOperator;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An explicit monoidal output policy for composing {@link Writer} values.
 *
 * <p>The caller provides a two-sided identity {@code empty} and an
 * associative {@code combineOutputs} operation. These laws are required for
 * lawful Writer composition but cannot be proven for arbitrary Java
 * functions. Commutativity is <em>not</em> required: output order is
 * significant. Avoid mutating the identity or any output in the combiner.
 *
 * <p>Only the combiner reference is structurally required non-null.
 * The identity is a payload and may be null when the generic type is
 * explicitly nullable. No extra Monoid typeclass or global state is needed.
 *
 * @param <W> output type
 * @param empty caller-supplied two-sided identity for output combination
 * @param combineOutputs caller-supplied associative combination function
 */
public record WriterPolicy<W extends @Nullable Object>(
        W empty,
        @NonNull BinaryOperator<W> combineOutputs) {

    /**
     * Lift a value into Writer with the policy's empty output.
     *
     * @param <A> computation value type
     * @param value computation value (may be explicitly nullable)
     * @return value with identity output, for use in Writer monad laws
     */
    public <A extends @Nullable Object> Writer<W, A> pure(A value) {
        return new Writer<>(value, empty);
    }
}
