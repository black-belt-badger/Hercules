package bbb.hercules.consumer;

import bbb.hercules.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Independent public consumer proving explicit immutable cursor transitions. */
class StatePublicApiTest {

    /** Pure snapshot state: no mutable cursor object or database handle. */
    record Cursor(long nextOffset, int processed, String source) {
        Cursor advance() {
            return new Cursor(nextOffset + 1, processed + 1, source);
        }
    }

    private static State<Cursor, Long> takeNextOffset() {
        return State.<Cursor>get().flatMap(before ->
                State.<Cursor>modify(Cursor::advance)
                        .map(unit -> before.nextOffset()));
    }

    @Test
    void consecutiveStepsThreadCursorWithoutChangingTheOriginalSnapshot() {
        Cursor start = new Cursor(100, 0, "BTC");
        State<Cursor, List<Long>> twoSteps = takeNextOffset()
                .flatMap(first -> takeNextOffset().map(second -> List.of(first, second)));

        State.Result<Cursor, List<Long>> result = twoSteps.run(start);

        assertEquals(new State.Result<>(new Cursor(102, 2, "BTC"),
                List.of(100L, 101L)), result);
        assertEquals(new Cursor(100, 0, "BTC"), start);
        assertEquals(result, twoSteps.run(start), "each run starts independently");
        assertEquals(new State.Result<>(new Cursor(52, 2, "ETH"),
                        List.of(50L, 51L)),
                twoSteps.run(new Cursor(50, 0, "ETH")));
    }

    @Test
    void getPutModifyAndGetsComposeWithoutSideEffects() {
        State<Cursor, Integer> workflow = State.<Cursor>put(
                new Cursor(10, 0, "reset"))
                .flatMap(unit -> State.<Cursor>modify(Cursor::advance))
                .flatMap(unit -> State.<Cursor, Integer>gets(Cursor::processed));

        Cursor original = new Cursor(999, 9, "old");
        assertEquals(new State.Result<>(new Cursor(11, 1, "reset"), 1),
                workflow.run(original));
        assertEquals(new Cursor(999, 9, "old"), original);
        assertEquals(11L, workflow.exec(original).nextOffset());
        assertEquals(1, workflow.eval(original));
    }

    @Test
    void stateUnitIsANonNullTypedResult() {
        State.Result<Cursor, State.Unit> result =
                State.<Cursor>modify(Cursor::advance)
                        .run(new Cursor(9, 0, "BTC"));
        assertSame(State.Unit.INSTANCE, result.value());
        assertEquals(10, result.state().nextOffset());
    }
}
