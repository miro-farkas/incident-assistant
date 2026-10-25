# P-0013: Layered package structure

- Status: Accepted (amended with A-0014: `ai` package)
- Date: 2026-10-18

## Context
Each package should hold one kind of class, so that the responsibility of a class is
visible from its package and the allowed dependencies between layers can be checked
mechanically. The services this applies to are single deployables with a small domain, so
feature isolation inside one module buys little.

## Decision
- Packages are split by layer, once per module, under the module's package root:

  | Package | Contains |
  |---|---|
  | `model` | JPA entities (logic-free, P-0014), `BaseEntity`, and the enums persisted in them |
  | `repository` | Data access objects; the only classes that use `EntityManager` or queries |
  | `service` | Business logic and transactions, including state machines and validation of transitions |
  | `exception` | Domain exceptions; thrown by services and repositories (a repository translates an optimistic-lock failure into a domain conflict exception), mapped to HTTP by resources |
  | `dto` | Immutable records passed into and returned from services, and published message payloads |
  | `mapper` | Hand-written entity ↔ DTO mappers |
  | `resource` | REST resources (JAX-RS) |
  | `messaging` | Kafka consumers and the outbox relay |
  | `config` | `@ConfigMapping` interfaces and CDI producers |
  | `health` | Health checks |
  | `ai` | AI service interfaces (`@RegisterAiService`) and their guardrails; structured output records are DTOs in `dto` |

  Packages are created when the first class needs them; no empty placeholders. Further
  kinds of classes (AI services, guardrails, MCP clients) get their package in the ADR that
  introduces them, following the same layering.
- Allowed dependencies (enforced with ArchUnit `layeredArchitecture()`):
  - `resource`, `messaging`, `health` → `service`, `dto`, `config`, `exception`.
  - `ai` → `dto`, `config`; used only by `service`, so a step's model call, its recording
    and its failure handling stay in one service method.
  - `service` → `repository`, `mapper`, `model`, `dto`, `config`, `exception`.
  - `repository` → `model`, `exception`.
  - `mapper` → `model`, `dto`.
  - `dto` → enums in `model` only.
  - `exception` → enums in `model` only.
  - `model` → nothing in the application.
  - Public service methods take and return no entities (P-0014).
  - Nothing outside `repository` and `model` uses `jakarta.persistence` or Hibernate ORM.
- Entry points (`resource`, `messaging`) contain no business logic; they translate between
  the transport and service DTOs.

## Alternatives considered
- Package by feature: keeps a feature's classes together, but layer boundaries are only a
  convention and cannot be checked as simply.
- Feature first, then layer (`<feature>.service`, `<feature>.repository`, …): keeps
  features separable for a later split into modules, but doubles the package count for a
  small domain and makes layer rules harder to express. Reconsider when a feature is
  extracted into its own module or service.
- Separate domain model plus entities (hexagonal / ports and adapters): logic lives in
  framework-free domain objects and entities are a persistence detail. Clean, but adds a
  second model and a second mapping for every type; not justified at this size.

## Consequences
- Classes of one feature are spread over several packages; consistent name prefixes keep
  them findable.
- The ArchUnit rules are part of the build; a violation fails `./mvnw verify`.
