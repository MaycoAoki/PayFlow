#!/bin/bash
set -eu

# TDD Enforcer - PreToolUse hook (v1.1.0)
# Checks if the file being edited is exempt from TDD enforcement.
# Exit 0 = allow | Exit 2 = block

# =============================================================================
# DEPENDENCIES
# =============================================================================
if ! command -v jq >/dev/null 2>&1; then
  echo "TDD Enforcer: 'jq' is required but not found. Install jq to enable TDD enforcement." >&2
  exit 0  # Fail open — don't block edits if jq is missing
fi

# =============================================================================
# DEBUG
# =============================================================================
debug() {
  if [ "${DEBUG_TDD:-}" = "1" ] || [ "${DEBUG_TDD:-}" = "true" ]; then
    echo "[TDD DEBUG check-exempt] $*" >&2
  fi
}

# =============================================================================
# INPUT
# =============================================================================
input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path // .tool_input.path // ""')
session_id=$(echo "$input" | jq -r '.session_id // ""')

if [ -z "$file_path" ] || [ "$file_path" = "null" ]; then
  exit 0
fi

debug "file_path=$file_path"
debug "session_id=$session_id"

normalized=$(echo "$file_path" | tr '[:upper:]' '[:lower:]')

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

# =============================================================================
# TEST FILES — Always allowed (writing tests IS TDD)
# =============================================================================
is_test_file() {
  local path
  path=$(echo "$1" | tr '[:upper:]' '[:lower:]')

  # File name patterns: *.spec.*, *.test.*, *_test.*, *_spec.*
  [[ "$path" =~ \.(spec|test)\.[^/]+$ ]] && return 0
  [[ "$path" =~ _test\.[^/]+$ ]] && return 0
  [[ "$path" =~ _spec\.[^/]+$ ]] && return 0

  # Directory patterns: test/, tests/, __tests__/, spec/
  [[ "$path" =~ /test/ ]] && return 0
  [[ "$path" =~ /tests/ ]] && return 0
  [[ "$path" =~ /__tests__/ ]] && return 0
  [[ "$path" =~ /spec/ ]] && return 0

  # Python test files: test_*.py
  [[ "$path" =~ /test_[^/]+\.py$ ]] && return 0

  # Java/Kotlin/PHP/Groovy test files: *Test.java, *Spec.groovy (PascalCase convention)
  [[ "$path" =~ test\.(java|kt|groovy|scala|php)$ ]] && return 0
  [[ "$path" =~ spec\.(java|kt|groovy|scala|php)$ ]] && return 0

  return 1
}

