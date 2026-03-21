# Cursor CLI integration

When **`AGENT_RUNTIME=cursor`** (default), the runner uses:

- **`CursorCliAdapter`** — AGENT_TASK jobs: writes pending JSON, optionally spawns **`cursor agent …`**.
- **`CursorPlanCliAdapter`** — plan mode: **`cursor agent -p --plan --trust --output-format stream-json --workspace <dir> "<prompt>"`**, resume with **`--resume <cliSessionId>`** and the user message.

## Configuration

| Env / property | Role |
|----------------|------|
| `CURSOR_CLI_INVOKE` | If `true`, spawn Cursor CLI after writing the task file. |
| `CURSOR_CLI_WORKSPACE` | Workspace directory for the CLI; fallback `AGENT_CLI_WORKSPACE`, then job payload `workspace`, then `AGENT_TASK_WORKSPACE`, then `user.home`. |
| `CURSOR_CLI_PROMPT` | Prompt for **agent** task invocations (not plan-specific). |
| `CURSOR_PLAN_PROMPT` | Suffix appended to the user’s `/plan` text to encourage question formatting. |

See [application.yml](../src/main/resources/application.yml) under `app.cursor` and [config.example.env](../config.example.env).

## Plan vs agent task

| Flow | Adapter | Output |
|------|---------|--------|
| `/agent`, classified AGENT_TASK | `CursorCliAdapter` | Pending JSON + optional long-running CLI stream to job logs. |
| `/plan`, PLAN_TASK | `CursorPlanCliAdapter` | Blocking `stream-json` parse → questions / final plan. |

## Documentation

- [Cursor CLI](https://cursor.com/docs/cli/using) — install and auth.
- [Plan mode](plan-mode.md) — end-user behavior.
