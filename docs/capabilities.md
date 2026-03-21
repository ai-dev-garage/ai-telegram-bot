# Capabilities, MVP scope, and community checklist

This page summarizes what the **ai-telegram-bot** runner does today, how **Cursor** vs **Claude Code** differ when wired as the agent runtime, and what we consider **v1.0-ready** for open source.

## Bot / runner (product surface)

| Area | Status | Notes |
|------|--------|--------|
| Telegram: jobs, shell `/run`, approval flow | Ready | Policy-driven classification; see [operator-guide](operator-guide.md). |
| Telegram: navigation `/nav`, `/ls`, `/cd`, `/pwd` | Ready | Requires `ALLOWED_NAVIGATION_PATHS`. |
| Telegram: `/agent`, todos (`/todo`, `/todos`, work) | Ready | Workspace from navigation or payload. |
| Telegram: **plan mode** `/plan`, `/plans`, inline Q&A, Build/Pause/Resume | Ready | Uses `PlanCliRuntime`; see [plan-mode](plan-mode.md). |
| Web UI + REST API | Ready | `/ui`, `/jobs`, `/todos`, etc. |
| Postgres + Flyway | Ready | See [operator-guide — Database migrations](operator-guide.md#database-migrations). |
| AGENT_TASK file handoff | Ready | Pending/result JSON under `AGENT_TASKS_DIR`; optional CLI spawn. |
| Security / trust | Ready | `TASK_AUTH_*`, trust marker, HMAC; [process-pending-agent-task](../.agent/commands/process-pending-agent-task.md). |

## Agent runtimes: Cursor vs Claude

| Capability | Cursor (`AGENT_RUNTIME=cursor`) | Claude Code (`AGENT_RUNTIME=claude`) |
|------------|-----------------------------------|--------------------------------------|
| Write pending AGENT_TASK JSON | Yes | Yes |
| Optional CLI spawn for tasks (`*_CLI_INVOKE`) | Yes (`cursor` / `cursor agent`) | Yes (`claude -p` + `launch-command`) |
| **Plan mode** (`PlanCliRuntime`) | Yes — `cursor agent -p --plan --trust --output-format stream-json` | Yes — `claude -p "<prompt>" --output-format stream-json --permission-mode plan` (+ resume); see [agent-claude](agent-claude.md) |
| Stream NDJSON parsing | Tuned for Cursor Agent CLI | Same parser + **Claude `stream_event` / `text_delta`** when partial messages are enabled; fixture-tested (CLI versions may vary) |

## From the IDE agent’s perspective

| Concern | Cursor | Claude Code |
|---------|--------|-------------|
| Install user integration | `./scripts/install-user-agent-integration.sh --agent cursor` | Same with `--agent claude` |
| Pending task workflow | `process-pending-agent-task` under `~/.cursor/commands/` | Equivalent under Claude’s rules/commands layout (see install script) |
| Guardrails | `.agent/rules/bot-agent-task-guardrails.mdc` | Same content; Claude install may strip Cursor frontmatter |

## v1.0 MVP — in scope

- Documented quick start: Docker Postgres, `.env`, `./gradlew bootRun`.
- **Cursor** path: agent tasks + plan flow (production-tested by maintainers).
- **Claude** path: agent tasks + plan flow (**implementation follows [Claude Code CLI](https://code.claude.com/docs/en/cli-reference) / [headless](https://code.claude.com/docs/en/headless) docs**; broader e2e validation is community-driven).
- Spring context starts for **both** `AGENT_RUNTIME=cursor` and `claude` (each exposes exactly one `PlanCliRuntime` bean).
- Honest documentation of permissions and security trade-offs.

## Out of scope / later

- Hosted multi-tenant SaaS, managed secrets, or hard multi-org isolation.
- Guaranteed flag-for-flag parity between Cursor CLI and Claude Code CLI.
- Automated CI e2e against real Telegram + real Claude/Cursor CLIs (optional follow-up).

## Community help welcome

- **Claude plan e2e:** Run `/plan` with `AGENT_RUNTIME=claude`, capture redacted `stream-json` if parsing fails, open an issue with **Claude Code CLI version**.
- **Permissions:** Document what works for your environment (`CLAUDE_PLAN_PERMISSION_MODE`, `--allowedTools`, `CLAUDE_PLAN_DANGEROUSLY_SKIP_PERMISSIONS` — use with extreme care).
- **Version matrix:** PRs to extend [agent-claude](agent-claude.md) with “tested with CLI version …”.

## Related

- [Operator guide](operator-guide.md) — env, Telegram, migrations.
- [Plan mode](plan-mode.md) — user-visible flow.
- [Agent: Cursor](agent-cursor.md) / [Agent: Claude](agent-claude.md).
