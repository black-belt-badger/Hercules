# Higher-arity functions — F3–F10

Hercules provides independent Java 17 functional interfaces for three to ten arguments. Each has one abstract `apply` method and accepts ordinary lambdas and method references. The API takes inspiration from Vavr and FunctionalJava, but uses compact names and JDK types wherever possible.

## F3: mapping and Reader-style composition

```java
F3<Integer, Integer, Integer, Integer> add =
        (a, b, c) -> a + b + c;

var formatted = add.map(Object::toString);
String value = formatted.apply(2, 3, 4); // "9"

var weighted = add.flatMap(sum -> (a, b, c) -> sum * c);
int result = weighted.apply(2, 3, 4); // 36
```

The `flatMap` binder receives the original result and must return another function of the **same arguments**. Both functions see the same environment (Reader semantics).

## F5: partial application and argument composition

```java
F5<Integer, Integer, Integer, Integer, Integer, Integer> sum =
        (a, b, c, d, e) -> a + b + c + d + e;

F3<Integer, Integer, Integer, Integer> add30 = sum.partial(10, 20);
int answer = add30.apply(1, 2, 3); // 36

var parseFirst = sum.compose1(Integer::parseInt);
int other = parseFirst.apply("10", 20, 1, 2, 3); // 36

var curried = sum.curried();
int again = curried.apply(1).apply(2).apply(3).apply(4).apply(5); // 15
var restored = F5.uncurry(curried);
```

`partial` binds a prefix, preserving argument order. A result with one
argument uses JDK `Function`, with two uses JDK `BiFunction`, and with
three or more uses Hercules `F3`–`F9`. `compose1`–`composeN` transform
the specified input (1-based); `map` transforms the result.

## F10: JDK interoperability

```java
F10<Integer, Integer, Integer, Integer, Integer,
    Integer, Integer, Integer, Integer, Integer, Integer> ten =
        (a, b, c, d, e, f, g, h, i, j) ->
                a + b + c + d + e + f + g + h + i + j;

Function<Integer, Integer> finalArgument =
        ten.partial(1, 2, 3, 4, 5, 6, 7, 8, 9);
int total = finalArgument.apply(10); // 55
```

## Semantics and contracts

- Building adapters, currying and partial application never invokes the function.
- Each evaluated adapter calls its underlying function once. Mapping calls the mapper once; binding invokes the binder and selected function once each. No memoization.
- `map`, `flatMap`, `composeN` and `uncurry` reject null callbacks immediately. A `flatMap` binder returning a null function fails on invocation.
- `@NullMarked` applies by default. Express intentional nullable arguments and returns using JSpecify `@Nullable` type uses; no blanket runtime checks of argument values are added.
- User exceptions propagate unchanged; order is left-to-right for arguments, then the original function, then the mapper/binder/selected function.
- The algebraic laws hold for pure functions. Deeply nested adapter chains have no stack-safety guarantee.
- Tuple adapters, reverse ordering, memoization and non-prefix partial application are intentionally deferred.

## Generation and verification

```bash
python3 scripts/generate_functions.py
python3 scripts/generate_function_tests.py
./mvnw -B -ntp verify
```

Generated Java source and tests are checked in; Python is **not** required to compile or consume the library. The tests check mapping and Reader laws, each argument position, all prefix lengths, laziness, evaluation count, null callbacks and exception propagation across all eight arities.