# =============================================================================
# EXEMPT FILES — No TDD required
# =============================================================================
is_exempt() {
  local path="$1"

  # --- Configuration files ---
  [[ "$path" =~ \.config\.[^/]+$ ]] && return 0
  [[ "$path" =~ \.env ]] && return 0
  [[ "$path" =~ \.(json|yaml|yml|toml|ini|cfg)$ ]] && return 0
  [[ "$path" =~ \.(lock|lockb)$ ]] && return 0
  [[ "$path" =~ \.editorconfig$ ]] && return 0
  [[ "$path" =~ \.nvmrc$ ]] && return 0
  [[ "$path" =~ \.node-version$ ]] && return 0
  [[ "$path" =~ \.tool-versions$ ]] && return 0

  # --- Documentation ---
  [[ "$path" =~ \.(md|mdx|txt|rst|adoc)$ ]] && return 0

  # --- Styles/CSS ---
  [[ "$path" =~ \.(css|scss|less|sass|styl)$ ]] && return 0

  # --- Static assets ---
  [[ "$path" =~ \.(svg|png|jpg|jpeg|gif|ico|webp|avif)$ ]] && return 0
  [[ "$path" =~ \.(woff|woff2|ttf|eot|otf)$ ]] && return 0
  [[ "$path" =~ \.(mp4|webm|ogg|mp3|wav)$ ]] && return 0

  # --- Type declarations ---
  [[ "$path" =~ \.d\.ts$ ]] && return 0
  [[ "$path" =~ \.d\.mts$ ]] && return 0

  # --- Database schemas/migrations/seeds ---
  [[ "$path" =~ \.(prisma|sql)$ ]] && return 0
  [[ "$path" =~ /migrations/ ]] && return 0
  [[ "$path" =~ /seeds?/ ]] && return 0
  [[ "$path" =~ /prisma/ ]] && return 0

  # --- Container/CI/CD ---
  [[ "$path" =~ docker-?compose ]] && return 0
  [[ "$path" =~ dockerfile ]] && return 0
  [[ "$path" =~ makefile ]] && return 0
  [[ "$path" =~ procfile ]] && return 0
  [[ "$path" =~ jenkinsfile ]] && return 0
  [[ "$path" =~ \.github/ ]] && return 0
  [[ "$path" =~ \.gitlab ]] && return 0
  [[ "$path" =~ \.circleci/ ]] && return 0

  # --- Generated/build output ---
  [[ "$path" =~ /generated/ ]] && return 0
  [[ "$path" =~ /dist/ ]] && return 0
  [[ "$path" =~ /build/ ]] && return 0
  [[ "$path" =~ /\.next/ ]] && return 0
  [[ "$path" =~ /node_modules/ ]] && return 0
  [[ "$path" =~ /\.turbo/ ]] && return 0
  [[ "$path" =~ /coverage/ ]] && return 0

  # --- Static/public directories ---
  [[ "$path" =~ /public/ ]] && return 0
  [[ "$path" =~ /static/ ]] && return 0

  # --- Scripts/tooling ---
  [[ "$path" =~ /scripts/ ]] && return 0

  # --- Claude/plugin files ---
  [[ "$path" =~ claude\.md$ ]] && return 0
  [[ "$path" =~ agents\.md$ ]] && return 0
  [[ "$path" =~ hooks\.json$ ]] && return 0
  [[ "$path" =~ plugin\.json$ ]] && return 0
  [[ "$path" =~ skill\.md$ ]] && return 0
  [[ "$path" =~ \.mcp\.json$ ]] && return 0

  # --- Git ---
  [[ "$path" =~ \.gitignore$ ]] && return 0
  [[ "$path" =~ \.gitattributes$ ]] && return 0

  # --- Linting/formatting ---
  [[ "$path" =~ \.(eslintrc|prettierrc|stylelintrc) ]] && return 0
  [[ "$path" =~ \.eslint\. ]] && return 0
  [[ "$path" =~ \.prettier ]] && return 0
  [[ "$path" =~ \.swcrc$ ]] && return 0
  [[ "$path" =~ \.babelrc ]] && return 0

  # --- App bootstrap/entrypoint (no testable business logic) ---
  [[ "$path" =~ /main\.(ts|js|mts|mjs)$ ]] && return 0
  [[ "$path" =~ /index\.(ts|js|mts|mjs)$ ]] && return 0
  [[ "$path" =~ app\.module\.(ts|js)$ ]] && return 0
  [[ "$path" =~ /config/ ]] && return 0
  [[ "$path" =~ \.module\.(ts|js)$ ]] && return 0

  return 1
}

# =============================================================================
# PROJECT-LEVEL EXEMPTIONS (.tdd-exempt file)
# =============================================================================
exempt_file_found=""

