#!/usr/bin/env python3
"""Architecture boundary gate for the service layer."""

from __future__ import annotations

from pathlib import Path
import sys


REPO_ROOT = Path(__file__).resolve().parents[1]
SERVICE_ROOT = REPO_ROOT / "backend/src/main/java/com/example/demo/service"
FORBIDDEN_IMPORT = "import com.example.demo.infrastructure."


def main() -> int:
    violations: list[str] = []
    if not SERVICE_ROOT.exists():
        print(f"Service root not found: {SERVICE_ROOT.relative_to(REPO_ROOT)}", file=sys.stderr)
        return 1

    for path in sorted(SERVICE_ROOT.rglob("*.java")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if line.strip().startswith(FORBIDDEN_IMPORT):
                relative_path = path.relative_to(REPO_ROOT)
                violations.append(f"{relative_path}:{line_number}: {line.strip()}")

    if violations:
        print("Architecture boundary gate failed:", file=sys.stderr)
        for violation in violations:
            print(f"  {violation}", file=sys.stderr)
        return 1

    print("Architecture boundary gate passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
