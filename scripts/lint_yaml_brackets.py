#!/usr/bin/env python3
"""Static bracket-balance linter for YAML/markdown files.

Treats these contexts as opaque so template `${...}` and quoted strings
don't inflate the bracket count:
  - line comments //
  - block comments /* ... */
  - double-quoted strings "...(escapes)"
  - single-quoted strings '...'
  - template expressions ${...}
"""
from __future__ import annotations

import pathlib
import sys

PAIRS = {"(": ")", "[": "]", "{": "}"}
STARTS = set(PAIRS) | set(PAIRS.values())


def bracket_balance(text: str) -> tuple[int, list[str]]:
    """Return (diff, errors) where diff is count unclosed.

    Errors list contains human-readable notes only; the diff is the source
    of truth for pass/fail.
    """
    diff = 0
    line = 1
    col = 0
    state = "text"
    text = text
    out_len = len(text)
    errors: list[str] = []

    while col < out_len:
        ch = text[col]

        if state == "comment-line":
            if ch == "\n":
                state = "text"
        elif state == "comment-block":
            if ch == "*" and col + 1 < out_len and text[col + 1] == "/":
                state = "text"
                col += 1
        elif state == "dq":
            if ch == "\\":
                col += 1  # skip escaped char
            elif ch == '"':
                state = "text"
        elif state == "sq":
            if ch == "\\":
                col += 1
            elif ch == "'":
                state = "text"
        elif state == "template":
            if ch == "}":
                state = "text"
        else:  # text
            if ch == "/" and col + 1 < out_len and text[col + 1] == "/":
                state = "comment-line"
            elif ch == "/" and col + 1 < out_len and text[col + 1] == "*":
                state = "comment-block"
            elif ch == '"':
                state = "dq"
            elif ch == "'":
                state = "sq"
            elif ch == "$" and col + 1 < out_len and text[col + 1] == "{":
                state = "template"

        if state not in ("comment-line", "comment-block", "template"):
            if ch in PAIRS:
                diff += 1
            elif ch in PAIRS.values():
                diff -= 1
            if diff < 0:
                errors.append(f"line {line}: unexpected close {ch!r} (diff {diff})")

        if ch == "\n":
            line += 1
        col += 1

    if state != "text":
        errors.append(f"end of file: unterminated context started at line 1")

    if diff > 0:
        errors.append(f"end of file: {diff} unclosed bracket(s)")

    return diff, errors


def main(argv: list[str]) -> int:
    files = argv[1:] if len(argv) > 1 else [".github/workflows/build-apk.yml"]
    root = pathlib.Path(".")
    failed = False
    for rel in files:
        path = root / rel
        if not path.is_file():
            print(f"SKIP (missing): {rel}")
            continue
        text = path.read_text(encoding="utf-8")
        diff, errors = bracket_balance(text)
        status = "balanced" if diff == 0 else f"UNBALANCED by {diff}"
        print(f"{rel}: {status}")
        for e in errors:
            print(f"  - {rel}: {e}")
        if diff != 0 or errors:
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
