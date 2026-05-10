#!/usr/bin/env python3
"""Frontend import boundary gate for components and App."""

from __future__ import annotations

from pathlib import Path
import re
import sys


REPO_ROOT = Path(__file__).resolve().parents[1]
FRONTEND_SRC = REPO_ROOT / "frontend/src"
API_CLIENT_IMPORT_PATTERN = re.compile(r"\bfrom\s+[\"'](?P<module>(?:@|\.{1,2})/api/client)[\"']")
API_CLIENT_CALL_PATTERN = re.compile(r"\bapiClient\.[A-Za-z_$][\w$]*\s*\(")


def line_number(source: str, index: int) -> int:
  return source.count("\n", 0, index) + 1


def production_targets() -> list[Path]:
  targets = [FRONTEND_SRC / "App.tsx"]
  components_root = FRONTEND_SRC / "components"
  if components_root.exists():
    targets.extend(
      path
      for path in components_root.rglob("*")
      if path.is_file()
      and path.suffix in {".ts", ".tsx"}
      and ".test." not in path.name
      and ".spec." not in path.name
    )
  return sorted(targets)


def collect_violations() -> list[str]:
  violations: list[str] = []
  for path in production_targets():
    if not path.exists():
      continue
    source = path.read_text(encoding="utf-8")
    relative = path.relative_to(REPO_ROOT).as_posix()
    for match in API_CLIENT_IMPORT_PATTERN.finditer(source):
      violations.append(
        f"{relative}:{line_number(source, match.start())}: import {match.group('module')} through hooks/query layer"
      )
    for match in API_CLIENT_CALL_PATTERN.finditer(source):
      violations.append(
        f"{relative}:{line_number(source, match.start())}: move apiClient network call into frontend/src/hooks"
      )
  return violations


def main() -> int:
  violations = collect_violations()
  if violations:
    print("Frontend boundary gate failed:", file=sys.stderr)
    for violation in violations:
      print(f"  {violation}", file=sys.stderr)
    return 1
  print("Frontend boundary gate passed.")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
