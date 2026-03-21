# `.agent`: operator + contributor reference

This directory is the **canonical home** for agent handoff assets (Cursor / Claude) and **developer rules**. It also summarizes how to run the runner locally, wire Telegram, and fix common issues.

- **Short onboarding:** root **[README.md](../README.md)**
- **Operators (env, Telegram, troubleshooting):** **[docs/operator-guide.md](../docs/operator-guide.md)**
- **Capabilities / MVP / Cursor vs Claude:** **[docs/capabilities.md](../docs/capabilities.md)**
- **Doc index:** **[docs/README.md](../docs/README.md)**

---

## Purpose

- **Humans:** quick path from clone → Postgres → `.env` → `bootRun` → optional AGENT_TASK integration.
- **IDE agents:** same flow, plus where rules live and how hexagonal packages are meant to be used.
- **Canonical operator copy:** [docs/operator-guide.md](../docs/operator-guide.md) (prerequisites, config, API overview).

---

## Prerequisites

- **JDK 21** (matches `javaLanguageVersion` in `gradle.properties`).
- **PostgreSQL 16** — local install or **Docker** (this repo ships `docker-compose.yml`).
- **Telegram account** — bot token from BotFather and your numeric user id(s) for the allowlist.
- **AGENT_TASK automation (optional):** **Cursor CLI** and/or **Claude Code CLI** on the machine that processes pending tasks.

