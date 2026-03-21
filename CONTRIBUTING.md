# Contributing to ai-telegram-bot

Thank you for helping improve this project.

## Workflow

1. **Fork** this repository (if you do not have push access).
2. **Create a branch** named `feature/<short-description>` (use lowercase kebab-case, e.g. `feature/add-metrics-endpoint`). Do not put secrets or tokens in the branch name.
3. **Make changes** in focused commits. Keep pull requests small and reviewable.
4. **Run tests locally** before opening a PR:
   ```bash
   ./gradlew test
   ```
   Tests use **JUnit 5**, **Mockito** (and **Testcontainers** where integration coverage is needed). Prefer precise stubs/verifies (`eq`, captors) when writing or refactoring tests — see [.agent/rules/dev/testing.mdc](.agent/rules/dev/testing.mdc).
5. **Open a pull request** targeting the **`master`** branch.
6. **Wait for review** and for CI checks to pass (branch name + tests).

## Project layout (hexagonal)

- **`domain`** — core models and enums
- **`application`** — ports (interfaces) and services / orchestration
- **`adapter`** — inbound (REST, Telegram, schedulers) and outbound (persistence, CLI, filesystem)

Keep controllers thin; put behavior in application/domain layers.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/) for the subject line when you can, for example:

- `feat: add job cancellation API`
- `fix: handle empty intent in classifier`
- `docs: update README quick start`
- `test: cover shell cwd validation`
- `chore: bump Spring patch version`

One logical change per commit is ideal.

## Pull request description

- **What** changed and **why** (motivation or bug).
- Link related issues if any (`Fixes #12`).
- Note any operator-facing changes (config, env vars, migrations) so **`config.example.env`**, **`docs/*.md`**, and the root **`README.md`** stay in sync.

## Secrets and local config

- Never commit **`.env`**, API tokens, or real database passwords.
- Use **`config.example.env`** for documented placeholders only.
- **`src/main/resources/application-local.yml`** is **gitignored** (Spring profile `local`). Use **`application-local.example.yml`** as a template; keep real overrides only in your local copy.

## Documentation map

Operator and agent CLI details live under **`docs/`** (see **[docs/README.md](docs/README.md)**). When you change behavior or env vars, update:

- **`config.example.env`** (placeholders)
- Relevant **`docs/*.md`** and the slim root **`README.md`** (links only, when possible)

## Agent / IDE rules (optional)

After clone, you can install local Cursor/Claude rules from this repo (match **`AGENT_RUNTIME`** / your IDE):

```bash
./scripts/install-dev-agent-assets.sh --target cursor
./scripts/install-dev-agent-assets.sh --target claude
# or: ./gradlew installDevAgentAssets -PdevAgent=claude
```

See **[`.agent/README.md`](.agent/README.md)** (local run, Telegram checklist, troubleshooting, architecture summary) and [`.agent/rules/dev/contribution.mdc`](.agent/rules/dev/contribution.mdc) for automation-friendly contributions.

## Branch protection (maintainers)

On GitHub, for **`master`**, enable required status checks for the **PR checks** workflow (`branch-name`, `build`) before merging, plus required reviews as appropriate for your org.
