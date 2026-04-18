#!/bin/bash

# Writes doc-updater context to a file that the agent reads as its first step.
# Only runs when the Agent tool is called with doc-updater subagent.

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

# Check if we're in a git repo and resolve the true git root (handles monorepos/submodules)
if ! git -C "$CLAUDE_PROJECT_DIR" rev-parse --is-inside-work-tree &>/dev/null; then
  exit 0
fi
GIT_ROOT=$(git -C "$CLAUDE_PROJECT_DIR" rev-parse --show-toplevel 2>/dev/null)
if [ -z "$GIT_ROOT" ]; then
  exit 0
fi

# Ensure .claude directory exists
mkdir -p "$CLAUDE_PROJECT_DIR/.claude"

# State files
COMMIT_FILE="$CLAUDE_PROJECT_DIR/.claude/.last-doc-commit"
CONTEXT_FILE="$CLAUDE_PROJECT_DIR/.claude/.doc-updater-context"

# Check for --full flag in agent prompt (force full scan)
agent_prompt=$(echo "$input" | jq -r '.tool_input.prompt // ""')
force_full=false
if [[ "$agent_prompt" == *"--full"* ]]; then
  force_full=true
fi

current_head=$(git -C "$GIT_ROOT" rev-parse HEAD)

# Read last documented commit (unless forcing full scan)
last_commit=""
if [ "$force_full" = false ] && [ -f "$COMMIT_FILE" ]; then
  last_commit=$(tr -d '[:space:]' < "$COMMIT_FILE")
  if ! git -C "$GIT_ROOT" cat-file -t "$last_commit" &>/dev/null; then
    last_commit=""
  fi
fi

# Determine mode and write context file
if [ -z "$last_commit" ]; then
  # MODE 1: Full scan
  cat > "$CONTEXT_FILE" << 'CTXEOF'
MODE: FULL DOCUMENTATION (first run or forced)

No previous documentation record found. Perform a complete project analysis:

1. Explore the project directory structure
2. Identify all modules, patterns, and conventions
3. Read key files from each module (models, routes, components, etc.)
4. Create or update CLAUDE.md in every relevant directory
5. Update root CLAUDE.md with the complete project map

Remember: CLAUDE.md is LLM context, not human documentation. Be dense and concise.
CTXEOF

elif [ "$last_commit" = "$current_head" ]; then
  # No changes since last doc run
  echo "NO_CHANGES: No files have been modified since the last documentation update." > "$CONTEXT_FILE"

else
  # MODE 2: Incremental update
  changed_files=$(git -C "$GIT_ROOT" diff --name-only "$last_commit" HEAD 2>/dev/null | grep -v 'CLAUDE\.md' | grep -v 'README\.md' || true)

  if [ -z "$changed_files" ]; then
    echo "NO_CHANGES: Only documentation files were modified. Nothing to update." > "$CONTEXT_FILE"
  else
    file_list=$(echo "$changed_files" | sed 's/^/- /')
    file_count=$(echo "$changed_files" | wc -l | tr -d ' ')

    # Get commit log summary for context
    commit_log=$(git -C "$GIT_ROOT" log --oneline "$last_commit"..HEAD 2>/dev/null | head -20 || true)
    diff_stat=$(git -C "$GIT_ROOT" diff --stat "$last_commit" HEAD 2>/dev/null | tail -1 || true)

    cat > "$CONTEXT_FILE" << CTXEOF
MODE: INCREMENTAL UPDATE (diff since $last_commit)

CHANGE SUMMARY:
$diff_stat

RECENT COMMITS:
$commit_log

MODIFIED FILES ($file_count files):
$file_list

INSTRUCTION: Focus documentation updates only on modules/directories containing these files. Do not read or modify CLAUDE.md of unaffected modules.
CTXEOF
  fi
fi

exit 0
