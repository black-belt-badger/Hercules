package io.github.blackbeltbadger.hercules;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionExamplesTest {

    @Test
    void f3ReaderAndMap() {
        F3<Integer, Integer, Integer, Integer> add = (a, b, c) -> a + b + c;

        assertEquals("9", add.map(Object::toString).apply(2, 3, 4));
        assertEquals(36, add.flatMap(sum -> (a, b, c) -> sum * c).apply(2, 3, 4));
    }

    @Test
    void f5PartialComposeAndCurrying() {
        F5<Integer, Integer, Integer, Integer, Integer, Integer> sum =
                (a, b, c, d, e) -> a + b + c + d + e;

        F3<Integer, Integer, Integer, Integer> add30 = sum.partial(10, 20);
        assertEquals(36, add30.apply(1, 2, 3));

        F5<String, Integer, Integer, Integer, Integer, Integer> parseFirst =
                sum.compose1(Integer::parseInt);
        assertEquals(36, parseFirst.apply("10", 20, 1, 2, 3));

        var curried = sum.curried();
        assertEquals(15, curried.apply(1).apply(2).apply(3).apply(4).apply(5));
        assertEquals(15, F5.uncurry(curried).apply(1, 2, 3, 4, 5));
    }

    @Test
    void f10PartialReturnsJdkFunction() {
        F10<Integer, Integer, Integer, Integer, Integer,
                Integer, Integer, Integer, Integer, Integer, Integer> ten =
                (a, b, c, d, e, f, g, h, i, j) -> a + b + c + d + e + f + g + h + i + j;

        Function<Integer, Integer> last = ten.partial(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(55, last.apply(10));
    }

    @Test
    void callbackExceptionsPropagateUnchanged() {
        F5<Integer, Integer, Integer, Integer, Integer, Integer> source =
                (a, b, c, d, e) -> a + b + c + d + e;
        var failure = new IllegalStateException("callback");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> source.map(x -> { throw failure; }).apply(1, 2, 3, 4, 5)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> source.flatMap(x -> { throw failure; }).apply(1, 2, 3, 4, 5)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> source.flatMap(x -> (a, b, c, d, e) -> { throw failure; })
                        .apply(1, 2, 3, 4, 5)));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> source.compose1((Integer x) -> { throw failure; })
                        .apply(1, 2, 3, 4, 5)));
    }
}
