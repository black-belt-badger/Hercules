package bbb.hercules;

import java.util.Objects;
import java.util.function.Function;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A value together with a pure, accumulated output value.
 *
 * <p>The output is <em>data</em>, not a logging side effect. For example,
 * {@code Writer<DList<Diagnostic>, Result>} can accumulate immutable
 * diagnostic records without writing to a console or log during evaluation.
 *
 * <p>{@link #map(Function)} transforms only the value. Dependent sequencing
 * via {@link #flatMap(Function, WriterPolicy)} combines the earlier output
 * before the later output, using a caller-supplied policy. Both operations
 * are eager and synchronous. Java cannot prove user functions pure.
 *
 * <p>Values and outputs follow JSpecify type-use nullness: explicitly
 * nullable payload specializations are supported. Structural callback and
 * policy references are checked eagerly, while a null Writer returned by
 * a binder is rejected when that binder executes.
 *
 * <p>This record is shallowly immutable. Referential transparency requires
 * output and value payloads (and the supplied combiner) to behave as
 * immutable data; mutable objects are not deep-copied by Hercules.
 *
 * @param <W> accumulated output type
 * @param <A> computation value type
 * @param value computation value
 * @param output accumulated output data
 */
public record Writer<W extends @Nullable Object, A extends @Nullable Object>(
        A value, W output) {

    /**
     * Map the computation value, preserving accumulated output unchanged.
     *
     * @param <B> mapped value type
     * @param mapper eager value transformation
     * @return a Writer of the mapped value and original output
     */
    public <B extends @Nullable Object> Writer<W, B> map(
            @NonNull Function<? super A, ? extends B> mapper) {
        return new Writer<>(mapper.apply(value), output);
    }

    /**
     * Run a dependent Writer computation and append its output to the current
     * output, using the explicit associative policy.
     *
     * <p>The binder is called exactly once, before the combiner. Its returned
     * Writer must not be null. The combiner is called exactly once with the
     * current output first and the next output second. Exceptions propagate
     * unchanged; no output is emitted as an external effect.
     *
     * <p>The lawfulness of this operation depends on the caller supplying
     * the <em>same</em> associative policy and a two-sided identity for all
     * steps in a computation. The runtime does not enforce those algebraic
     * laws. Since binding happens eagerly, repeatedly calling flatMap in
     * a loop does not produce a recursively evaluated function chain.
     *
     * @param <B> next computation value type
     * @param binder next Writer computation
     * @param policy explicit output identity and associative combination
     * @return the next value and ordered combined output
     */
    public <B extends @Nullable Object> Writer<W, B> flatMap(
            @NonNull Function<? super A, ? extends Writer<W, B>> binder,
            @NonNull WriterPolicy<W> policy) {
        Writer<W, B> next = Objects.requireNonNull(
                binder.apply(value), "flatMap returned null Writer");
        return new Writer<>(
                next.value(), policy.combineOutputs().apply(output, next.output()));
    }
}
