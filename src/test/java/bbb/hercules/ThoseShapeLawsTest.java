package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(InstancioExtension.class)
class ThoseShapeLawsTest {

    @RepeatedTest(64)
    void informationLatticeLawsHoldForAllShapes(@Given String explanation) {
        for (ThoseShape x : ThoseShape.values()) {
            assertEquals(x, x.join(ThoseShape.NEITHER));
            assertEquals(x, x.meet(ThoseShape.BOTH));
            assertEquals(x, x.join(x));
            assertEquals(x, x.meet(x));

            for (ThoseShape y : ThoseShape.values()) {
                assertEquals(x.join(y), y.join(x));
                assertEquals(x.meet(y), y.meet(x));
                assertEquals(x, x.join(x.meet(y)));
                assertEquals(x, x.meet(x.join(y)));

                for (ThoseShape z : ThoseShape.values()) {
                    assertEquals(x.join(y).join(z), x.join(y.join(z)));
                    assertEquals(x.meet(y).meet(z), x.meet(y.meet(z)));
                }
            }
        }

        assertEquals(ThoseShape.NEITHER,
                ThoseShape.of(Those.<String, Integer, String>neither(explanation)));
        assertEquals(ThoseShape.LEFT,
                ThoseShape.of(Those.<String, Integer, String>left(explanation)));
        assertEquals(ThoseShape.RIGHT,
                ThoseShape.of(Those.<String, Integer, String>right(42)));
        assertEquals(ThoseShape.BOTH,
                ThoseShape.of(Those.<String, Integer, String>both(explanation, 42)));
    }

    @Test
    void shapeOnlyProjectionIsDeliberatelyLossy() {
        var timeout = Those.<String, Integer, String>neither("provider A timed out");
        var unavailable = Those.<String, Integer, String>neither("provider B unavailable");

        assertNotEquals(timeout, unavailable);
        assertEquals(ThoseShape.of(timeout), ThoseShape.of(unavailable));
        assertEquals(ThoseShape.RIGHT,
                ThoseShape.of(timeout).join(ThoseShape.RIGHT));
        assertThrows(NullPointerException.class, () -> ThoseShape.of(null));
        assertThrows(NullPointerException.class, () -> ThoseShape.LEFT.join(null));
        assertThrows(NullPointerException.class, () -> ThoseShape.LEFT.meet(null));
    }

    @Test
    void leftAndRightAreIncomparableButJoinToBoth() {
        assertEquals(ThoseShape.BOTH, ThoseShape.LEFT.join(ThoseShape.RIGHT));
        assertEquals(ThoseShape.NEITHER, ThoseShape.LEFT.meet(ThoseShape.RIGHT));
    }
}
