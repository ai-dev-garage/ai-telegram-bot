# Operator guide

Installation, environment variables, Telegram setup, web UI, and troubleshooting for **ai-telegram-bot**.

For **Cursor vs Claude** wiring, see [agent-cursor.md](agent-cursor.md) and [agent-claude.md](agent-claude.md). For **what is MVP-ready**, see [capabilities.md](capabilities.md).

## Prerequisites

- **JDK 21**
- **PostgreSQL 16** (local or Docker; repo includes `docker-compose.yml`)
- **Telegram account** (bot token + numeric user id)
- Optional: **Cursor CLI** and/or **Claude Code CLI** on the machine that processes AGENT_TASK / plan sessions

## Create the Telegram bot (BotFather)

1. Chat with [@BotFather](https://t.me/BotFather).
2. `/newbot` → choose name and username (must end in `bot`).
3. Copy the **HTTP API token** → `TELEGRAM_BOT_TOKEN` in `.env`.
4. Optional: `/setjoingroups`, `/setprivacy` — for a private ops bot, avoid groups or restrict who can add the bot.
5. Your **numeric Telegram user id** (e.g. `@userinfobot`) → `ALLOWED_TELEGRAM_USER_IDS` (comma-separated for several).

## Quick start

```bash
cp config.example.env .env
# Edit: TELEGRAM_BOT_TOKEN, ALLOWED_TELEGRAM_USER_IDS, DB_* if needed

docker compose up -d
./gradlew bootRun
```

Web UI (default): [http://localhost:8765](http://localhost:8765) (`RUNNER_PORT`).

Helper script (loads `.env`):

```bash
./scripts/run-telegram-runner-local.sh
./scripts/run-telegram-runner-local.sh --agent claude
```

Example **web UI** and **Telegram** captures (for docs / marketing): [screenshots-web-ui.md](screenshots-web-ui.md), [screenshots-telegram.md](screenshots-telegram.md).

## Installation checklist

1. Clone repo, **JDK 21**.
2. `.env` from `config.example.env` (Telegram, DB, optional `RUNNER_AUTH_TOKEN`, `ALLOWED_NAVIGATION_PATHS`, task auth). Optional: with profile **`local`**, copy `src/main/resources/application-local.example.yml` → **`application-local.yml`** (gitignored) for extra Spring overrides.
3. Start Postgres (`docker compose up -d`) or set `DB_URL` / `DB_USER` / `DB_PASSWORD`.
4. Run `./gradlew bootRun` or `./gradlew bootJar` and deploy the JAR (set JVM heap yourself for `java -jar`).
5. For AGENT_TASK: `./scripts/install-user-agent-integration.sh --agent cursor|claude` on the worker machine; mirror `TASK_AUTH_*` with `.env`.

## JVM heap (Spring Boot process)

Defaults: **`bootRunJvmXms` / `bootRunJvmXmx`** in **`gradle.properties`** (typically **1 g** each) apply to **`bootRun`** only.

This is **not** **`org.gradle.jvmargs`** (Gradle Daemon only).

Overrides: `./gradlew bootRun -PbootRunJvmXmx=2g`, or **`BOOTRUN_JVM_XMS` / `BOOTRUN_JVM_XMX`** with **`run-telegram-runner-local.sh`**.

## Choosing Cursor vs Claude

- **`AGENT_RUNTIME=cursor`** or **`claude`** in `.env`.
- Align **`install-user-agent-integration.sh --agent`** and **`install-dev-agent-assets.sh --target`** with the same family.
- Details: [agent-cursor.md](agent-cursor.md), [agent-claude.md](agent-claude.md).

## Configuration (summary)

Full placeholders: **[config.example.env](../config.example.env)**.

| Variable | Description |
|----------|-------------|
| `AGENT_RUNTIME` | `cursor` (default) or `claude` |
| `AGENT_TASKS_DIR` | Pending/result JSON root (default `~/.ai-dev-garage/agent-tasks`) |
| `TASK_AUTH_SECRET_PHRASE` | Trust phrase in pending `intent` (with marker) |
| `TASK_AUTH_HMAC_SECRET` | HMAC-SHA256 key for `signature` on pending JSON |
| `TASK_SOURCE_ID` | Must match JSON `source` (default `ai-telegram-bot`) |
| `CURSOR_CLI_*` / `CLAUDE_CLI_*` | See agent docs |
| `AGENT_CLI_WORKSPACE` | Shared fallback workspace |
| `ALLOWED_NAVIGATION_PATHS` | Absolute roots for `/nav`, `/ls`, `/cd`, shell cwd |
| `RUNNER_AUTH_TOKEN` | Optional Bearer token for HTTP API |
| `PLANS_EXPORT_DIR` | Exported `.plan.md` files (default `~/.ai-dev-garage/plans`) |

Trust marker line defaults to `[TASK_RUNNER_TRUSTED]`; override via `app.runner.task-trust-marker-line` or `APP_RUNNER_TASK_TRUST_MARKER_LINE`.

### Task auth secrets

Mirror **`TASK_AUTH_SECRET_PHRASE`**, **`TASK_AUTH_HMAC_SECRET`**, **`TASK_SOURCE_ID`** on the runner and in the IDE where **process-pending-agent-task** runs. See [.agent/commands/process-pending-agent-task.md](../.agent/commands/process-pending-agent-task.md).

Generate HMAC secret:

```bash
openssl rand -hex 32
```

Never commit real secrets; use `.env` or a secret manager.

## Web UI and HTTP API

- **`/ui`**, **`/ui/jobs`**, **`/ui/todos`** — Thymeleaf; **`/`** redirects to **`/ui`**.
- REST: `/jobs`, `/todos` — see `adapter.in.rest`.

Health: **`GET /actuator/health`**.

## Telegram commands (summary)

| Area | Commands |
|------|----------|
| Navigation | `/nav`, `/ls`, `/cd`, `/pwd` |
| Agent | `/agent`, `/agent @folder`, **`/models`** (Cursor CLI model ids) |
| Todos | `/todo`, `/todos`, `/todo work`, etc. |
| Plans | `/plan`, `/plans`, inline Build/Adjust/Pause/Resume/Cancel |
| Jobs | `/run`, `/status`, `/logs`, `/approve`, `/reject`, `/cancel` |

Full detail (button behavior, debugging): see the slim [README](../README.md#telegram-commands) or browse [Plan mode](plan-mode.md).

**Debugging Telegram:** failures log at **WARN**. Set **`LOG_LEVEL_APP_ROOT=DEBUG`** (see `logback-spring.xml` / `config.example.env`) for dispatch and callback traces.

## Reset local Postgres (Flyway checksum mismatch)

If startup fails with **checksum mismatch** for migration **V1**, the Docker volume may hold an older schema history:

```bash
docker compose down -v
docker compose up -d
```

**`down -v` deletes all data** in that volume — dev only.

## Startup logs (normal vs fix)

| Log | Meaning |
|-----|--------|
| Flyway “schema history does not exist” on first run | Expected on empty DB. |
| Flyway **checksum mismatch** V1 | See [Reset local Postgres](#reset-local-postgres-flyway-checksum-mismatch). |
| Spring Integration “No bean named 'errorChannel'” | Informational. |
| Tomcat started / `Started RunnerApplication` | Healthy. |
| **macOS** Netty `MacOSDnsServerAddressStreamProvider` / `UnsatisfiedLinkError` | Native DNS JAR: `./gradlew clean bootRun` on Mac so the classifier dependency resolves. |

## Database migrations

The schema ships as a **single** Flyway script:

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__init_runner_tables.sql` | All enums, `jobs`, `job_logs`, `job_events`, `plan_sessions`, `plan_questions`, `todos`, indexes |

Scripts live under `src/main/resources/db/migration/`.

If Flyway reports a **checksum mismatch** for **V1** after changing branches or images, use a fresh dev database (see [Reset local Postgres](#reset-local-postgres-flyway-checksum-mismatch)).

## Agent integration (install scripts)

```bash
./scripts/install-user-agent-integration.sh --agent cursor
# or claude
```

Canonical rules/commands: **[`.agent/`](../.agent/)**.

## Developer / contributor setup

See **[CONTRIBUTING.md](../CONTRIBUTING.md)** and **[`.agent/README.md`](../.agent/README.md)** (branch `feature/…` → `master`, tests, IDE rules).

```bash
./scripts/install-dev-agent-assets.sh --target cursor
./gradlew installDevAgentAssets -PdevAgent=claude
```
