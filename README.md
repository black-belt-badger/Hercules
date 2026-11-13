# Hercules

**Hercules** is a small, public functional programming library for modern Java.

> **Working mission — open for discussion:** Bring expressive, lawful functional abstractions to ordinary Java applications, one capability at a time. Using Hercules should never require an application to adopt a whole framework, rewrite its architecture, or become dependent on Hercules throughout its codebase.

## Design principles

- **Opt in, don't buy in.** Each abstraction should be useful independently and interoperate with ordinary Java code.
- **Standard Java at the boundary.** Prefer JDK `Function`, `BiFunction`, `List`, `Map`, `Optional`, `CompletionStage`, `Flow`, records and sealed types where they suffice.
- **Minimal implementation dependencies.** Guava immutable collections, Lombok and JSpecify are our approved foundations. Do not require clients to use Guava types or install Lombok simply to consume the library. JSpecify annotations describe nullness contracts without requiring callers to adopt a framework.
- **Prefer algebraic laws to ceremony.** Add examples, tests and clear semantics. Do not manufacture a full typeclass hierarchy or duplicate JDK reduction APIs without a practical reason.
- **Functional, not dogmatic.** Explicit state, immutable data and composition are goals; existing Java programs remain first-class citizens.

## Areas under consideration

Higher-arity functions (`F3`–`F10`); `Reader`, `Writer`, and `State`; trampolines; `Either`, `These`/`Ior`, and typed `Those`; `Alternative`, Applicative, Monad and Comonad; functional lenses; persistent measured finger trees; Free Monads; type algebra, bounded and modular types.

All are **proposals** until implemented and validated. Inclusion in this list does not imply a committed public API.

## Java and Maven

**Java 17 is the minimum supported language/runtime level.** Records and sealed types are available, and we verify the library against Java 17, 21 and 25.

The Maven coordinates reserved for development are:

```text
io.github.blackbeltbadger:hercules:0.1.0-SNAPSHOT
```

The project is a **library JAR**, not an executable application. Its production dependencies are Guava, JSpecify annotations, and compile-time-only Lombok. Tests use JUnit 5.

```bash
./mvnw -B -ntp verify
```

JSpecify `@NullMarked` is enabled for the initial package. JSpecify annotations **document contracts**; they do not by themselves enforce nullness without a compatible static analyzer. Lombok is configured as an annotation processor for building the library; downstream applications do not need the Lombok processor merely to use the compiled JAR.

## Status

Scaffold initialized. No functional abstractions or published release yet.

## License

License selection is pending. Public repository visibility alone is not a software license.
