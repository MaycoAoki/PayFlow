---
name: tdd-workflow
description: This skill should be used when the user asks to "implement a feature", "fix a bug", "add functionality", "create an endpoint", "modify behavior", "refactor code", "add a method", "change logic", "build a component", "create a service", "write a function", or any task that involves writing or modifying source code. Enforces the mandatory TDD Red-Green-Refactor cycle before any implementation. This skill is NOT optional — it MUST be activated for ALL code changes.
version: 1.1.0
---

# TDD Workflow

## Overview

Enforce Test-Driven Development for every code change. No source code modification without a failing test first. This applies to any programming language, framework, or project.

A PreToolUse hook blocks Edit/Write on source files without a failing test. When you run a test and it fails, the GREEN phase is enabled automatically by a PostToolUse hook — no manual marker management needed.

**IMPORTANT:** NEVER manually create, touch, or remove the `.tdd-state` marker file. It is managed entirely by hooks. Just run the test — if it fails, source edits will be unlocked automatically.

## The Iron Law

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST.
```

Code written before the test must be deleted. No exceptions. No "reference" keeping. Delete means delete.

## Mandatory Workflow: Red, Green, Refactor

### 1. RED — Write a Failing Test

Before touching any source code:

1. Detect the project's test framework (see `references/test-patterns.md`)
2. Identify the behavior to implement or fix
3. Create or modify a test file following the project's existing conventions
4. Write **one minimal test** that asserts the expected behavior
5. Run the test command — confirm it **FAILS**
6. Verify the failure is correct: the test fails because the feature is missing, NOT because of a typo or syntax error

**Test file placement:** Mirror the project's existing test structure. Check for `test/`, `tests/`, `__tests__/`, `spec/`, or colocated patterns.

**Test command detection:**
- `package.json` with `jest` → `npx jest path/to/test`
- `package.json` with `vitest` → `npx vitest run path/to/test`
- `bun.lockb` exists → `bun test path/to/test`
- `pytest.ini` or `pyproject.toml` → `pytest path/to/test`
- `go.mod` → `go test ./path/to/package`
- `Cargo.toml` → `cargo test test_name`
- `Gemfile` with `rspec` → `bundle exec rspec path/to/spec`

### 2. GREEN — Minimal Implementation

After the test fails, source edits are unlocked automatically (the PostToolUse hook creates `.tdd-state`). Do NOT run `touch` or any manual marker command.

1. Edit the source code file — write the **minimum** code to make the test pass
2. Do NOT add features beyond what the test requires
3. Do NOT refactor yet
4. Run the test command — confirm it **PASSES**
5. Confirm no other tests broke (run full suite if fast enough)

### 3. REFACTOR — Clean Up

Only after the test passes:

1. Improve code quality: names, duplication, structure
2. Run tests after **each** refactoring change — they must stay GREEN
3. Do NOT add new behavior during refactoring

### 4. Repeat

Next behavior → next failing test → next cycle.

## Special Cases

### Bug Fixes

Every bug fix starts with a test that **reproduces** the bug:

1. **RED**: Write a test that fails because the bug exists
2. **GREEN**: Fix the bug — the test passes
3. **REFACTOR**: Clean up

### Refactoring Existing Code

When restructuring without changing behavior:

1. Run existing tests — confirm they **PASS** first
2. Refactor the code
3. Run tests again — they must still pass
4. If behavior needs to change → start a new Red→Green→Refactor cycle

### Multiple Related Changes

When a feature requires changes across multiple files:

1. Write tests for the **first** unit of behavior
2. Red→Green→Refactor for that unit
3. Write tests for the **next** unit
4. Red→Green→Refactor
5. Continue until feature is complete

Do NOT write all tests at once then implement everything.

## Exempt Files

The following files do NOT require TDD (the hook allows them automatically):

- **Test files** themselves (`.spec.*`, `.test.*`, `_test.*`, etc.)
- **Configuration** (`.config.*`, `.env*`, `*.json`, `*.yaml`, `*.toml`, `*.lock`)
- **Documentation** (`*.md`, `*.txt`, `*.rst`)
- **Styles** (`*.css`, `*.scss`, `*.less`)
- **Database** (`*.prisma`, `*.sql`, migrations, seeds)
- **Build/CI** (`Dockerfile`, `docker-compose`, `.github/`, `Makefile`)
- **Generated code** (`generated/`, `dist/`, `build/`)
- **Static assets** (images, fonts, media)
- **Type declarations** (`*.d.ts`)

See `references/exempt-files.md` for the complete list.

## Test Quality

- One assertion concept per test
- Descriptive test names: describe behavior, not implementation
- Test behavior, not implementation details
- Prefer real code over mocks when possible
- Mock only external dependencies (DB, APIs, file system)

## Common Rationalizations — STOP

| Thought | Reality |
|---------|---------|
| "Too simple to test" | Simple code breaks. Test takes 30 seconds. |
| "I'll test after" | Tests passing immediately prove nothing. |
| "Just this once" | Every exception becomes the rule. |
| "Need to explore first" | Fine. Then delete exploration and start with TDD. |
| "It will slow me down" | TDD is faster than debugging. |

## Custom Test Commands

If the project uses non-standard test commands (e.g. `./run-tests.sh`, `make test`), create a `.tdd-test-patterns` file in the project root with one pattern per line:

```
# .tdd-test-patterns
./run-tests.sh
make test
custom-runner --test
```

Common wrappers like `make test`, `make check`, `./run-tests.sh`, and `./test.sh` are detected automatically.

## Debugging

Set `DEBUG_TDD=1` to see detailed hook decision tracing:

```bash
export DEBUG_TDD=1
```

This outputs each check evaluated by the hooks (test file detection, exemption matching, GREEN phase status) and why an edit was allowed or blocked.

## Manual Reset

Use `/tdd-reset` to clear all GREEN phase markers and return to the RED phase within the current session. Useful when starting a new unit of work without restarting Claude Code.

## Multi-Session Safety

GREEN phase markers are scoped to the current session. Running tests in one Claude session will not unlock source edits in another concurrent session working on the same project.

## Additional Resources

### Reference Files

For detailed information, consult:

- **`references/test-patterns.md`** — Framework-specific test patterns, file conventions, and command detection
- **`references/exempt-files.md`** — Complete list of file patterns exempt from TDD enforcement
