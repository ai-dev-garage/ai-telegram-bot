# Claude Code CLI integration

When **`AGENT_RUNTIME=claude`**, the runner uses:

- **`ClaudeCliAdapter`** — AGENT_TASK jobs: writes pending JSON, optionally spawns **`claude`** using `app.claude.launch-command` + `prompt` from **`CLAUDE_CLI_PROMPT`**, with working directory from `CLAUDE_CLI_WORKSPACE` (same resolution chain as Cursor via `CliWorkspaceResolver`).
- **`ClaudePlanCliAdapter`** — plan mode: print/headless invocations aligned with official docs:
  - **Start:** `claude -p "<full prompt>" --output-format stream-json [--permission-mode <mode>] …`
  - **Resume:** `claude -p "<user message>" --output-format stream-json … --resume <cliSessionId>`

Do **not** assume the same flags as Cursor: Cursor uses **`--plan`**; Claude Code uses **[permission modes](https://code.claude.com/docs/en/permissions)** (e.g. **`--permission-mode plan`**).

## Official references

- [CLI reference](https://code.claude.com/docs/en/cli-reference)
- [Headless / `claude -p`](https://code.claude.com/docs/en/headless)
- [Streaming `stream-json`](https://code.claude.com/docs/en/headless#get-structured-output) (and optional `--include-partial-messages`)

## Configuration

| Env / property | Role |
|----------------|------|
| `CLAUDE_CLI_INVOKE` | If `true`, spawn Claude after writing the task file. |
| `CLAUDE_CLI_WORKSPACE` | Process working directory for `claude`. |
| `CLAUDE_CLI_PROMPT` | Prompt appended after `launch-command` for **agent** tasks. |
| `CLAUDE_PLAN_PROMPT` | Suffix appended to the user `/plan` text (parity with Cursor). |
| `CLAUDE_PLAN_PERMISSION_MODE` | Default `plan`; set empty in YAML to omit `--permission-mode`. |
| `CLAUDE_PLAN_DANGEROUSLY_SKIP_PERMISSIONS` | If `true`, adds `--dangerously-skip-permissions` for **plan** invocations only. **Dangerous** — trusted environments only. |
| `CLAUDE_PLAN_INCLUDE_PARTIAL_MESSAGES` | If `true`, adds `--include-partial-messages` so NDJSON may contain `stream_event` lines; `CliStreamParser` accumulates `text_delta` chunks. |

**Extra argv for plan only** (e.g. `--allowedTools` patterns): add a list under `app.claude.plan-extra-args` in YAML (no single env var — copy `application-local.example.yml` → `application-local.yml`, or use `SPRING_APPLICATION_JSON` if needed). `application-local.yml` is gitignored.

## Stream parsing note

`CliStreamParser` supports:

- Cursor-style `type: assistant` / `type: result` lines.
- Claude **`stream_event`** lines with `text_delta` (fixtures in `CliStreamParserTest`; real CLI output may vary by version).

If parsing breaks after a Claude upgrade, open an issue with a **redacted** NDJSON sample and **CLI version**.

## Plan vs agent task

| Flow | Adapter | Output |
|------|---------|--------|
| `/agent`, AGENT_TASK | `ClaudeCliAdapter` | Pending JSON + optional CLI stream to job logs. |
| `/plan`, PLAN_TASK | `ClaudePlanCliAdapter` | Blocking `stream-json` → questions / final plan. |

See [Plan mode](plan-mode.md) and [Capabilities](capabilities.md).
