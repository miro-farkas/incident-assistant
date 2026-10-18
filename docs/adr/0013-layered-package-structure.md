# ADR-0013: Layered package structure

- Status: Accepted
- Date: 2026-10-18

## Context
The code was organised package-by-feature (`workflow`, `outbox`, …) with JPA entities in a
`persistence` subpackage of each feature (CLAUDE.md, code conventions). The maintainer wants
a layered structure, where each package holds one kind of class, so that the responsibilities
of a class are visible from its package and the allowed dependencies between layers can be
checked mechanically. The application is a single deployable service with a small domain,
so feature isolation buys little today.

## Decision
- Packages are split by layer, once for the whole module, under the package root
  `dev.mfa.incident` (and `dev.mfa.incident.opstools` in `ops-tools-mcp`):

  | Package | Contains |
  |---|---|
  | `model` | JPA entities (logic-free, see ADR-0014), `BaseEntity`, and the enums persisted in them (`IncidentState`, `StepType`, …) |
  | `repository` | Data access objects; the only classes that use `EntityManager` or queries |
  | `service` | Business logic and transactions: services, the state machine, validation of transitions |
  | `exception` | Domain exceptions; thrown by services and repositories (a repository translates an optimistic-lock failure into `IncidentVersionConflictException`), mapped to HTTP by resources |
  | `dto` | Immutable records passed into and returned from services, and published message payloads (e.g. `IncidentStateChanged`) |
  | `mapper` | Hand-written entity ↔ DTO mappers |
  | `resource` | REST resources (JAX-RS); added with the first endpoint (INC-009) |
  | `messaging` | Kafka consumers and the outbox relay; added with INC-003 |
  | `config` | `@ConfigMapping` interfaces and CDI producers |
  | `health` | Health checks |

  Packages are created when the first class needs them; no empty placeholders.
- Allowed dependencies (enforced with ArchUnit `layeredArchitecture()`):
  - `resource`, `messaging`, `health` → `service`, `dto`, `config`, `exception`.
  - `service` → `repository`, `mapper`, `model`, `dto`, `config`, `exception`.
  - `repository` → `model`, `exception`.
  - `mapper` → `model`, `dto`.
  - `dto` → enums in `model` only.
  - `exception` → enums in `model` only.
  - `model` → nothing in the application.
  - Public service methods take and return no entities (ADR-0014).
  - Nothing outside `repository` and `model` uses `jakarta.persistence` or Hibernate ORM.
  - `resource` and `messaging` never touch `repository` or `model` entities.
- Entry points (`resource`, `messaging`) contain no business logic; they translate between
  the transport and service DTOs (existing CLAUDE.md rule, unchanged).
- Later code (AI services, guardrails, MCP clients from INC-004 onwards) gets its package in
  the ADR or backlog item that introduces it, following the same layering.
- This supersedes the package-by-feature convention in CLAUDE.md, and the "package-by-feature
  boundaries" ArchUnit rules in ADR-0010 become layer rules.

## Alternatives considered
- Keep package-by-feature: rejected by the maintainer in favour of explicit layers.
- Feature first, then layer (`workflow.service`, `workflow.repository`, …): keeps features
  separable for a later split into modules, but doubles the package count for a domain this
  size and makes layer rules harder to express. Reconsider if a feature is extracted into its
  own module or service.
- Separate domain model plus entities (hexagonal / ports and adapters): logic lives in
  framework-free domain objects and entities are a persistence detail. Clean, but adds a
  second model and a second mapping for every type; not justified at this size.

## Consequences
- Every existing class moves package; the INC-002 code is refactored in one change, with no
  behavioural change and the existing tests kept green.
- Classes of one feature are spread over several packages; naming (`Incident…`, `Outbox…`)
  keeps them findable.
- CLAUDE.md code conventions and the ArchUnit tests are updated with the refactor.
- Not decided here: the shape of entities, DTOs and the base entity (ADR-0014).
