#!/usr/bin/env python3
"""Generate law and behavior tests for all F3–F10 arities."""
from pathlib import Path

PKG = "bbb.hercules"
DEST = Path(__file__).resolve().parents[1] / "src/test/java" / PKG.replace(".", "/")
LETTERS = "ABCDEFGHIJ"

def test_for(n):
    names = list(LETTERS[:n].lower())
    values = ", ".join(names)
    args = ", ".join(["Integer"] * (n + 1))
    typ = f"F{n}<{args}>"
    oracle = " + ".join(f"{i+1} * {x}" for i,x in enumerate(names))
    bind1 = f"v + 3 * {names[0]} - {names[-1]}"
    bind2 = f"v * 2 - {names[0]} + 2 * {names[-1]}"
    s = []
    s += [f"    @Test", f"    void f{n}LawsAndArguments() {{",
          f"        {typ} base = ({values}) -> {oracle};",
          f"        Function<Integer, {typ}> bind1 = v -> ({values}) -> {bind1};",
          f"        Function<Integer, {typ}> bind2 = v -> ({values}) -> {bind2};",
          f"        {typ} constant = ({values}) -> 17;",
          f"        var random = new Random({n} * 19L);",
          f"        for (int trial = 0; trial < 50; trial++) {{"]
    for x in names:
        s.append(f"            int {x} = random.nextInt(31) - 15;")
    s += [f"            int expected = {oracle};",
          f"            assertEquals(expected, base.apply({values}));",
          f"            assertEquals(expected, base.map(Function.identity()).apply({values}));",
          f"            assertEquals(base.map(x -> 2 * x + 1).map(x -> x - 5).apply({values}),",
          f"                    base.map(x -> (2 * x + 1) - 5).apply({values}));",
          f"            assertEquals(bind1.apply(17).apply({values}),",
          f"                    constant.flatMap(bind1).apply({values}));",
          f"            assertEquals(expected, base.flatMap(v -> ({', '.join('x'+str(i) for i in range(1,n+1))}) -> v).apply({values}));",
          f"            assertEquals(base.flatMap(bind1).flatMap(bind2).apply({values}),",
          f"                    base.flatMap(v -> bind1.apply(v).flatMap(bind2)).apply({values}));",
          f"            assertEquals(expected, base.curried(){''.join(f'.apply({x})' for x in names)});",
          f"            assertEquals(expected, F{n}.uncurry(base.curried()).apply({values}));"]
    for k in range(1,n):
        s.append(f"            assertEquals(expected, base.partial({', '.join(names[:k])}).apply({', '.join(names[k:])}));")
    for i, x in enumerate(names):
        transformed = names.copy()
        transformed[i] = f"({x} + 11)"
        s.append(f"            assertEquals(base.apply({', '.join(transformed)}),")
        s.append(f"                    base.compose{i+1}((Integer v) -> v + 11).apply({values}));")
    s += [f"            assertEquals(base.compose1((Integer v) -> 2 * v).compose1((Integer v) -> v + 3).apply({values}),",
          f"                    base.compose1((Integer v) -> 2 * (v + 3)).apply({values}));",
          "        }", "    }", "",
          "    @Test", f"    void f{n}IsLazyAndEvaluatesOnce() {{",
          "        var sourceCalls = new AtomicInteger();",
          "        var mapperCalls = new AtomicInteger();",
          "        var bindCalls = new AtomicInteger();",
          "        var continuationCalls = new AtomicInteger();",
          f"        {typ} source = ({values}) -> {{ sourceCalls.incrementAndGet(); return {oracle}; }};",
          f"        var partial = source.partial({', '.join(str(i) for i in range(1,n))});",
          "        assertEquals(0, sourceCalls.get());",
          f"        partial.apply({n});",
          "        assertEquals(1, sourceCalls.get());",
          f"        var mapped = source.map(v -> {{ mapperCalls.incrementAndGet(); return v + 1; }});",
          "        assertEquals(0, mapperCalls.get());",
          f"        mapped.apply({', '.join(str(i) for i in range(1,n+1))});",
          "        assertEquals(1, mapperCalls.get());",
          "        assertEquals(2, sourceCalls.get());",
          f"        var bound = source.flatMap(v -> {{ bindCalls.incrementAndGet();",
          f"            return ({values}) -> {{ continuationCalls.incrementAndGet(); return v + 1; }};",
          "        });",
          "        assertEquals(0, bindCalls.get());",
          f"        bound.apply({', '.join(str(i) for i in range(1,n+1))});",
          "        assertEquals(1, bindCalls.get());",
          "        assertEquals(1, continuationCalls.get());",
          "        assertEquals(3, sourceCalls.get());",
          "        var curried = source.curried();",
          "        assertEquals(3, sourceCalls.get());",
          f"        curried{''.join(f'.apply({i})' for i in range(1,n+1))};",
          "        assertEquals(4, sourceCalls.get());",
          "    }", "",
          "    @Test", f"    void f{n}NullCallbacksAndExceptions() {{",
          f"        {typ} base = ({values}) -> 1;",
          "        assertThrows(NullPointerException.class, () -> base.map(null));",
          "        assertThrows(NullPointerException.class, () -> base.flatMap(null));",
          "        assertThrows(NullPointerException.class, () -> base.compose1(null));",
          f"        assertThrows(NullPointerException.class, () -> F{n}.uncurry(null));",
          f"        assertThrows(NullPointerException.class, () -> base.flatMap(v -> null).apply({', '.join(str(i) for i in range(1,n+1))}));",
          "        var exception = new IllegalStateException(\"expected\");",
          f"        {typ} throwing = ({values}) -> {{ throw exception; }};",
          f"        assertSame(exception, assertThrows(IllegalStateException.class, () -> throwing.apply({', '.join(str(i) for i in range(1,n+1))})));",
          f"        assertSame(exception, assertThrows(IllegalStateException.class, () -> throwing.partial(1).apply({', '.join(str(i) for i in range(2,n+1))})));",
          f"        assertSame(exception, assertThrows(IllegalStateException.class, () -> throwing.map(v -> v).apply({', '.join(str(i) for i in range(1,n+1))})));",
          f"        assertSame(exception, assertThrows(IllegalStateException.class, () -> throwing.compose1((Integer v) -> v).apply({', '.join(str(i) for i in range(1,n+1))})));",
          "    }", ""]
    return "\n".join(s)

head = f"""// Generated by scripts/generate_function_tests.py; edit the generator, not this file.
package {PKG};

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HigherArityFunctionsTest {{

    @Test
    void explicitlyNullableArgumentsArePreservedDuringPartialApplication() {{
        F3<@Nullable String, Integer, Integer, String> format =
                (prefix, a, b) -> String.valueOf(prefix) + ":" + (a + b);
        assertEquals("null:5", format.partial(null).apply(2, 3));
    }}

"""
DEST.mkdir(parents=True, exist_ok=True)
(DEST / "HigherArityFunctionsTest.java").write_text(
    head + "\n".join(test_for(n) for n in range(3,11)) + "}\n", encoding="utf8"
)
print("Generated HigherArityFunctionsTest.java")
