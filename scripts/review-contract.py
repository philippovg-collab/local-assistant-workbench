#!/usr/bin/env python3
"""Validate PR review metadata and anti-sprawl declarations."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


REQUIRED_SECTIONS = (
    "risk / review focus",
    "tests / evidence",
    "anti-sprawl",
)

SPRAWL_KEYWORDS = re.compile(
    r"\b(legacy|fallback|rollout|bestEffort|compatibility)\b",
    re.IGNORECASE,
)

PRODUCTION_PATHS = (
    "backend/src/main/java/",
    "frontend/src/",
)

TEST_PATH_MARKERS = (
    ".test.",
    "/test/",
    "backend/src/test/",
)

HIGH_RISK_PATH_PATTERNS = (
    re.compile(r"backend/src/main/resources/db/migration/"),
    re.compile(r"backend/src/main/java/.*/Security", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/Material", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/Chat", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/KnowledgePreset", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/Elasticsearch", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/Retrieval", re.IGNORECASE),
    re.compile(r"backend/src/main/java/.*/Ocr", re.IGNORECASE),
    re.compile(r"docker-compose\.yml$"),
)


@dataclass(frozen=True)
class SprawlHit:
    path: str
    text: str


def emit_error(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::error::{message}", file=sys.stderr)
    else:
        print(f"ERROR: {message}", file=sys.stderr)


def emit_notice(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS"):
        print(f"::notice::{message}")
    else:
        print(message)


def normalize_heading(heading: str) -> str:
    return re.sub(r"\s+", " ", heading.strip().lower())


def extract_sections(body: str) -> dict[str, str]:
    matches = list(re.finditer(r"(?m)^#{1,3}\s+(.+?)\s*$", body))
    sections: dict[str, str] = {}
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(body)
        sections[normalize_heading(match.group(1))] = body[start:end].strip()
    return sections


def meaningful_lines(section: str) -> list[str]:
    lines: list[str] = []
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("<!--") or line.endswith("-->"):
            continue
        if "<!--" in line:
            line = line.split("<!--", 1)[0].strip()
        if not line:
            continue
        if line in {"-", "_", "n/a", "N/A"}:
            continue
        lines.append(line)
    return lines


def checked(body: str, text_fragment: str) -> bool:
    pattern = re.compile(
        r"(?im)^\s*-\s*\[[xX]\]\s+.*" + re.escape(text_fragment),
    )
    return bool(pattern.search(body))


def any_checked(body: str) -> bool:
    return bool(re.search(r"(?im)^\s*-\s*\[[xX]\]\s+", body))


def label_value(body: str, label: str) -> str:
    pattern = re.compile(rf"(?im)^\s*-\s*{re.escape(label)}:\s*(.*)$")
    match = pattern.search(body)
    if not match:
        return ""
    value = match.group(1).strip()
    if "<!--" in value:
        value = value.split("<!--", 1)[0].strip()
    return value


def read_body(args: argparse.Namespace) -> str | None:
    if args.body_file:
        return Path(args.body_file).read_text(encoding="utf-8")
    if args.event_path:
        event = json.loads(Path(args.event_path).read_text(encoding="utf-8"))
        pull_request = event.get("pull_request") or {}
        return pull_request.get("body") or ""
    env_event_path = os.environ.get("GITHUB_EVENT_PATH")
    if env_event_path and Path(env_event_path).exists():
        event = json.loads(Path(env_event_path).read_text(encoding="utf-8"))
        pull_request = event.get("pull_request") or {}
        return pull_request.get("body") or ""
    return None


def run_git_diff(base_ref: str | None) -> str:
    candidates = []
    if base_ref:
        candidates.append(base_ref)
    candidates.extend(["origin/main", "origin/master", "main", "master"])

    for candidate in candidates:
        merge_base = subprocess.run(
            ["git", "merge-base", candidate, "HEAD"],
            check=False,
            capture_output=True,
            text=True,
        )
        if merge_base.returncode != 0:
            continue
        base = merge_base.stdout.strip()
        diff = subprocess.run(
            ["git", "diff", "--unified=0", "--no-ext-diff", base],
            check=True,
            capture_output=True,
            text=True,
        )
        return diff.stdout + untracked_file_diff()

    emit_notice("No base ref found for local diff; anti-sprawl diff scan skipped.")
    return ""


def untracked_file_diff() -> str:
    result = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        check=True,
        capture_output=True,
        text=True,
    )
    chunks: list[str] = []
    for path in result.stdout.splitlines():
        if not is_production_path(path):
            continue
        file_path = Path(path)
        if not file_path.is_file():
            continue
        content = file_path.read_text(encoding="utf-8", errors="replace")
        lines = content.splitlines()
        chunks.append(f"diff --git a/{path} b/{path}\n--- /dev/null\n+++ b/{path}\n@@ -0,0 +1,{len(lines)} @@\n")
        chunks.extend(f"+{line}\n" for line in lines)
    return "".join(chunks)


def read_diff(args: argparse.Namespace) -> str:
    if args.diff_file:
        return Path(args.diff_file).read_text(encoding="utf-8")
    return run_git_diff(args.base_ref)


def is_production_path(path: str) -> bool:
    if not path.startswith(PRODUCTION_PATHS):
        return False
    return not any(marker in path for marker in TEST_PATH_MARKERS)


def changed_files_from_diff(diff: str) -> set[str]:
    files: set[str] = set()
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            files.add(line.removeprefix("+++ b/"))
    return files


def sprawl_hits_from_diff(diff: str) -> list[SprawlHit]:
    hits: list[SprawlHit] = []
    current_path = ""
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current_path = line.removeprefix("+++ b/")
            continue
        if not current_path or not is_production_path(current_path):
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue
        text = line[1:].strip()
        if SPRAWL_KEYWORDS.search(text):
            hits.append(SprawlHit(current_path, text[:160]))
    return hits


def high_risk_files(changed_files: set[str]) -> list[str]:
    matched = []
    for path in sorted(changed_files):
        if any(pattern.search(path) for pattern in HIGH_RISK_PATH_PATTERNS):
            matched.append(path)
    return matched


def validate_body(body: str, sprawl_hits: list[SprawlHit], high_risk: list[str]) -> list[str]:
    errors: list[str] = []
    sections = extract_sections(body)

    for section_name in REQUIRED_SECTIONS:
        if section_name not in sections:
            errors.append(f"PR body is missing required section: {section_name}")
        elif not meaningful_lines(sections[section_name]):
            errors.append(f"PR body section is empty: {section_name}")

    risk = sections.get("risk / review focus", "")
    for label in ("Risk level", "Touched boundaries", "Review focus"):
        if not label_value(risk, label):
            errors.append(f"Risk / Review Focus is missing '{label}'.")

    tests = sections.get("tests / evidence", "")
    test_evidence = label_value(tests, "Evidence / skipped tests")
    if not any_checked(tests) and not test_evidence:
        errors.append("Tests / Evidence must check at least one command or explain skipped tests.")

    anti_sprawl = sections.get("anti-sprawl", "")
    no_sprawl_checked = checked(anti_sprawl, "No new or changed")
    declared_sprawl_checked = checked(anti_sprawl, "This PR adds or changes")

    if no_sprawl_checked == declared_sprawl_checked:
        errors.append("Anti-Sprawl must check exactly one declaration checkbox.")

    if sprawl_hits and not declared_sprawl_checked:
        examples = "; ".join(f"{hit.path}: {hit.text}" for hit in sprawl_hits[:3])
        errors.append(
            "Production diff adds legacy/fallback/rollout/bestEffort/compatibility language, "
            f"but the PR does not declare an anti-sprawl path. Examples: {examples}"
        )

    if declared_sprawl_checked:
        for label in ("Reason", "Owner scenario", "Test coverage", "Removal criterion"):
            if not label_value(anti_sprawl, label):
                errors.append(f"Anti-Sprawl declaration is missing '{label}'.")

    verify_checked = bool(re.search(r"(?im)^\s*-\s*\[[xX]\]\s+`mvn -B verify`", tests))
    verify_explained = "mvn -B verify" in test_evidence
    if high_risk and not verify_checked and not verify_explained:
        examples = ", ".join(high_risk[:3])
        errors.append(
            "High-risk backend/deploy paths changed but Tests / Evidence does not check or explain "
            f"`mvn -B verify`. Examples: {examples}"
        )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event-path", help="GitHub event payload path.")
    parser.add_argument("--body-file", help="Markdown file containing a PR body.")
    parser.add_argument("--diff-file", help="Unified diff file to scan.")
    parser.add_argument("--base-ref", help="Base ref for local diff scanning.")
    parser.add_argument("--skip-body", action="store_true", help="Skip PR body validation.")
    args = parser.parse_args()

    diff = read_diff(args)
    sprawl_hits = sprawl_hits_from_diff(diff)
    high_risk = high_risk_files(changed_files_from_diff(diff))

    errors: list[str] = []
    body_validated = False
    if args.skip_body:
        emit_notice("PR body validation skipped.")
    else:
        body = read_body(args)
        if body is None:
            emit_notice("No PR body found; PR body validation skipped.")
        else:
            body_validated = True
            errors.extend(validate_body(body, sprawl_hits, high_risk))

    if errors:
        for error in errors:
            emit_error(error)
        return 1

    if sprawl_hits:
        emit_notice(f"Anti-sprawl declaration validated for {len(sprawl_hits)} production diff hit(s).")
    else:
        emit_notice("No new production anti-sprawl keywords detected.")
    if high_risk:
        if body_validated:
            emit_notice(f"High-risk path evidence validated for {len(high_risk)} changed file(s).")
        else:
            emit_notice(f"High-risk path(s) detected in local diff: {len(high_risk)} changed file(s).")
    emit_notice("Review contract passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
