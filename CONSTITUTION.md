<!--
Sync Impact Report:
- Version change: initial creation → 1.0.0
- Principles added: 6 core principles (Hexagonal Architecture, Agent Agnosticism, Human in the Loop,
  Testability, Event-Driven Orchestration, Code Quality Gates)
- Package structure section added
- Follow-up TODOs: None

- Version change: 1.0.0 → 1.0.1
- Principle 1 amended: services must implement primary port interfaces; consumers depend on interfaces
- Principle 5 amended: @Transactional + @TransactionalEventListener prohibition; self-invocation warning
- Principle 6 amended: Error Prone added to quality gate list
- Trigger: PR #4 code review — found missing port interface, transactional footgun, unlisted gate

- Version change: 1.0.1 → 1.1.0
- Principle 7 added: Expressive Java Style — Stream/Optional over loops, chain formatting, lambda
  extraction, @UtilityClass
- Trigger: PR #4 code review — recurring style comments on method chaining, lambda size, loop style
-->

# AI-TELEGRAM-BOT Constitution

**Version**: 1.1.0
**Ratified**: 2026-04-04
**Last Amended**: 2026-04-04

## Purpose

This constitution defines the core principles and governance rules for AI-assisted development in the
ai-telegram-bot project. It serves as the foundational contract between human developers and AI agents,
ensuring consistency, quality, and alignment with project goals.

The project is a Spring Boot job runner with an optional Telegram bot interface. It orchestrates
multi-step workflows by delegating shell commands and agentic tasks to external CLI runtimes
(Cursor, Claude Code), exposing a REST API and a Thymeleaf web UI for job monitoring.

## Core Principles

### Principle 1: Hexagonal Architecture (Ports and Adapters)

The project MUST follow the Hexagonal Architecture (Ports and Adapters) pattern. Domain and
application layers must not import from adapter implementations. All cross-layer communication
goes through ports defined in `application/port/in` (primary) and `application/port/out`
(secondary). New integrations are added as new adapters only, never by modifying domain or
application logic.

Application service classes MUST implement a corresponding primary port interface from
`application/port/in/`. Consumers (adapters, other services) MUST depend on the port interface,
not the concrete service class.

**Rationale**: Clean boundaries between business logic and external dependencies enable independent
testing and the ability to swap or add adapters (e.g., a new CLI runtime or a new notification
channel) without touching core business rules. Depending on interfaces rather than concrete
services ensures that adapter code cannot accidentally couple to implementation details.

### Principle 2: Agent Agnosticism

AI runtimes (Cursor, Claude Code, or future runtimes) are interchangeable implementations of
`AgentTaskRuntime`, `PlanCliRuntime`, and `WorkflowPlannerRuntime` ports. The application layer
MUST NOT reference any specific CLI tool or vendor. Hybrid and multi-runtime configurations are
wired exclusively in `AgentRuntimeConfiguration`; runtime selection logic belongs nowhere else.

**Rationale**: Prevents vendor lock-in and enables the hybrid mode (different agents for planning
vs. execution). Adding a new runtime is a pure adapter concern.

### Principle 3: Human in the Loop for Risky Operations

High-risk jobs (critical workflow steps, shell commands matching `require_approval_patterns`, and
`RiskLevel.HIGH` agent tasks) MUST pass through an explicit approval gate (`ApprovalState.PENDING`)
before execution. This gate MUST NOT be bypassed programmatically or via configuration.

**Rationale**: The bot executes real shell commands and agentic tasks that can modify production
systems. The approval gate is the primary safety mechanism; weakening it must be a deliberate,
reviewed governance decision.

### Principle 4: Testability

Each layer MUST be independently testable:
- Application services and domain classes: `@ExtendWith(MockitoExtension.class)` unit tests, no
  Spring context required.
- REST adapters: `MockMvc`-based slice tests.
- Persistence adapters: Testcontainers or H2 integration tests.
- Target coverage: ≥ 80 % for `application/` and `domain/` packages.

**Rationale**: Fast, isolated tests provide a safety net for refactoring, enable CI/CD confidence,
and reduce the feedback loop during development.

### Principle 5: Event-Driven Orchestration

