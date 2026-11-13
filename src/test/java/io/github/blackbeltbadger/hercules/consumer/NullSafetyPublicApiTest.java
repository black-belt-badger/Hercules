package io.github.blackbeltbadger.hercules.consumer;

import io.github.blackbeltbadger.hercules.Either;
import io.github.blackbeltbadger.hercules.EitherChoice;
import io.github.blackbeltbadger.hercules.Validation;
import io.github.blackbeltbadger.hercules.ValidationApplicative;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** External-package usage verifies that compiled library bytecode enforces contracts. */
class NullSafetyPublicApiTest {

    @Test
    void compiledLibraryMethodsRejectNullStructuralReferences() {
        Either<String, Integer> value = Either.right(42);

        assertThrows(NullPointerException.class, () -> value.map(null));
        assertThrows(NullPointerException.class, () -> value.fold(
                Function.identity(), null));
        assertThrows(NullPointerException.class, () -> new EitherChoice<String>("", null));
        assertThrows(NullPointerException.class,
                () -> new ValidationApplicative<List<String>>(null));

        var app = new ValidationApplicative<List<String>>((a, b) -> a);
        Validation<List<String>, Integer> valid = Validation.valid(3);
        assertThrows(NullPointerException.class,
                () -> app.map2(valid, valid, null));
    }

    @Test
    void compiledLibraryStillAllowsExplicitNullablePayloadTypes() {
        Either<@Nullable String, Integer> left = Either.left(null);
        Validation<@Nullable String, @Nullable Integer> valid = Validation.valid(null);
        EitherChoice<@Nullable String> choice = new EitherChoice<>(null,
                (a, b) -> a != null ? a : b);

        assertInstanceOf(Either.Left.class, left);
        assertInstanceOf(Validation.Valid.class, valid);
        assertEquals(Either.left(null), choice.empty());
        assertEquals(Either.right(null), valid.toEither());
    }
}
