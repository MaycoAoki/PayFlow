---
name: tdd-reset
description: Use this skill when the user asks to "reset TDD", "clear TDD state", "remove TDD markers", "start TDD fresh", "tdd-reset", or wants to manually clear all GREEN phase markers to re-enforce the TDD Red-Green-Refactor cycle from scratch.
version: 1.1.0
---

# TDD Reset

## Overview

Clears all TDD GREEN phase markers for the current project, forcing the TDD workflow back to the RED phase. Use this when you want to start a fresh TDD cycle without waiting for a new session.

## When to Use

- The GREEN phase is active but you want to enforce RED-first again
- State feels stale or incorrect
- Starting a new unit of work within the same session

## Instructions

When this skill is activated, run the following bash command:

```bash
root="$(git rev-parse --show-toplevel 2>/dev/null || echo "${CLAUDE_PROJECT_DIR:-.}")"; removed=0; for f in "$root"/.tdd-state*; do [ -f "$f" ] || continue; rm -f "$f"; removed=$((removed + 1)); done; if [ $removed -gt 0 ]; then echo "TDD Reset: Cleared $removed GREEN phase marker(s). Now in RED phase."; else echo "TDD Reset: No active markers found. Already in RED phase."; fi
```

After executing, inform the user:
- All GREEN phase markers have been cleared
- Source code edits are now blocked until a failing test is run
- The normal Red-Green-Refactor cycle is re-enforced
