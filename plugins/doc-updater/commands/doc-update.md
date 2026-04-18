---
name: doc-update
description: Run the doc-updater agent to create or update project documentation (CLAUDE.md files)
args: "[--full]"
---

Use the doc-updater agent (`subagent_type: "doc-updater:doc-updater"`) to analyze the current project and create or update CLAUDE.md documentation files.

If the user passed `--full`, include that flag in the prompt to force a complete rescan regardless of previous documentation state.

Pass this prompt to the agent:

"Analyze this project and create/update CLAUDE.md documentation. Start by reading the file `.claude/.doc-updater-context` in the project root to determine the operation mode (full scan vs incremental update). Follow the instructions in that file and in your system prompt precisely. $ARGUMENTS"
