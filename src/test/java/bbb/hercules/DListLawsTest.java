package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Functional laws, persistence and stack safety for the DList implementation. */
@ExtendWith(InstancioExtension.class)
class DListLawsTest {

    @RepeatedTest(64)
    void appendIsAssociativeWithLeftAndRightIdentity(
            @Given Integer first, @Given Integer second, @Given Integer third,
            @Given String word) {
        DList<Integer> a = DList.singleton(first);
        DList<Integer> b = DList.fromList(List.of(second, third));
        DList<Integer> c = DList.fromList(List.of(first, third, second));
        DList<Integer> empty = DList.empty();

        assertEquals(a, empty.append(a));
        assertEquals(a, a.append(empty));
        assertEquals(b, empty.append(b));
        assertEquals(b, b.append(empty));
        assertEquals(empty, empty.append(DList.empty()));

        assertEquals(a.append(b).append(c), a.append(b.append(c)));
        assertEquals(List.of(first, second, third, first, third, second),
                a.append(b).append(c).toList());
        assertEquals(a.append(b).append(c).hashCode(),
                a.append(b.append(c)).hashCode());
    }

    @RepeatedTest(64)
    void consSnocAndFromListAgreeWithOrdinaryLists(
            @Given String a, @Given String b, @Given String c,
            @Given String d) {
        List<String> oracle = List.of(a, b, c, d);
        DList<String> built = DList.<String>empty()
                .snoc(b).snoc(c).cons(a).snoc(d);
        DList<String> fromList = DList.fromList(oracle);

        assertEquals(oracle, built.toList());
        assertEquals(oracle, fromList.toList());
        assertEquals(built, fromList);
        assertEquals(fromList, DList.singleton(a)
                .append(DList.singleton(b))
                .append(DList.fromList(List.of(c, d))));
        assertEquals(built.hashCode(), fromList.hashCode());
        assertEquals("DList" + oracle, fromList.toString());
        assertEquals(List.of(), DList.empty().toList());
    }

    @RepeatedTest(64)
    void deterministicMixedConstructionAgreesWithListOracle(
            @Given int seed, @Given int shift) {
        List<Integer> oracle = new ArrayList<>();
        DList<Integer> result = DList.empty();
        int count = 40 + Math.floorMod(seed, 80);
        for (int i = 0; i < count; i++) {
            int value = i ^ shift;
            switch (Math.floorMod(i + seed, 4)) {
                case 0 -> {
                    result = result.snoc(value);
                    oracle.add(value);
                }
                case 1 -> {
                    result = result.cons(value);
                    oracle.add(0, value);
                }
                case 2 -> {
                    List<Integer> chunk = List.of(value, value + 1);
                    result = result.append(DList.fromList(chunk));
                    oracle.addAll(chunk);
                }
                case 3 -> {
                    List<Integer> chunk = List.of(value, value + 1);
                    result = DList.fromList(chunk).append(result);
                    oracle.addAll(0, chunk);
                }
                default -> throw new AssertionError("unreachable");
            }
        }
        assertEquals(oracle, result.toList());
        assertEquals(result, DList.fromList(oracle));
    }

    @Test
    void deepLeftAndRightAssociatedAppendMaterializeWithoutRecursion() {
        int count = 120_000;
        DList<Integer> left = DList.empty();
        DList<Integer> right = DList.empty();
        for (int i = 0; i < count; i++) {
            left = left.append(DList.singleton(i));
            right = DList.singleton(count - 1 - i).append(right);
        }
        List<Integer> leftValues = left.toList();
        List<Integer> rightValues = right.toList();
        assertEquals(count, leftValues.size());
        assertEquals(leftValues, rightValues);
        assertEquals(0, leftValues.get(0));
        assertEquals(count / 2, leftValues.get(count / 2));
        assertEquals(count - 1, leftValues.get(count - 1));
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void deepConsAndSnocMaterializeWithoutRecursion() {
        int count = 110_000;
        DList<Integer> cons = DList.empty();
        DList<Integer> snoc = DList.empty();
        for (int i = 0; i < count; i++) {
            cons = cons.cons(count - 1 - i);
            snoc = snoc.snoc(i);
        }
        assertEquals(snoc.toList(), cons.toList());
        assertEquals(0, cons.toList().get(0));
        assertEquals(count - 1, snoc.toList().get(count - 1));
    }

    @Test
    void snapshotsAreIndependentAndOldBranchesRemainUsable() {
        List<Integer> input = new ArrayList<>(List.of(1, 2, 3));
        DList<Integer> origin = DList.fromList(input);
        DList<Integer> branch1 = origin.snoc(4);
        DList<Integer> branch2 = origin.cons(0);

        input.add(99);
        input.set(0, -1);

        assertEquals(List.of(1, 2, 3), origin.toList());
        assertEquals(List.of(1, 2, 3, 4), branch1.toList());
        assertEquals(List.of(0, 1, 2, 3), branch2.toList());
        assertEquals(List.of(1, 2, 3, 1, 2, 3), origin.append(origin).toList());

        List<Integer> materialized = branch1.toList();
        List<Integer> anotherSnapshot = branch1.toList();
        assertNotSame(materialized, anotherSnapshot);
        assertThrows(UnsupportedOperationException.class, () -> materialized.add(5));
        assertThrows(UnsupportedOperationException.class, () -> anotherSnapshot.set(0, 42));
    }

    @Test
    void explicitlyNullableElementsArePreservedAtEveryOperation() {
        List<@Nullable String> values = Arrays.asList(null, "b", null);
        DList<@Nullable String> data = DList.fromList(values);
        DList<@Nullable String> result = data.cons(null).snoc(null)
                .append(DList.singleton(null));

        List<@Nullable String> expected =
                Arrays.asList(null, null, "b", null, null, null);
        assertEquals(expected, result.toList());
        assertEquals(result, DList.fromList(expected));
        assertEquals(List.of(), DList.<@Nullable String>empty().toList());

        values.set(1, "changed");
        assertEquals(expected, result.toList());
    }

    @Test
    void structuralInputsAreCheckedAtCallTime() {
        assertThrows(NullPointerException.class, () -> DList.fromList(null));
        assertThrows(NullPointerException.class, () -> DList.empty().append(null));
        assertThrows(NullPointerException.class, () -> DList.singleton(1).append(null));
    }

    @Test
    void valueEqualityIsIndependentOfTreeShapeAndRejectsOtherTypes() {
        var a = DList.fromList(List.of(1, 2, 3));
        var b = DList.singleton(1).snoc(2).snoc(3);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, List.of(1, 2, 3));
        assertNotEquals(a, DList.fromList(List.of(3, 2, 1)));
        assertNotEquals(a, null);
    }

    @Test
    void emptyAppendIsAnIdentityEvenForNullableTypes() {
        DList<@Nullable Integer> empty = DList.empty();
        DList<@Nullable Integer> withNull = DList.singleton(null);
        assertEquals(withNull, empty.append(withNull));
        assertEquals(withNull, withNull.append(empty));
    }
}
