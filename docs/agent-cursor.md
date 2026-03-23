# Cursor CLI integration

When **`AGENT_RUNTIME=cursor`** (default), the runner uses:

- **`CursorCliAdapter`** — AGENT_TASK jobs: writes pending JSON, optionally spawns **`cursor agent …`**.
- **`CursorPlanCliAdapter`** — plan mode: **`cursor agent --print --plan --trust --output-format stream-json --workspace <dir> [--model <id>] "<prompt>"`** (prefix configurable), resume with **`--resume <cliSessionId>`** and the user message. Global flags are documented in [CLI parameters](https://cursor.com/docs/cli/reference/parameters); `cursor agent --help` may only show a short subcommand summary.
- **`CursorCliAdapter`** — AGENT_TASK: adds **`--model <id>`** when a model is resolved (see below).

## Model selection

Resolution order for **`--model`**:

1. Job payload **`cliModel`** (set when you use a Telegram **`@alias`** that maps in config, or future API fields).
2. Else **`app.cursor.default-model`** (default **`auto`** in `application.yml`). If empty, **`--model` is omitted** and the CLI uses its own default.

Telegram:

- **`/models`** — runs **`cursor … --list-models`** (global flag from [CLI parameters](https://cursor.com/docs/cli/reference/parameters)), not the `agent models` subcommand, so listing does not start an agent run. Copy ids into `telegram-model-aliases`.
- **`/plan @<alias> <prompt>`** and **`/agent @<alias> <prompt>`** — optional first token; **`alias`** must be a key in **`app.cursor.telegram-model-aliases`** (case-insensitive). Otherwise **`@token`** is still treated as a **folder** under the current cwd (existing behavior).
- **Model then folder**: e.g. **`/plan @sonnet @myapp fix bug`** — model from alias, workspace `cwd/myapp`, prompt `fix bug`.

If a folder name collides with an alias key, **the alias wins**; rename the folder or drop the alias.

Illustrative id examples: [agent-models-reference.md](agent-models-reference.md).

## Configuration

| Env / property | Role |
|----------------|------|
| `CURSOR_CLI_BIN` / `app.cursor.executable` | Executable or absolute path for the `cursor` binary (plan mode runs `cursor agent …`). Use when the JVM’s `PATH` differs from an interactive shell (e.g. macOS GUI launch). |
| `CURSOR_CLI_INVOKE` | If `true`, spawn Cursor CLI after writing the task file. |
| `CURSOR_CLI_WORKSPACE` | Workspace directory for the CLI; fallback `AGENT_CLI_WORKSPACE`, then job payload `workspace`, then `AGENT_TASK_WORKSPACE`, then `user.home`. |
| `CURSOR_CLI_PROMPT` | Prompt for **agent** task invocations (not plan-specific). |
| `CURSOR_PLAN_PROMPT` | Suffix appended to the user’s `/plan` text to encourage question formatting. |
| `app.cursor.plan-prefix-args` | List of argv segments after `executable` (default: one `agent`). Use two `agent` entries only if your install requires it. |
| `app.cursor.plan-extra-args` | Optional extra flags before the prompt (same idea as Claude `plan-extra-args`). |
| `app.cursor.default-model` | Cursor **`--model`** when payload has no `cliModel`; default `auto`; blank omits the flag. |
| `app.cursor.telegram-model-aliases` | Map: short Telegram **`@alias`** → exact CLI model id (from **`/models`**). |

See [application.yml](../src/main/resources/application.yml) under `app.cursor` and [config.example.env](../config.example.env).

## Plan vs agent task

| Flow | Adapter | Output |
|------|---------|--------|
| `/agent`, classified AGENT_TASK | `CursorCliAdapter` | Pending JSON + optional long-running CLI stream to job logs. |
| `/plan`, PLAN_TASK | `CursorPlanCliAdapter` | Blocking `stream-json` parse → questions / final plan. |

## Documentation

- [Cursor CLI](https://cursor.com/docs/cli/using) — install and auth.
- [Plan mode](plan-mode.md) — end-user behavior.
