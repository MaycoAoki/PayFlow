#!/bin/bash

# Save current HEAD as last documented commit after doc-updater runs successfully.
# Only saves if the agent actually made changes (not when nothing changed or on failure).

input=$(cat)

# Fast early-exit: skip jq if input doesn't mention doc-updater at all
if [[ "$input" != *"doc-updater"* ]]; then
  exit 0
fi

# Confirm with jq for accuracy
subagent_type=$(echo "$input" | jq -r '.tool_input.subagent_type // ""')
if [[ "$subagent_type" != *"doc-updater"* ]]; then
  exit 0
fi

# Check if we're in a git repo
if ! git -C "$CLAUDE_PROJECT_DIR" rev-parse --is-inside-work-tree &>/dev/null; then
  exit 0
fi

CONTEXT_FILE="$CLAUDE_PROJECT_DIR/.claude/.doc-updater-context"
COMMIT_FILE="$CLAUDE_PROJECT_DIR/.claude/.last-doc-commit"

# Don't save commit if the context indicated no changes were needed
if [ -f "$CONTEXT_FILE" ]; then
  if grep -q "^NO_CHANGES:" "$CONTEXT_FILE" 2>/dev/null; then
    rm -f "$CONTEXT_FILE"
    exit 0
  fi
fi

# Check if the agent result indicates failure/error
agent_result=$(echo "$input" | jq -r '.tool_result // ""')
if echo "$agent_result" | grep -qi 'error\|failed\|budget.*exhaust\|context.*limit\|could not complete'; then
  # Agent likely failed — don't mark as documented
  rm -f "$CONTEXT_FILE"
  exit 0
fi

# All checks passed — save current HEAD as last documented commit
mkdir -p "$(dirname "$COMMIT_FILE")"
git -C "$CLAUDE_PROJECT_DIR" rev-parse HEAD > "$COMMIT_FILE"

# Clean up the context file (no longer needed)
rm -f "$CONTEXT_FILE"