is_project_exempt() {
  local path="$1"
  local dir
  dir=$(dirname "$path")

  # Walk up to find .tdd-exempt (max 15 levels)
  local i=0
  local exempt_file=""
  while [ "$dir" != "/" ] && [ $i -lt 15 ]; do
    if [ -f "$dir/.tdd-exempt" ]; then
      exempt_file="$dir/.tdd-exempt"
      exempt_file_found="$exempt_file"
      break
    fi
    dir=$(dirname "$dir")
    i=$((i + 1))
  done

  [ -z "$exempt_file" ] && return 1

  debug "found .tdd-exempt at: $exempt_file"

  while IFS= read -r pattern || [ -n "$pattern" ]; do
    # Skip comments and empty lines
    [[ -z "$pattern" || "$pattern" =~ ^[[:space:]]*# ]] && continue
    # Trim whitespace
    pattern=$(echo "$pattern" | xargs)
    [ -z "$pattern" ] && continue

    # Try regex match; catch invalid patterns (bash returns exit 2 for bad regex)
    set +e
    [[ "$path" =~ $pattern ]]
    local rc=$?
    set -e

    if [ $rc -eq 0 ]; then
      debug "-> matched .tdd-exempt pattern: $pattern"
      return 0
    elif [ $rc -eq 2 ]; then
      echo "TDD Enforcer: WARNING — invalid regex in .tdd-exempt, skipping: '$pattern'" >&2
      debug "-> invalid regex skipped: $pattern"
    fi
  done < "$exempt_file"

  return 1
}

# =============================================================================
# GREEN PHASE CHECK
# =============================================================================
is_green_phase() {
  local project_root
  project_root=$(find_project_root)

  # Session-scoped marker (preferred)
  if [ -n "$session_id" ] && [ -f "$project_root/.tdd-state-${session_id}" ]; then
    debug "-> found session-scoped marker: .tdd-state-${session_id}"
    return 0
  fi

  # Legacy/fallback marker
  if [ -f "$project_root/.tdd-state" ]; then
    debug "-> found legacy marker: .tdd-state"
    return 0
  fi

  # Walk up from file path (fallback for unusual directory structures)
  local dir
  dir=$(dirname "$file_path")
  local i=0
  while [ "$dir" != "/" ] && [ $i -lt 15 ]; do
    if [ -n "$session_id" ] && [ -f "$dir/.tdd-state-${session_id}" ]; then
      return 0
    fi
    if [ -f "$dir/.tdd-state" ]; then
      return 0
    fi
    dir=$(dirname "$dir")
    i=$((i + 1))
  done

  return 1
}

# =============================================================================
# STYLE
# =============================================================================
RST=$'\033[0m'
BLD=$'\033[1m'
DIM=$'\033[2m'
ITL=$'\033[3m'
C_RED=$'\033[38;5;203m'
C_GRN=$'\033[38;5;114m'
C_YLW=$'\033[38;5;220m'
C_BLU=$'\033[38;5;75m'
C_DIM=$'\033[38;5;245m'
C_WHT=$'\033[38;5;255m'
NF_LOCK=$'\uf023'
NF_FILE=$'\uf15c'
NF_X=$'\uf00d'
NF_BULB=$'\uf0eb'
NF_FLASK=$'\uf0c3'
NF_PLAY=$'\uf04b'
NF_CODE=$'\uf121'
SEP=$(printf '━%.0s' {1..54})
HSEP_CK=$(printf '─%.0s' {1..42})
HSEP_WF=$(printf '─%.0s' {1..40})

# =============================================================================
# DECISION
# =============================================================================

# Test files -> always allow (writing tests IS TDD)
if is_test_file "$file_path"; then
  debug "ALLOWED: test file"
  exit 0
fi

# Exempt files -> allow without TDD
if is_exempt "$normalized"; then
  debug "ALLOWED: exempt file type"
  exit 0
fi

# Project-level exemptions -> allow without TDD
r_project="No .tdd-exempt file found"
if is_project_exempt "$file_path"; then
  debug "ALLOWED: project .tdd-exempt match"
  exit 0
fi
[ -n "$exempt_file_found" ] && r_project="No match in .tdd-exempt"

# GREEN phase -> allow source edits after a recent failing test
if is_green_phase; then
  debug "ALLOWED: GREEN phase active"
  exit 0
fi

debug "BLOCKED: no matching exemption"

# SOURCE CODE FILE DETECTED -> Block and require TDD
{
  echo ""
  printf "  ${C_DIM}${SEP}${RST}\n"
  echo ""
  printf "  ${C_RED}${BLD}${NF_LOCK}  TDD ENFORCER${RST}  ${C_DIM}·${RST}  ${C_WHT}${BLD}SOURCE EDIT BLOCKED${RST}\n"
  echo ""
  printf "  ${C_BLU}${NF_FILE}${RST}  ${C_WHT}%s${RST}\n" "$file_path"
  echo ""
  printf "  ${C_DIM}── Checks ${HSEP_CK}${RST}\n"
  printf "    ${C_RED}${NF_X}${RST}  ${C_DIM}Not a test file${RST}\n"
  printf "    ${C_RED}${NF_X}${RST}  ${C_DIM}Not an exempt file type${RST}\n"
  printf "    ${C_RED}${NF_X}${RST}  ${C_DIM}%s${RST}\n" "$r_project"
  printf "    ${C_RED}${NF_X}${RST}  ${C_DIM}No GREEN phase marker${RST}\n"
  echo ""
  printf "  ${C_DIM}── Workflow ${HSEP_WF}${RST}\n"
  printf "   ${C_RED}${BLD}1${RST}  ${C_RED}${NF_FLASK} RED${RST}    ${C_WHT}Write a failing test first${RST}\n"
  printf "   ${C_YLW}${BLD}2${RST}  ${C_YLW}${NF_PLAY} RUN${RST}    ${C_WHT}Execute and confirm it fails${RST}\n"
  printf "   ${C_GRN}${BLD}3${RST}  ${C_GRN}${NF_CODE} GREEN${RST}  ${C_WHT}Edit source to make it pass${RST}\n"
  echo ""
  printf "  ${C_BLU}${NF_BULB}${RST}  ${C_DIM}${ITL}Run the failing test — GREEN enables automatically${RST}\n"
  echo ""
  printf "  ${C_DIM}${SEP}${RST}\n"
  echo ""
} >&2

exit 2
