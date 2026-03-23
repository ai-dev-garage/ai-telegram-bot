# Agent model identifiers (reference)

Authoritative **`--model` strings** come from your installed CLI — use Telegram **`/models`** (runs **`--list-models`**) or e.g. **`cursor agent --list-models`** locally. The tables below are **illustrative only**; names change with Cursor/Claude releases.

## Cursor Agent CLI

- Configure a default: `app.cursor.default-model` (e.g. `auto`).
- Optional Telegram aliases: `app.cursor.telegram-model-aliases` maps short names (e.g. `sonnet`) to the exact id from `/models`.
- See [CLI parameters](https://cursor.com/docs/cli/reference/parameters) (`--model`, `--list-models`).

| Illustrative Telegram alias | Example `--model` id (verify with `/models`) |
|----------------------------|-----------------------------------------------|
| `sonnet` | (paste from CLI output) |
| `opus` | (paste from CLI output) |
| `auto` | `auto` (if your CLI accepts it) |

## Claude Code CLI

When **`AGENT_RUNTIME=claude`**, plan/agent invocations use Claude’s CLI, not Cursor’s `--model`. Model selection follows [agent-claude.md](agent-claude.md). Telegram **`/models`** may reply with a pointer to Claude docs if listing is not wired for that runtime.

## Collision note

If a **folder** name under your cwd equals a **telegram-model-aliases** key, the alias wins and the token is treated as a model, not a workspace folder. Rename the folder or remove the alias.
