package bbb.hercules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * An immutable sequence optimized for persistent, constant-time concatenation.
 *
 * <p>A mathematical difference list represents a sequence by a function
 * {@code List<A> -> List<A>} and concatenates by function composition.
 * Composing many Java functions can overflow the call stack on evaluation.
 * Hercules instead stores the equivalent append expression as an immutable
 * tree of chunks and elements, then interprets it with an explicit stack.
 * Deep left- or right-associated appends are therefore stack-safe.
 *
 * <p>{@link #append(DList)}, {@link #cons(Object)}, and
 * {@link #snoc(Object)} take constant time and preserve both operands.
 * {@link #fromList(List)} takes a shallow defensive copy. {@link #toList()}
 * materializes an unmodifiable, independent shallow snapshot in linear time
 * in the number of tree nodes and values. Element objects themselves are not
 * deep-copied. As with other persistent sequences, mutating an element object
 * is observable to all structures referring to that element.
 *
 * <p>{@code equals}, {@code hashCode}, and {@code toString} use materialized
 * list values, not tree shape; each is linear in materialized size.
 * Repeated appends followed by a single materialization avoid repeated
 * intermediate list copies. If mutation is already allowed and no persistent
 * intermediate values are needed, an ordinary {@link ArrayList} is simpler
 * and typically faster.
 *
 * <p>All structural arguments are non-null by default. Elements may be null
 * when {@code A} is explicitly declared nullable using JSpecify.
 *
 * @param <A> element type
 */
public final class DList<A extends @Nullable Object> {

    private final Node<A> root;

    private DList(Node<A> root) {
        this.root = root;
    }

    /**
     * Construct the empty difference list (identity for {@link #append(DList)}).
     *
     * @param <A> element type
     * @return an empty immutable list representation
     */
    public static <A extends @Nullable Object> DList<A> empty() {
        return new DList<>(new Empty<>());
    }

    /**
     * Construct a one-element difference list, including a nullable element
     * when the type argument is explicitly nullable.
     *
     * @param <A> element type
     * @param value element to contain
     * @return a singleton difference list
     */
    public static <A extends @Nullable Object> DList<A> singleton(A value) {
        return new DList<>(new One<>(value));
    }

    /**
     * Snapshot an ordinary list into a persistent sequence.
     *
     * <p>The input reference is non-null. The elements are shallow-copied,
     * so later structural mutation of the input cannot affect this DList.
     * Explicitly nullable elements are preserved.
     *
     * @param <A> element type
     * @param values input list to copy
     * @return a persistent difference list
     */
    public static <A extends @Nullable Object> DList<A> fromList(
            @NonNull List<? extends A> values) {
        if (values.isEmpty()) {
            return empty();
        }
        return new DList<>(new Chunk<>(
                Collections.unmodifiableList(new ArrayList<A>(values))));
    }

    /**
     * Prefix a value in constant time; does not mutate this list.
     *
     * @param value element to prepend
     * @return a new list with value at its front
     */
    public DList<A> cons(A value) {
        return new DList<>(new Join<>(new One<>(value), root));
    }

    /**
     * Append a value in constant time; does not mutate this list.
     *
     * @param value element to append
     * @return a new list with value at its end
     */
    public DList<A> snoc(A value) {
        return new DList<>(new Join<>(root, new One<>(value)));
    }

    /**
     * Concatenate two persistent sequences in constant time, in left-to-right
     * order. The operands remain usable unchanged.
     *
     * @param next sequence to concatenate after this one
     * @return this sequence followed by next
     */
    public DList<A> append(@NonNull DList<A> next) {
        if (root instanceof Empty<?>) {
            return next;
        }
        if (next.root instanceof Empty<?>) {
            return this;
        }
        return new DList<>(new Join<>(root, next.root));
    }

    /**
     * Materialize a fresh, unmodifiable list in encounter order.
     *
     * <p>Uses an explicit traversal stack, never recursion or nested
     * function invocation. The result preserves explicitly nullable elements.
     * Each invocation returns a distinct independent list snapshot.
     *
     * @return the elements in append order, as an immutable list snapshot
     */
    public List<A> toList() {
        ArrayList<A> values = new ArrayList<>();
        ArrayDeque<Node<A>> pending = new ArrayDeque<>();
        pending.push(root);

        while (!pending.isEmpty()) {
            pending.pop().visit(values, pending);
        }
        return Collections.unmodifiableList(values);
    }

    /** Value equality by element sequence, independent of concatenation tree shape. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other ||
                (other instanceof DList<?> that && toList().equals(that.toList()));
    }

    /** A value hash code with the same semantics as an ordinary List. */
    @Override
    public int hashCode() {
        return toList().hashCode();
    }

    /** A diagnostic string based on the ordered materialized values. */
    @Override
    public String toString() {
        return "DList" + toList();
    }

    private sealed interface Node<T extends @Nullable Object>
            permits Empty, One, Chunk, Join {
        void visit(ArrayList<T> values, ArrayDeque<Node<T>> pending);
    }

    private record Empty<T extends @Nullable Object>() implements Node<T> {
        @Override
        public void visit(ArrayList<T> values, ArrayDeque<Node<T>> pending) {
            // Identity node: no values.
        }
    }

    private record One<T extends @Nullable Object>(T value) implements Node<T> {
        @Override
        public void visit(ArrayList<T> values, ArrayDeque<Node<T>> pending) {
            values.add(value);
        }
    }

    private record Chunk<T extends @Nullable Object>(List<T> elements) implements Node<T> {
        @Override
        public void visit(ArrayList<T> values, ArrayDeque<Node<T>> pending) {
            values.addAll(elements);
        }
    }

    private record Join<T extends @Nullable Object>(Node<T> left, Node<T> right)
            implements Node<T> {
        @Override
        public void visit(ArrayList<T> values, ArrayDeque<Node<T>> pending) {
            pending.push(right);
            pending.push(left);
        }
    }
}
