#!/usr/bin/env bash
# Materialize .agent/rules/dev into project-local .cursor or .claude (gitignored).
# For Claude, Cursor YAML frontmatter is stripped from .mdc sources (see scripts/lib/).

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
# shellcheck source=lib/strip_cursor_rule_frontmatter.sh
source "$SCRIPT_DIR/lib/strip_cursor_rule_frontmatter.sh"
TARGET=""
WITH_GUARDRAILS=false
DRY_RUN=false

show_help() {
  cat <<EOF
Usage: $(basename "$0") --target cursor|claude [--with-guardrails] [--dry-run]

Copy .agent/rules/dev into ./.cursor/rules or ./.claude/rules (gitignored). For Claude, strip
Cursor YAML frontmatter so rules are plain .md.

  --target cursor|claude   Required. Where to install project-local rules.
  --with-guardrails        Also copy bot-agent-task-guardrails into that rules dir.
  --dry-run                Print actions without writing files.

  -h, --help               Show this help and exit.

Gradle: ./gradlew installDevAgentAssets (target from -PdevAgent or env DEV_AGENT / AGENT_RUNTIME).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    --target) TARGET="${2:-}"; shift 2 ;;
    --with-guardrails) WITH_GUARDRAILS=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    *) echo "Unknown arg: $1 (try --help)"; exit 1 ;;
  esac
done

if [[ "$TARGET" != "cursor" && "$TARGET" != "claude" ]]; then
  echo "Usage: $(basename "$0") --target cursor|claude [--with-guardrails] [--dry-run]  (use --help)"
  exit 1
fi

run() {
  if $DRY_RUN; then
    echo "[dry-run] $*"
  else
    eval "$@"
  fi
}

write_claude_rule_from_mdc() {
  local src=$1 dest=$2
  if $DRY_RUN; then
    echo "[dry-run] strip Cursor frontmatter: $src -> $dest"
  else
    mkdir -p "$(dirname "$dest")"
    strip_cursor_rule_frontmatter "$src" > "$dest"
  fi
}

DEV_RULES="$ROOT/.agent/rules/dev"
if [[ ! -d "$DEV_RULES" ]]; then
  echo "Missing $DEV_RULES"
  exit 1
fi

if [[ "$TARGET" == "cursor" ]]; then
  DEST_RULES="$ROOT/.cursor/rules"
  run "mkdir -p \"$DEST_RULES\""
  for f in "$DEV_RULES"/*.mdc; do
    [[ -e "$f" ]] || continue
    base=$(basename "$f")
    run "cp \"$f\" \"$DEST_RULES/$base\""
  done
  if $WITH_GUARDRAILS; then
    run "cp \"$ROOT/.agent/rules/bot-agent-task-guardrails.mdc\" \"$DEST_RULES/\""
  fi
else
  DEST_RULES="$ROOT/.claude/rules"
  if $DRY_RUN; then
    echo "[dry-run] mkdir -p \"$DEST_RULES\""
  else
    mkdir -p "$DEST_RULES"
  fi
  for f in "$DEV_RULES"/*.mdc; do
    [[ -e "$f" ]] || continue
    base=$(basename "$f" .mdc).md
    write_claude_rule_from_mdc "$f" "$DEST_RULES/$base"
  done
  if $WITH_GUARDRAILS; then
    write_claude_rule_from_mdc "$ROOT/.agent/rules/bot-agent-task-guardrails.mdc" "$DEST_RULES/bot-agent-task-guardrails.md"
  fi
fi

echo "Installed dev rules into $DEST_RULES"