Inter-service reactions to job state changes MUST be expressed as Spring application events
(`JobTerminalEvent`, `WorkflowPlanReadyEvent`). Event listeners MUST be annotated with
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` so they execute only after the
publishing transaction commits, in a fresh transaction. Synchronous `@EventListener` is prohibited
for workflow lifecycle events.

Event listener methods annotated with `@TransactionalEventListener` MUST NOT also carry
`@Transactional` — Spring Boot's `RestrictedTransactionalEventListenerFactory` rejects this
combination at startup. If the listener needs transactional work, it MUST delegate to a separate
Spring bean so that the call crosses the AOP proxy boundary. Calling a `@Transactional` method on
the same bean (self-invocation) bypasses the proxy and the annotation has no effect.

**Rationale**: Firing listeners inside the publishing transaction causes inconsistent reads (the
child job's data is not yet visible to other sessions) and risks re-entrant event publishing.
AFTER_COMMIT listeners ensure the DB state is stable before orchestration logic acts on it. The
`@Transactional` prohibition and self-invocation warning prevent silent transaction mismanagement
that compiles and appears to work but breaks under load or concurrent access.

### Principle 6: Code Quality Gates

All code MUST pass the configured Checkstyle, PMD, SpotBugs, and Error Prone gates
(`config/checkstyle/`, `config/pmd/`, `config/spotbugs/`; Error Prone runs as a `compileJava`
compiler plugin). Static analysis suppressions require an inline comment explaining the
justification. Code style follows `.editorconfig` (4-space Java indent, 120-char line limit).

**Rationale**: Automated quality gates enforce consistency, catch common defects early, and reduce
the cognitive load of code review.

### Principle 7: Expressive Java Style

Code MUST prefer the Java Stream and Optional APIs over imperative loops and null checks for
collection transformations, lookups, and conditional chains:

- **Stream over loop**: use `stream().filter().map().collect()` or `IntStream.range()` instead of
  `for` / `for-each` loops when the operation is a pure transformation (filter, map, collect,
  reduce). Imperative loops remain appropriate for side-effecting iterations or when readability
  suffers.
- **Optional chaining**: use `findById(...).ifPresent(...)`, `.map(...)`, `.orElseThrow(...)` rather
  than `if (x != null)` / `if (optional.isPresent()) { ... optional.get() }`.
- **Chain formatting**: when a method chain exceeds one operation, break each chained call onto its
  own line, aligned under the receiver:
  ```java
  jobStore.findById(id)
      .ifPresent(job -> notify(job));
  ```
- **Lambda extraction**: extract lambdas longer than 2–3 statements into named private methods. The
  method name documents intent; the call site stays scannable.
- **Utility classes**: use Lombok `@UtilityClass` instead of manual `final class` + private
  constructor (add a Checkstyle suppression for `HideUtilityClassConstructor` when needed).
- **Compact expressions**: single-expression ternaries, `Collectors.toMap(...)`, and similar
  one-liners SHOULD fit on a single line when they stay within the 120-char limit. Do not break a
  simple expression across lines just because it could be broken.

**Rationale**: Declarative pipelines make data flow explicit, reduce mutable state, and align with
the idioms expected by modern Java (21+) reviewers. Consistent formatting of chains and extracted
lambdas keeps methods short and intention-revealing, reducing review friction.

## Package Structure

The project is a single-module Spring Boot application organised by hexagonal layer:

- **`domain/`** — JPA entities and value objects (`Job`, `WorkflowGraph`, `WorkflowStep`, enums).
  No framework annotations beyond JPA mapping.
- **`application/port/in/`** — Primary port interfaces (`JobManagement`, `PlanManagement`,
  `TodoManagement`, etc.). These are the use-case API.
- **`application/port/out/`** — Secondary port interfaces (`JobStore`, `AgentTaskRuntime`,
  `WorkflowPlannerRuntime`, `JsonCodec`, etc.).
- **`application/service/`** — Use-case implementations. May inject ports only; must not import
  adapter classes.
- **`adapter/in/`** — Inbound adapters: `scheduler/` (RunnerWorker), `rest/` (REST API),
  `telegram/` (Telegram bot), `web/` (Thymeleaf UI), `filewatcher/` (agent result watcher).
- **`adapter/out/`** — Outbound adapters: `cursor/`, `claude/`, `execution/`, `persistence/`,
  `agenttask/`, `filesystem/`, `policy/`.
- **`config/`** — Spring `@Configuration` classes. `AgentRuntimeConfiguration` is the single place
  where runtime adapter beans are conditionally wired.

**Rationale**: Explicit package boundaries enforce the Ports and Adapters discipline and make
architectural violations visible at a glance.

## Governance

### Amendment Procedure

1. Proposed amendments must be discussed with a clear rationale.
2. Version must be incremented following semantic versioning:
   - **MAJOR**: Breaking changes to principles or governance structure
   - **MINOR**: New principle or significant structural addition
   - **PATCH**: Clarifications, wording improvements, non-semantic fixes
3. All dependent templates and AI agent rules (`.agent/rules/`) must be updated to stay consistent.
4. The `Last Amended` field must be updated to the ISO date of the change.

### Compliance

- All AI agents working on this project MUST adhere to the principles defined in this constitution.
- Human developers are responsible for ensuring AI-generated code and decisions align with these
  principles.
- Pull requests that violate a principle require an explicit amendment to this constitution before
  they can be merged.

### Versioning Policy

This constitution follows semantic versioning (MAJOR.MINOR.PATCH). Each version change must be
documented with:
- A clear description of what changed
- Rationale for the change
- Impact on existing adapters, templates, or agent rules

## Maintenance

This constitution should be reviewed and updated:
- When a new agent runtime or significant adapter type is introduced
- When the job lifecycle (states, transitions) changes materially
- When team composition or development workflow evolves
- At minimum, with each major release

---

*This constitution was created using the ai-dev-garage workflow framework.*
