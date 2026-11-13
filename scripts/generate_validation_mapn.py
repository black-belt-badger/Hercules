#!/usr/bin/env python3
"""Generate the public map3..map10 overloads inside ValidationApplicative.

Do not edit the marked section by hand; these methods rely only on
ValidationApplicative.pure/ap and Hercules F3..F10.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "src/main/java/bbb/hercules/ValidationApplicative.java"
START = "    // BEGIN generated map3-map10 (scripts/generate_validation_mapn.py)"
END = "    // END generated map3-map10"
LETTERS = "ABCDFGHIJK"  # E is the enclosing error type; never shadow it


def method(arity):
    types = list(LETTERS[:arity])
    names = [t.lower() for t in types]
    generics = ", ".join(f"{type_name} extends @Nullable Object" for type_name in [*types, "R"])
    param_types = ", ".join(f"? super {t}" for t in types)
    function_type = f"F{arity}<{param_types}, ? extends R>"
    lines = [
        "    /**",
        f"     * Combine {arity} independent validations, accumulating all errors in input order.",
        "     *",
        "     * <p>Input validations are already evaluated. Errors combine left to right,",
        "     * with no identity or commutativity requirement. The mapper runs exactly",
        "     * once only when all inputs are Valid; errors from callbacks propagate.",
        "     *",
        *[f"     * @param <{t}> successful value type of argument {i + 1}"
          for i, t in enumerate(types)],
        "     * @param <R> mapped result type",
        *[f"     * @param {name} independent validation {i + 1}"
          for i, name in enumerate(names)],
        "     * @param mapper function applied when all arguments are valid",
        "     * @return a mapped successful value, or accumulated errors",
        "     */",
        f"    public <{generics}> Validation<E, R> map{arity}(",
        *[f"            @NonNull Validation<E, {t}> {name},"
          for t, name in zip(types, names)],
        f"            @NonNull {function_type} mapper) {{",
    ]
    expression = "pure(mapper.curried())"
    for name in names:
        expression = f"ap({expression}, {name})"
    lines += [
        f"        return {expression};",
        "    }",
    ]
    return "\n".join(lines)


def generate():
    original = FILE.read_text(encoding="utf8")
    generated = START + "\n\n" + "\n\n".join(method(n) for n in range(3, 11)) + "\n\n" + END
    if START in original or END in original:
        if original.count(START) != 1 or original.count(END) != 1:
            raise ValueError("unbalanced generated section markers")
        start = original.index(START)
        end = original.index(END) + len(END)
        updated = original[:start] + generated + original[end:]
    else:
        assert original.rstrip().endswith("}"), "unexpected Java file layout"
        updated = original.rstrip()[:-1].rstrip() + "\n\n" + generated + "\n}\n"
    FILE.write_text(updated, encoding="utf8")
    print("Generated ValidationApplicative.map3 through map10")


if __name__ == "__main__":
    generate()
