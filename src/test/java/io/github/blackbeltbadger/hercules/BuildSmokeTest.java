package io.github.blackbeltbadger.hercules;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildSmokeTest {

    private record Pair(int left, int right) {}

    @Getter
    @RequiredArgsConstructor
    private static final class Named {
        @NonNull
        private final String value;
    }

    @Test
    void supportsJava17RecordsAndGuavaImmutableCollections() {
        var items = ImmutableList.of(new Pair(1, 2), new Pair(3, 4));
        assertEquals(10, items.stream().mapToInt(p -> p.left() + p.right()).sum());
    }

    @Test
    void usesLombokGeneratedCodeAndRuntimeNullChecks() {
        assertEquals("Hercules", new Named("Hercules").getValue());
        assertThrows(NullPointerException.class, () -> new Named(null));
    }
}
