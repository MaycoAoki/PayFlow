#!/bin/bash
set -eu

# TDD Enforcer - PostToolUse hook (v1.1.0)
# Auto-creates GREEN phase marker when a test command fails.
# Eliminates manual marker management — no permission prompts.

# =============================================================================
# DEPENDENCIES
# =============================================================================
if ! command -v jq >/dev/null 2>&1; then
  echo "TDD Enforcer: 'jq' is required but not found." >&2
  exit 0
fi

# =============================================================================
# DEBUG
# =============================================================================
debug() {
  if [ "${DEBUG_TDD:-}" = "1" ] || [ "${DEBUG_TDD:-}" = "true" ]; then
    echo "[TDD DEBUG auto-marker] $*" >&2
  fi
}

# =============================================================================
# INPUT
# =============================================================================
input=$(cat)

bash_cmd=$(echo "$input" | jq -r '.tool_input.command // ""' 2>/dev/null) || exit 0
[ -z "$bash_cmd" ] || [ "$bash_cmd" = "null" ] && exit 0

session_id=$(echo "$input" | jq -r '.session_id // ""' 2>/dev/null) || true

debug "bash_cmd=$bash_cmd"
debug "session_id=$session_id"

# =============================================================================
# PROJECT ROOT DETECTION
# =============================================================================
find_project_root() {
  local dir="${CLAUDE_PROJECT_DIR:-$PWD}"
  local i=0
  while [ "$dir" != "/" ] && [ $i -lt 15 ]; do
    for m in package.json Cargo.toml go.mod pyproject.toml mix.exs build.gradle pom.xml Gemfile composer.json pubspec.yaml .git; do
      if [ -e "$dir/$m" ]; then
        echo "$dir"
        return 0
      fi
    done
    dir=$(dirname "$dir")
    i=$((i + 1))
  done
  echo "${CLAUDE_PROJECT_DIR:-$PWD}"
}

project_root=$(find_project_root)
debug "project_root=$project_root"

# =============================================================================
# CUSTOM TEST PATTERNS (.tdd-test-patterns file)
# =============================================================================
check_custom_test_patterns() {
  local cmd="$1"
  local patterns_file="$project_root/.tdd-test-patterns"
  [ -f "$patterns_file" ] || return 1

  while IFS= read -r pattern || [ -n "$pattern" ]; do
    [[ -z "$pattern" || "$pattern" =~ ^[[:space:]]*# ]] && continue
    pattern=$(echo "$pattern" | xargs)
    [ -z "$pattern" ] && continue
    if [[ "$cmd" == *"$pattern"* ]]; then
      debug "-> matched custom test pattern: $pattern"
      return 0
    fi
  done < "$patterns_file"

  return 1
}

# =============================================================================
# DETECT TEST COMMANDS
# =============================================================================
is_test_command() {
  local cmd="$1"

  case "$cmd" in
    # JavaScript/TypeScript
    *"npm test"*|*"npm run test"*) return 0 ;;
    *"yarn test"*|*"yarn run test"*) return 0 ;;
    *"pnpm test"*|*"pnpm run test"*) return 0 ;;
    *"bun test"*|*"bunx vitest"*|*"bunx jest"*) return 0 ;;
    *"npx jest"*|*"npx vitest"*|*"npx mocha"*|*"npx playwright"*) return 0 ;;
    # Python
    *pytest*|*"python -m pytest"*|*"py.test"*) return 0 ;;
    # Go
    *"go test"*) return 0 ;;
    # Rust
    *"cargo test"*) return 0 ;;
    # Ruby
    *rspec*|*"bundle exec rspec"*) return 0 ;;
    # Elixir
    *"mix test"*) return 0 ;;
    # PHP
    *phpunit*) return 0 ;;
    # Dart/Flutter
    *"dart test"*|*"flutter test"*) return 0 ;;
    # .NET
    *"dotnet test"*) return 0 ;;
    # Java/Kotlin
    *"mvn test"*|*"gradle test"*|*"./gradlew test"*) return 0 ;;
    # Swift
    *"swift test"*) return 0 ;;
    # Common script wrappers
    *"make test"*|*"make check"*) return 0 ;;
    *"./run-tests"*|*"./run_tests"*|*"./test.sh"*) return 0 ;;
  esac

  # Project-level custom patterns
  if check_custom_test_patterns "$cmd"; then
    return 0
  fi

  return 1
}

if ! is_test_command "$bash_cmd"; then
  debug "not a test command, skipping"
  exit 0
fi

debug "detected test command"

# =============================================================================
# MARKER PATH (session-scoped)
# =============================================================================
marker_name=".tdd-state"
[ -n "$session_id" ] && marker_name=".tdd-state-${session_id}"
marker="$project_root/$marker_name"

debug "marker=$marker"

# =============================================================================
# DETECT TEST FAILURE IN OUTPUT
# =============================================================================
# tool_response is an object with stdout, stderr, interrupted fields
stdout=$(echo "$input" | jq -r '.tool_response.stdout // .tool_response // .tool_result // ""' 2>/dev/null) || exit 0
stderr=$(echo "$input" | jq -r '.tool_response.stderr // ""' 2>/dev/null) || true
result="${stdout}
${stderr}"

test_failed=false

# Exit code patterns (most reliable signal)
if echo "$result" | grep -qE '[Ee]xit code:?\s*[1-9]'; then
  test_failed=true
  debug "-> failure detected via exit code"
fi

# Test framework failure patterns (word boundaries to reduce false positives)
if [ "$test_failed" = false ]; then
  if echo "$result" | grep -qE '\bFAIL\b|\bFAILED\b|--- FAIL:|test result: FAILED'; then
    test_failed=true
    debug "-> failure detected via framework pattern"
  elif echo "$result" | grep -qiE 'Tests:.*[1-9]+\s+failed'; then
    test_failed=true
    debug "-> failure detected via test count"
  fi
fi

# Timeout detection
if [ "$test_failed" = false ]; then
  if echo "$result" | grep -qiE '\bTimeout\b|\btimed?\s*out\b'; then
    test_failed=true
    debug "-> failure detected via timeout"
  fi
fi

# Counter-signal: strong pass indicators override false positives
if [ "$test_failed" = true ]; then
  if echo "$result" | grep -qiE '\ball tests passed\b|\btest result: ok\b|\b0 failed\b'; then
    debug "-> passing counter-signal detected, overriding failure"
    test_failed=false
  fi
fi

# =============================================================================
# CREATE MARKER ON FAILURE
# =============================================================================
# Style
RST=$'\033[0m'
BLD=$'\033[1m'
ITL=$'\033[3m'
C_GRN=$'\033[38;5;114m'
C_DIM=$'\033[38;5;245m'
NF_UNLOCK=$'\uf09c'

if [ "$test_failed" = true ] && [ ! -f "$marker" ]; then
  touch "$marker"
  debug "marker created: $marker"
  printf "\n  ${C_GRN}${BLD}${NF_UNLOCK}  GREEN PHASE ENABLED${RST}  ${C_DIM}${ITL}Test failure detected — source edits unlocked${RST}\n\n"
elif [ "$test_failed" = true ]; then
  debug "marker already exists, no action needed"
fi

exit 0