More: [docs/operator-guide — Prerequisites](../docs/operator-guide.md#prerequisites).

---

## Telegram bot setup (checklist)

1. Chat with [@BotFather](https://t.me/BotFather) → `/newbot` → copy **HTTP API token** → `TELEGRAM_BOT_TOKEN` in `.env`.
2. Optional: `/setjoingroups`, `/setprivacy` — for a private ops bot, keep it out of groups or restrict who can add it.
3. Get your **numeric Telegram user id** (e.g. `@userinfobot`) → `ALLOWED_TELEGRAM_USER_IDS` (comma-separated for several).

Step-by-step: [docs/operator-guide — BotFather](../docs/operator-guide.md#create-the-telegram-bot-botfather).

---

## Local run

1. `cp config.example.env .env` and set at least `TELEGRAM_BOT_TOKEN`, `ALLOWED_TELEGRAM_USER_IDS`, and DB vars if not using defaults.
2. Start Postgres: `docker compose up -d` (or point `DB_*` at your instance).
3. Run the app:
   - `./gradlew bootRun` (app heap: **`bootRunJvmXms` / `bootRunJvmXmx`** in `gradle.properties`, default **1 g**; not `org.gradle.jvmargs`, which is the Gradle Daemon only), or
   - `./scripts/run-telegram-runner-local.sh` (loads `.env`; optional **`BOOTRUN_JVM_XMS` / `BOOTRUN_JVM_XMX`** override heap via `-P…`; optional `--agent cursor|claude` overrides `AGENT_RUNTIME`).
4. Default web UI / API: [http://localhost:8765](http://localhost:8765) (`RUNNER_PORT`).
5. Health: `GET /actuator/health`.

Choosing **Cursor vs Claude**: [docs/agent-cursor.md](../docs/agent-cursor.md) and [docs/agent-claude.md](../docs/agent-claude.md).

---

## Local development

- **Tests:** `./gradlew test` (JUnit 5, Mockito, Testcontainers — see [.agent/rules/dev/testing.mdc](rules/dev/testing.mdc)).
- **IDE rules:** after clone, materialize dev rules into `.cursor` or `.claude` (gitignored):

  ```bash
  ./scripts/install-dev-agent-assets.sh --target cursor
  # or: ./gradlew installDevAgentAssets  (-PdevAgent / DEV_AGENT / AGENT_RUNTIME)
  ```

- **Gradle:** plugin and toolchain versions are centralized in **[gradle.properties](../gradle.properties)**; do not add version literals in `build.gradle` for plugins or explicit deps (see [.agent/rules/dev/gradle.mdc](rules/dev/gradle.mdc)).
- **Branch / PR workflow:** [CONTRIBUTING.md](../CONTRIBUTING.md) — `feature/<short-description>` → `master`.

---

## Architecture (`com.ai.dev.garage.bot`)

- **`domain`** — Entities/value types, enums, domain-only logic. **No** Spring or framework imports.
- **`application`** — **`port.in`** (use cases / queries facades), **`port.out`** (repositories, runtimes, appenders), **services** and orchestration. Depends on domain; not on adapter implementations.
- **`adapter.in`** — Inbound: REST, Telegram, web (Thymeleaf), schedulers, file watchers. **Thin:** map HTTP/Telegram to application ports; **no** business rules here.
- **`adapter.out`** — Outbound: JPA, CLI (Cursor / Claude), filesystem (agent tasks), policy YAML, etc.
- **`config`** — Spring `@ConfigurationProperties` and wiring that does not belong in a port.

**Do not:** put core business rules in controllers or Telegram handlers; leak JPA types into `application` or `domain`; skip Flyway for schema changes (add a new script under `src/main/resources/db/migration/`).

---

## Contributing and tests

- Non-trivial **behavior changes** should include or update **automated tests**.
- Stack: **JUnit 5**, **Mockito** (`@ExtendWith(MockitoExtension.class)`; `@MockBean` on slice tests such as `@WebMvcTest`); **Testcontainers** for integration tests.
- Stubs and verifies should prefer **concrete values**, **`eq(...)`**, and **`ArgumentCaptor`** over broad **`any*`** matchers when you touch tests (see **testing** rule).
- **Legacy suite:** many existing test method names and some `any()` usages predate these conventions; **new or heavily refactored tests** should follow [.agent/rules/dev/testing.mdc](rules/dev/testing.mdc).

Full contributor workflow: [CONTRIBUTING.md](../CONTRIBUTING.md).

---

## Troubleshooting

| Symptom | Things to check |
|--------|------------------|
| DB / Flyway errors | `docker compose` up; `DB_URL` / `DB_USER` / `DB_PASSWORD` match [docker-compose.yml](../docker-compose.yml); Postgres reachable on expected port. |
| Bot does not respond | `TELEGRAM_ENABLED=true` in `.env`; valid `TELEGRAM_BOT_TOKEN`; your Telegram user id in `ALLOWED_TELEGRAM_USER_IDS`. |
| Telegram “nothing happens” / navigation | Use **`/nav`** or **`/navigation`** (not `/navigate`). Set **`ALLOWED_NAVIGATION_PATHS`** for cwd picker. Check app logs for **WARN** from Telegram API; use **`LOG_LEVEL_APP_ROOT=DEBUG`** ([config.example.env](../config.example.env)) for dispatch and callback traces. Unknown text gets the same help as **`/start`**. |
| Port in use | Change `RUNNER_PORT` or stop the other process. |
| AGENT_TASK stuck / trust failures | Same `AGENT_TASKS_DIR`, `TASK_AUTH_*`, `TASK_SOURCE_ID` on runner and in IDE; correct `AGENT_RUNTIME` (`cursor` vs `claude`); CLI on PATH. |
| Wrong agent CLI | `AGENT_RUNTIME` and `CURSOR_CLI_*` vs `CLAUDE_CLI_*` aligned; see [config.example.env](../config.example.env). |
| Netty **ERROR** `MacOSDnsServerAddressStreamProvider` / `UnsatisfiedLinkError` (macOS) | See [docs/operator-guide — Startup logs](../docs/operator-guide.md#startup-logs-normal-vs-fix): native DNS JAR when building on Mac; `./gradlew clean bootRun` / refresh deps. |

---

## Agent task handoff assets (versioned)

Canonical copies of rules and commands used with **Cursor** and **Claude Code** when driving `AGENT_TASK` handoffs from the runner.

| Path | Purpose |
|------|---------|
| `rules/bot-agent-task-guardrails.mdc` | Trust guardrails (Cursor `~/.cursor/rules/` as-is; Claude `~/.claude/rules/*.md` with YAML frontmatter **stripped** at install — see `scripts/lib/strip_cursor_rule_frontmatter.sh`) |
| `commands/process-pending-agent-task.md` | Cursor user command template (copy to `~/.cursor/commands/`) |
| `rules/dev/*.mdc` | Developer rules for **this repo** (Cursor as-is under `.cursor/rules/`; Claude as `.md` under `.claude/rules/` with frontmatter stripped) |

**Do not commit** generated `./.cursor/` or `./.claude/` — they are gitignored. Use:

- `scripts/install-dev-agent-assets.sh` — materialize dev rules into the project
- `scripts/install-user-agent-integration.sh` — install guardrails + command under your home directory

Use the **same** agent family (`cursor` vs `claude`) for **`AGENT_RUNTIME`** on the runner, **`install-user-agent-integration.sh --agent`**, and **`install-dev-agent-assets.sh --target`** (or `./gradlew installDevAgentAssets` with `-PdevAgent` / `DEV_AGENT` / `AGENT_RUNTIME`).

More on env and security: [docs/agent-cursor.md](../docs/agent-cursor.md), [docs/agent-claude.md](../docs/agent-claude.md), [config.example.env](../config.example.env).
