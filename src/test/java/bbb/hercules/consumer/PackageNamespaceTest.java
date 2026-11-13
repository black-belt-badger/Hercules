package bbb.hercules.consumer;

import bbb.hercules.Either;
import bbb.hercules.EitherChoice;
import bbb.hercules.F3;
import bbb.hercules.F4;
import bbb.hercules.F5;
import bbb.hercules.F6;
import bbb.hercules.F7;
import bbb.hercules.F8;
import bbb.hercules.F9;
import bbb.hercules.F10;
import bbb.hercules.These;
import bbb.hercules.Those;
import bbb.hercules.Validation;
import bbb.hercules.ValidationApplicative;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public API namespace and its relocated JSpecify package contract. */
class PackageNamespaceTest {

    @Test
    void publicTypesUseTheCanonicalNamespaceAndPreserveNullMarked() {
        Class<?>[] types = {
                Either.class, Either.Left.class, Either.Right.class,
                These.class, These.This.class, These.That.class, These.Both.class,
                Those.class, Those.This.class, Those.That.class,
                Those.Both.class, Those.Neither.class,
                EitherChoice.class, Validation.class,
                Validation.Invalid.class, Validation.Valid.class,
                ValidationApplicative.class,
                F3.class, F4.class, F5.class, F6.class,
                F7.class, F8.class, F9.class, F10.class
        };

        for (Class<?> type : types) {
            assertEquals("bbb.hercules", type.getPackageName(), type.getName());
            assertTrue(type.getPackage().isAnnotationPresent(NullMarked.class),
                    "Missing @NullMarked on package for " + type.getName());
        }
    }
}
