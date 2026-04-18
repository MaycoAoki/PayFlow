#!/bin/bash
set -eu

# TDD Enforcer - Stop hook (v1.1.0)
# Informational message if TDD was active in this session.

# =============================================================================
# DEPENDENCIES
# =============================================================================
if ! command -v jq >/dev/null 2>&1; then
  exit 0
fi

# =============================================================================
# DEBUG
# =============================================================================
debug() {
  if [ "${DEBUG_TDD:-}" = "1" ] || [ "${DEBUG_TDD:-}" = "true" ]; then
    echo "[TDD DEBUG check-tdd-active] $*" >&2
  fi
}

# =============================================================================
# INPUT
# =============================================================================
input=$(cat)
session_id=$(echo "$input" | jq -r '.session_id // ""' 2>/dev/null) || true

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

# =============================================================================
# FIND MARKER
# =============================================================================
project_root=$(find_project_root)
debug "project_root=$project_root"

marker=""

# Session-scoped marker (preferred)
if [ -n "$session_id" ] && [ -f "$project_root/.tdd-state-${session_id}" ]; then
  marker="$project_root/.tdd-state-${session_id}"
fi

# Legacy/fallback marker
if [ -z "$marker" ] && [ -f "$project_root/.tdd-state" ]; then
  marker="$project_root/.tdd-state"
fi

if [ -z "$marker" ]; then
  debug "no TDD marker found, exiting silently"
  exit 0
fi

debug "found marker: $marker"

RST=$'\033[0m'
BLD=$'\033[1m'
ITL=$'\033[3m'
C_BLU=$'\033[38;5;75m'
C_DIM=$'\033[38;5;245m'
NF_SHIELD=$'\uf132'

printf "\n  ${C_BLU}${NF_SHIELD}${RST}  ${BLD}TDD${RST}  ${C_DIM}${ITL}Session active — markers cleaned on next start${RST}\n\n"

exit 0
