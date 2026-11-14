package bbb.hercules.consumer;

import bbb.hercules.Trampoline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** External-package example of deferred, pure market-data progress calculation. */
class TrampolinePublicApiTest {

    record Cursor(int remaining, long processed, long checksum) {
        Cursor advance() {
            return new Cursor(remaining - 1, processed + 1,
                    checksum + remaining);
        }
    }

    /** A single suspended step; no I/O, no mutable cursor. */
    private static Trampoline<Cursor> scan(Cursor cursor) {
        return cursor.remaining() == 0
                ? Trampoline.done(cursor)
                : Trampoline.more(() -> scan(cursor.advance()));
    }

    @Test
    void largePureScanComputesCorrectResultWithoutStackOverflow() {
        Cursor start = new Cursor(250_000, 0, 0);
        Trampoline<Cursor> computation = scan(start);
        assertEquals(new Cursor(250_000, 0, 0), start);
        assertEquals(new Cursor(0, 250_000, 31_250_125_000L), computation.run());
        assertEquals(new Cursor(0, 250_000, 31_250_125_000L), computation.run(),
                "running pure descriptions repeatedly is deterministic");
    }

    @Test
    void canComposePostprocessingWithoutForcingTheScan() {
        Trampoline<List<Long>> processed = scan(new Cursor(1_000, 0, 0))
                .map(cursor -> List.of(cursor.processed(), cursor.checksum()))
                .flatMap(result -> Trampoline.done(result));

        assertEquals(List.of(1_000L, 500_500L), processed.run());
    }

    @Test
    void nullablePayloadsRemainResultsNotStructuralFailure() {
        Trampoline<@org.jspecify.annotations.Nullable String> maybe = Trampoline.done(null);
        assertNull(maybe.run());
        assertEquals("absent", maybe.map(s -> s == null ? "absent" : s).run());
    }
}
