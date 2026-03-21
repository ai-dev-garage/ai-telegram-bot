# ai-telegram-bot

Spring Boot **job runner** with optional **Telegram** bot: shell commands, **AGENT_TASK** handoff to **Cursor** or **Claude Code**, and **plan mode** with Q&A and `.plan.md` export.

**Coordinates:** `com.ai.dev.garage:ai-telegram-bot` (Gradle `group` + JAR base name). Public home: **ai-dev-garage**.

```bash
git clone <repository-url>
cd ai-telegram-bot
cp config.example.env .env
# Set TELEGRAM_BOT_TOKEN, ALLOWED_TELEGRAM_USER_IDS, DB_* if needed
docker compose up -d
./gradlew bootRun
```

- Web UI: [http://localhost:8765](http://localhost:8765) (`RUNNER_PORT`)
- Health: `GET /actuator/health`

## Documentation (start here)

| | |
|---|---|
| **[docs/README.md](docs/README.md)** | Index of all guides |
| **[docs/capabilities.md](docs/capabilities.md)** | What works, Cursor vs Claude, v1 MVP scope |
| **[docs/operator-guide.md](docs/operator-guide.md)** | Install, configuration, Flyway, troubleshooting |
| **[docs/plan-mode.md](docs/plan-mode.md)** | `/plan` flow and export directory |
| **[docs/screenshots-web-ui.md](docs/screenshots-web-ui.md)** | Web UI screenshots |
| **[docs/screenshots-telegram.md](docs/screenshots-telegram.md)** | Telegram screenshots |
| **[docs/agent-cursor.md](docs/agent-cursor.md)** | Cursor CLI + env |
| **[docs/agent-claude.md](docs/agent-claude.md)** | Claude Code CLI + env |
| **[config.example.env](config.example.env)** | Environment variable templates |
| **[`.agent/README.md`](.agent/README.md)** | Agent handoff assets, local dev, architecture |

## Prerequisites (short)

- **JDK 21**, **PostgreSQL 16**, **Telegram** bot token + numeric user id  
- Optional: **Cursor CLI** and/or **Claude Code CLI** for AGENT_TASK / plan automation  
- Full checklist: [docs/operator-guide.md](docs/operator-guide.md)

## Choosing Cursor vs Claude

Set **`AGENT_RUNTIME=cursor`** (default) or **`claude`** in `.env`. Use the same family for `./scripts/install-user-agent-integration.sh --agent …` and `./scripts/install-dev-agent-assets.sh --target …`.

```bash
./scripts/run-telegram-runner-local.sh
./scripts/run-telegram-runner-local.sh --agent claude
```

Details: [docs/agent-cursor.md](docs/agent-cursor.md), [docs/agent-claude.md](docs/agent-claude.md).

## Telegram commands (overview)

- **Navigation:** `/nav`, `/ls`, `/cd`, `/pwd` (needs `ALLOWED_NAVIGATION_PATHS` for cwd picker)
- **Agent:** `/agent …`, `/run …`
- **Todos:** `/todo`, `/todos`, `/todo work …`
- **Plans:** `/plan`, `/plans` — inline Build / Adjust / Pause / Resume / Cancel; see [docs/plan-mode.md](docs/plan-mode.md)
- **Jobs:** `/status`, `/logs`, `/approve`, `/reject`, `/cancel`

More: [docs/operator-guide.md — Telegram commands](docs/operator-guide.md#telegram-commands-summary).

## Contributing

See **[CONTRIBUTING.md](CONTRIBUTING.md)** — branch `feature/<short-description>` → **`master`**, Conventional Commits, `./gradlew test`. **[CHANGELOG.md](CHANGELOG.md)** covers release notes.

## License

[LICENSE](LICENSE) (MIT).
