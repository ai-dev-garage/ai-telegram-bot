#!/usr/bin/env bash
# Install guardrails + process-pending-agent-task under the user's home directory.
# For Claude, Cursor YAML frontmatter is stripped from canonical .mdc sources.

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
# shellcheck source=lib/strip_cursor_rule_frontmatter.sh
source "$SCRIPT_DIR/lib/strip_cursor_rule_frontmatter.sh"
AGENT=""
FORCE=false

show_help() {
  cat <<EOF
Usage: $(basename "$0") --agent cursor|claude [--force]

Install bot-agent-task guardrails (and Cursor command template) under your home directory.

  --agent cursor|claude   Required. cursor → ~/.cursor/rules + ~/.cursor/commands;
                          claude → ~/.claude/rules/*.md (YAML frontmatter stripped from .mdc).
  --force                 Overwrite existing files instead of skipping.

  -h, --help              Show this help and exit.

Mirror TASK_AUTH_*, AGENT_TASKS_DIR, TASK_SOURCE_ID with the runner — see README.md and config.example.env.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    --agent) AGENT="${2:-}"; shift 2 ;;
    --force) FORCE=true; shift ;;
    *) echo "Unknown arg: $1 (try --help)"; exit 1 ;;
  esac
done

if [[ "$AGENT" != "cursor" && "$AGENT" != "claude" ]]; then
  echo "Usage: $(basename "$0") --agent cursor|claude [--force]  (use --help for details)"
  exit 1
fi

copy_file() {
  local src=$1 dest=$2
  if [[ -f "$dest" && "$FORCE" != true ]]; then
    echo "Skip existing (use --force): $dest"
    return
  fi
  mkdir -p "$(dirname "$dest")"
  cp "$src" "$dest"
  echo "Installed $dest"
}

copy_mdc_as_claude_md() {
  local src=$1 dest=$2
  if [[ -f "$dest" && "$FORCE" != true ]]; then
    echo "Skip existing (use --force): $dest"
    return
  fi
  mkdir -p "$(dirname "$dest")"
  strip_cursor_rule_frontmatter "$src" > "$dest"
  echo "Installed $dest (frontmatter stripped)"
}

if [[ "$AGENT" == "cursor" ]]; then
  copy_file "$ROOT/.agent/rules/bot-agent-task-guardrails.mdc" "${HOME}/.cursor/rules/bot-agent-task-guardrails.mdc"
  copy_file "$ROOT/.agent/commands/process-pending-agent-task.md" "${HOME}/.cursor/commands/process-pending-agent-task.md"
else
  copy_mdc_as_claude_md "$ROOT/.agent/rules/bot-agent-task-guardrails.mdc" "${HOME}/.claude/rules/bot-agent-task-guardrails.md"
  echo "Claude: add a slash-command or workflow for pending tasks if your setup supports it; rule installed under ~/.claude/rules/"
fi

echo ""
echo "Set in the runner AND in your agent environment (when applicable):"
echo "  TASK_AUTH_SECRET_PHRASE"
echo "  TASK_AUTH_HMAC_SECRET"
echo "  AGENT_TASKS_DIR (optional; default ~/.ai-dev-garage/agent-tasks)"
echo "  TASK_SOURCE_ID (optional; default ai-telegram-bot)"
echo "  TASK_TRUST_MARKER_LINE (optional; default [TASK_RUNNER_TRUSTED])"
