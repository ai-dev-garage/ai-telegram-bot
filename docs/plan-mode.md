# Plan mode (Telegram + web)

Plan mode lets you iterate on a **PLAN_TASK** job: the external agent asks clarifying questions, you answer via inline buttons or free text, then you can **Build** (approve), **Adjust**, **Pause**, **Resume**, or **Cancel**.

## User flow

1. Start: **`/plan <your goal>`** (Telegram) or create a plan job from the web UI / todos “work → plan”.
2. The runner invokes the configured **plan CLI** (`PlanCliRuntime`): **Cursor** or **Claude Code**, depending on `AGENT_RUNTIME`.
3. The CLI returns **NDJSON** (`stream-json`). The runner extracts:
   - **`session_id`** — stored as `cliSessionId` for **resume** on the next round.
   - **Assistant text** — parsed for lines ending with `?` (questions) via `AgentQuestionParser`.
4. If there are questions, you get Telegram inline buttons (and optional “type your own answer”). When the round is complete, the runner **resumes** the CLI session with your compiled answers.
5. When there are no more questions, the plan text is shown; you can **Build** / **Adjust** / **Pause** / etc.

If the plan CLI exits immediately (non-zero exit, or no `stream-json` output), the job is marked **FAILED**, the session state is **FAILED**, and the web UI / job error shows a short reason plus any raw stderr/stdout preview. Typical fix: install the CLI, run `cursor agent --help` from the same environment as the runner, or set **`CURSOR_CLI_BIN`** to an absolute path (see [agent-cursor.md](agent-cursor.md)).

## Export directory

On **Build** or **Pause**, a **`.plan.md`** file is written under **`PLANS_EXPORT_DIR`** (default `~/.ai-dev-garage/plans`). Point your IDE or agent at this file if you use Cursor-style plan markdown workflows.

## Configuration pointers

| Concern | Cursor | Claude |
|---------|--------|--------|
| Plan prompt suffix | `CURSOR_PLAN_PROMPT` / `app.cursor.plan-prompt` | `CLAUDE_PLAN_PROMPT` / `app.claude.plan-prompt` |
| CLI details | [agent-cursor.md](agent-cursor.md) | [agent-claude.md](agent-claude.md) |

## Implementation references (developers)

- `PlanCliRuntime` — port for start/resume.
- `CursorPlanCliAdapter` / `ClaudePlanCliAdapter` — concrete CLIs.
- `CliStreamParser` — shared NDJSON parsing (`assistant` / `result` + Claude `stream_event` deltas).
- `PlanSessionService` — orchestration and persistence.
