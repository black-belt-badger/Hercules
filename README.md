# Hercules

**Hercules** is a small, public functional programming library for modern Java.

> **Working mission — open for discussion:** Bring expressive, lawful functional abstractions to ordinary Java applications, one capability at a time. Using Hercules should never require an application to adopt a whole framework, rewrite its architecture, or become dependent on Hercules throughout its codebase.

## Design principles

- **Opt in, don't buy in.** Each abstraction should be useful independently and interoperate with ordinary Java code.
- **Standard Java at the boundary.** Prefer JDK `Function`, `BiFunction`, `List`, `Map`, `Optional`, `CompletionStage`, `Flow`, records and sealed types where they suffice.
- **Minimal implementation dependencies.** Guava immutable collections and Lombok are acceptable foundations. Do not require clients to use Guava types or install Lombok simply to consume the library.
- **Prefer algebraic laws to ceremony.** Add examples, tests and clear semantics. Do not manufacture a full typeclass hierarchy or duplicate JDK reduction APIs without a practical reason.
- **Functional, not dogmatic.** Explicit state, immutable data and composition are goals; existing Java programs remain first-class citizens.

## Areas under consideration

Higher-arity functions (`F3`–`F10`); `Reader`, `Writer`, and `State`; trampolines; `Either`, `These`/`Ior`, and typed `Those`; `Alternative`, Applicative, Monad and Comonad; functional lenses; persistent measured finger trees; Free Monads; type algebra, bounded and modular types.

All are **proposals** until implemented and validated. Inclusion in this list does not imply a committed public API.

## Status

Repository initialization and design discussion. No public release yet.

## License

License selection is pending. Public repository visibility alone is not a software license.
