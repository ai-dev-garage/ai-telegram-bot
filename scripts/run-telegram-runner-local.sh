#!/usr/bin/env bash
#
# One-click local run for ai-telegram-bot (Spring Boot + PostgreSQL + Telegram polling).
#
# Usage:
#   ./scripts/run-telegram-runner-local.sh [--agent cursor|claude]
#
# Optional:
#   --agent   One-shot override for AGENT_RUNTIME (after .env is loaded, this wins).
#
# Required env vars:
#   TELEGRAM_BOT_TOKEN
#   ALLOWED_TELEGRAM_USER_IDS   (numeric ids, comma-separated)
#
# Agent runtime (set in .env or via --agent):
#   AGENT_RUNTIME — cursor (default) or claude; selects Spring AgentTaskRuntime.
#
# When AGENT_RUNTIME=cursor (defaults for local dev if unset in .env):
#   CURSOR_CLI_INVOKE, CURSOR_CLI_WORKSPACE — see config.example.env
#
# When AGENT_RUNTIME=claude:
#   CLAUDE_CLI_INVOKE, CLAUDE_CLI_WORKSPACE — Claude CLI uses workspace as process cwd
#
# Spring profile `local` is set by default below. Optional overrides: copy
#   src/main/resources/application-local.example.yml -> application-local.yml (gitignored).
#

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
BOT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

show_help() {
  cat <<EOF
One-click local run for ai-telegram-bot (Spring Boot + PostgreSQL + Telegram polling).

Usage:
  $(basename "$0") [--agent cursor|claude]

Options:
  -h, --help     Show this help and exit.
  --agent X      One-shot override for AGENT_RUNTIME (cursor|claude), after .env is loaded.

Required env vars:
  TELEGRAM_BOT_TOKEN
  ALLOWED_TELEGRAM_USER_IDS   (numeric ids, comma-separated; also loadable from .env)

Agent runtime (set in .env or via --agent):
  AGENT_RUNTIME — cursor (default) or claude; selects Spring AgentTaskRuntime.

When AGENT_RUNTIME=cursor (defaults for local dev if unset in .env):
  CURSOR_CLI_INVOKE, CURSOR_CLI_WORKSPACE — see config.example.env

When AGENT_RUNTIME=claude:
  CLAUDE_CLI_INVOKE, CLAUDE_CLI_WORKSPACE — Claude CLI uses workspace as process cwd

Optional (bootRun app JVM — overrides gradle.properties when set, e.g. from .env):
  BOOTRUN_JVM_XMS, BOOTRUN_JVM_XMX  — passed as ./gradlew -PbootRunJvmXms=/Xmx= (see README / gradle.properties).
EOF
}

CLI_AGENT=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      show_help
      exit 0
      ;;
    --agent)
      CLI_AGENT="${2:-}"
      if [[ -z "$CLI_AGENT" ]]; then
        echo "Usage: $0 [--agent cursor|claude]"
        exit 1
      fi
      shift 2
      ;;
    *)
      echo "Unknown argument: $1 (try --help)"
      exit 1
      ;;
  esac
done

# Auto-load .env from project root if present.
if [ -f "$BOT_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$BOT_ROOT/.env"
  set +a
fi

if [[ -n "$CLI_AGENT" ]]; then
  export AGENT_RUNTIME="$CLI_AGENT"
fi

if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
  echo "Set TELEGRAM_BOT_TOKEN before running."
  exit 1
fi
if [ -z "${ALLOWED_TELEGRAM_USER_IDS:-}" ]; then
  echo "Set ALLOWED_TELEGRAM_USER_IDS (numeric ids) before running."
  exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export RUNNER_PORT="${RUNNER_PORT:-8765}"
export TELEGRAM_ENABLED="${TELEGRAM_ENABLED:-true}"

export AGENT_RUNTIME="${AGENT_RUNTIME:-cursor}"
case "$AGENT_RUNTIME" in
  cursor|claude) ;;
  *)
    echo "AGENT_RUNTIME must be cursor or claude (got: $AGENT_RUNTIME)"
    exit 1
    ;;
esac

if [[ "$AGENT_RUNTIME" == "cursor" ]]; then
  export CURSOR_CLI_INVOKE="${CURSOR_CLI_INVOKE:-true}"
  export CURSOR_CLI_WORKSPACE="${CURSOR_CLI_WORKSPACE:-$BOT_ROOT}"
else
  export CLAUDE_CLI_INVOKE="${CLAUDE_CLI_INVOKE:-true}"
  export CLAUDE_CLI_WORKSPACE="${CLAUDE_CLI_WORKSPACE:-$BOT_ROOT}"
fi

cd "$BOT_ROOT"
echo "Starting PostgreSQL via docker compose..."
docker compose up -d

echo "Starting ai-telegram-bot on port $RUNNER_PORT (AGENT_RUNTIME=$AGENT_RUNTIME)..."

BOOTRUN_GRADLE_PROPS=()
if [[ -n "${BOOTRUN_JVM_XMS:-}" ]]; then
  BOOTRUN_GRADLE_PROPS+=(-PbootRunJvmXms="$BOOTRUN_JVM_XMS")
fi
if [[ -n "${BOOTRUN_JVM_XMX:-}" ]]; then
  BOOTRUN_GRADLE_PROPS+=(-PbootRunJvmXmx="$BOOTRUN_JVM_XMX")
fi

# With `set -u`, expanding an empty "${array[@]}" can error on some Bash builds; branch instead.
if [[ ${#BOOTRUN_GRADLE_PROPS[@]} -gt 0 ]]; then
  ./gradlew bootRun "${BOOTRUN_GRADLE_PROPS[@]}"
else
  ./gradlew bootRun
fi
