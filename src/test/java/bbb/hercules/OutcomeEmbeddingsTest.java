package bbb.hercules;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-type naturality: widening a result before or after a transformation
 * must give the same observable result, without ever discarding a variant.
 */
@ExtendWith(InstancioExtension.class)
class OutcomeEmbeddingsTest {

    private static <L, R> List<Either<L, R>> eithers(L left, R right) {
        return List.of(Either.left(left), Either.right(right));
    }

    private static <L, R> List<These<L, R>> theses(L left, R right) {
        return List.of(These.left(left), These.right(right), These.both(left, right));
    }

    @RepeatedTest(64)
    void eitherToThesePreservesMappingsAndSwap(
            @Given String left, @Given Integer right) {
        Function<String, Integer> leftMapper = String::length;
        Function<Integer, String> rightMapper = n -> "value:" + n;

        for (Either<String, Integer> source : eithers(left, right)) {
            assertEquals(source.map(rightMapper).toThese(),
                    source.toThese().map(rightMapper));
            assertEquals(source.mapLeft(leftMapper).toThese(),
                    source.toThese().mapLeft(leftMapper));
            assertEquals(source.bimap(leftMapper, rightMapper).toThese(),
                    source.toThese().bimap(leftMapper, rightMapper));
            assertEquals(source.swap().toThese(), source.toThese().swap());
            assertEquals(source.toThese(),
                    source.fold(These::left, These::right));
        }
    }

    @RepeatedTest(64)
    void theseToThosePreservesMappingsAndSwap(
            @Given String left, @Given Integer right) {
        Function<String, Integer> leftMapper = String::length;
        Function<Integer, String> rightMapper = n -> "value:" + n;

        for (These<String, Integer> source : theses(left, right)) {
            assertEquals(source.map(rightMapper).<Long>toThose(),
                    source.<Long>toThose().map(rightMapper));
            assertEquals(source.mapLeft(leftMapper).<Long>toThose(),
                    source.<Long>toThose().mapLeft(leftMapper));
            assertEquals(source.bimap(leftMapper, rightMapper).<Long>toThose(),
                    source.<Long>toThose().bimap(leftMapper, rightMapper));
            assertEquals(source.swap().<Long>toThose(),
                    source.<Long>toThose().swap());

            assertEquals(source.<Long>toThose(),
                    source.fold(Those::left, Those::right, Those::both));
        }
    }

    @RepeatedTest(64)
    void embeddingsCooperateWithTheNestedEitherTheseIsomorphism(
            @Given String left, @Given Integer right) {
        for (These<String, Integer> source : theses(left, right)) {
            Those<String, Integer, Long> widened = source.toThose();
            Either<Long, These<String, Integer>> nested = Either.right(source);

            assertEquals(widened, Those.fromEither(nested));
            assertEquals(nested, widened.toEither());
            assertEquals(source, widened.toEither().fold(
                    ignored -> fail("embedding cannot produce Neither"),
                    Function.identity()));
        }

        for (Either<String, Integer> source : eithers(left, right)) {
            Those<String, Integer, Long> widened = source.toThese().toThose();
            Either<Long, These<String, Integer>> nested = widened.toEither();

            assertEquals(Either.right(source.toThese()), nested);
            assertEquals(widened, Those.fromEither(nested));
        }
    }

    @RepeatedTest(64)
    void wideningKeepsActiveVariantAndPayloadReferences(
            @Given String left, @Given Integer right) {
        Either<String, Integer> onlyLeft = Either.left(left);
        Either<String, Integer> onlyRight = Either.right(right);

        These<String, Integer> leftThese = onlyLeft.toThese();
        These<String, Integer> rightThese = onlyRight.toThese();
        assertInstanceOf(These.This.class, leftThese);
        assertInstanceOf(These.That.class, rightThese);
        assertSame(left, leftThese.fold(Function.identity(),
                value -> fail("wrong variant"), (a, b) -> fail("wrong variant")));
        assertSame(right, rightThese.fold(
                value -> fail("wrong variant"), Function.identity(),
                (a, b) -> fail("wrong variant")));

        Those<String, Integer, Long> leftThose = leftThese.toThose();
        Those<String, Integer, Long> rightThose = rightThese.toThose();
        Those<String, Integer, Long> bothThose = These.<String, Integer>both(left, right).toThose();

        assertInstanceOf(Those.This.class, leftThose);
        assertInstanceOf(Those.That.class, rightThose);
        assertInstanceOf(Those.Both.class, bothThose);

        assertSame(left, leftThose.fold(Function.identity(),
                value -> fail("wrong variant"), (a, b) -> fail("wrong variant"),
                reason -> fail("wrong variant")));
        assertSame(right, rightThose.fold(
                value -> fail("wrong variant"), Function.identity(),
                (a, b) -> fail("wrong variant"), reason -> fail("wrong variant")));
        bothThose.fold(
                value -> fail("wrong variant"), value -> fail("wrong variant"),
                (a, b) -> {
                    assertSame(left, a);
                    assertSame(right, b);
                    return null;
                }, reason -> fail("wrong variant"));
    }

    @Test
    void nullablePayloadTypesArePreservedAlongBothEmbeddings() {
        Either<@Nullable String, @Nullable Integer> left = Either.left(null);
        Either<@Nullable String, @Nullable Integer> right = Either.right(null);
        These<@Nullable String, @Nullable Integer> both = These.both(null, null);

        assertEquals(These.left(null), left.toThese());
        assertEquals(These.right(null), right.toThese());

        Those<@Nullable String, @Nullable Integer, @Nullable Long> leftThose =
                left.toThese().toThose();
        Those<@Nullable String, @Nullable Integer, @Nullable Long> rightThose =
                right.toThese().toThose();
        Those<@Nullable String, @Nullable Integer, @Nullable Long> bothThose =
                both.toThose();

        assertEquals(Those.left(null), leftThose);
        assertEquals(Those.right(null), rightThose);
        assertEquals(Those.both(null, null), bothThose);
        assertEquals(leftThose, Those.fromEither(leftThose.toEither()));
        assertEquals(rightThose, Those.fromEither(rightThose.toEither()));
        assertEquals(bothThose, Those.fromEither(bothThose.toEither()));
    }

    @Test
    void leftEvidenceMustNotBeConfusedWithTheNeitherExplanation() {
        Either<String, Integer> left = Either.left("observation A");
        Those<String, Integer, String> widened = left.toThese().toThose();
        Those<String, Integer, String> absent = Those.neither("nothing observed");

        assertInstanceOf(Those.This.class, widened);
        assertInstanceOf(Those.Neither.class, absent);
        assertNotEquals(absent, widened);
        assertEquals(Either.right(These.left("observation A")), widened.toEither());
        assertEquals(Either.left("nothing observed"), absent.toEither());
    }

    @Test
    void conversionsAreStrictButNeverInvokeApplicationCallbacks() {
        var calls = new AtomicInteger();
        These<String, Integer> source = Either.<String, Integer>right(42)
                .map(n -> {
                    calls.incrementAndGet();
                    return n;
                }).toThese();
        assertEquals(1, calls.get());

        Those<String, Integer, Long> widened = source.toThose();
        assertEquals(1, calls.get());
        assertEquals(Those.right(42), widened);
    }
}
