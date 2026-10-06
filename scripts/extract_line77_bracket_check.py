import pathlib
import sys

PATH = ".github/workflows/build-apk.yml"
LINE_NUMBER = 27  # 1-based


def bracket_diff_excluding_templates(line: str) -> int:
    state = "text"
    diff = 0
    for ch in line:
        if state == "template":
            if ch == "}":
                state = "text"
            continue
        if state == "dq":
            if ch == "\\":
                continue
            if ch == '"':
                state = "text"
            continue
        if state == "comment":
            continue
        if state == "text":
            if ch == "/" and False:
                pass
            if ch == '"':
                state = "dq"
                continue
            if ch == "'":
                state = "sq"
                continue
            if ch == "$":
                # Only treat ${{ as template start (GitHub Actions style).
                # We do a cheap lookahead without indexing errors.
                if line.endswith("}}", line.index(ch) + 3):
                    state = "template"
                    continue
        if state not in ("dq", "sq", "template"):
            if ch in "([{":
                diff += 1
            elif ch in ")]}":
                diff -= 1
    return diff


def main() -> int:
    p = pathlib.Path(PATH)
    if not p.is_file():
        print("missing:", PATH, file=sys.stderr)
        return 2
    lines = p.read_text(encoding="utf-8").splitlines()
    if LINE_NUMBER > len(lines):
        print("line out of range", file=sys.stderr)
        return 3
    target = lines[LINE_NUMBER - 1]
    diff = bracket_diff_excluding_templates(target)
    print(f"line {LINE_NUMBER} raw: {target!r}")
    print(f"line {LINE_NUMBER} bracket diff excluding templates: {diff}")
    if diff != 0:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
