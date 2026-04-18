#!/bin/bash
set -eu

# TDD Enforcer - SessionStart hook (v1.1.0)
# Removes all stale GREEN phase markers so each session enforces TDD from scratch.

# =============================================================================
# DEBUG
# =============================================================================
debug() {
  if [ "${DEBUG_TDD:-}" = "1" ] || [ "${DEBUG_TDD:-}" = "true" ]; then
    echo "[TDD DEBUG clean-marker] $*" >&2
  fi
}

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
# CLEANUP
# =============================================================================
project_root=$(find_project_root)
debug "project_root=$project_root"

removed=0
for f in "$project_root"/.tdd-state*; do
  [ -f "$f" ] || continue
  debug "removing stale marker: $f"
  rm -f "$f"
  removed=$((removed + 1))
done

debug "cleaned up $removed marker(s)"
exit 0
