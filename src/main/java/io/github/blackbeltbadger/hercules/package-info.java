/**
 * Hercules: small, independently usable functional programming abstractions.
 *
 * <p>Public APIs are non-null by default. Use JSpecify {@code @Nullable}
 * on intentional nullable type uses; this does not imply the absence
 * of an algebraic-data-type variant. Required callbacks and container
 * references use Lombok {@code @NonNull} for fail-fast runtime checks.
 * See {@code docs/nullness.md} for the complete contract.
 */
@org.jspecify.annotations.NullMarked
package io.github.blackbeltbadger.hercules;
