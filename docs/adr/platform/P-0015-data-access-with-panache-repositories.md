# P-0015: Data access with Panache repositories

- Status: Accepted
- Date: 2026-10-18

## Context
Repositories (P-0013) were written against `EntityManager` directly. Upcoming data access
needs lookups by business keys, filtered and paged lists, and lock-based claiming of work
(P-0003). Hibernate ORM with Panache is the Quarkus convention for this and removes the
boilerplate of typed queries and paging.

## Decision
- Every repository is an application-scoped CDI bean implementing
  `PanacheRepositoryBase<Entity, UUID>` (extension `quarkus-hibernate-orm-panache`, status
  *stable*). Entities keep extending `BaseEntity` (P-0014); the active-record style
  (`PanacheEntity`, static finders on entities) is not used.
- Repositories add named methods for every query the services need (`findOpenByKey`,
  `listByState(state, pageIndex, pageSize)`), so services read as intent and do not build
  query strings.
- Repository signatures use only entities, `Optional`, `List`, counts and plain values —
  never Panache types (`PanacheQuery`, `Page`, `Sort`), which would carry persistence
  concerns into the service layer.
- Native SQL or features Panache does not express (for example `FOR UPDATE SKIP LOCKED`)
  go through `getEntityManager()` inside the repository.
- Translating persistence failures into domain exceptions (an optimistic-lock failure on
  flush → a conflict exception) stays a hand-written repository method.
- ArchUnit confines Panache, like JPA and Hibernate ORM, to `repository` and `model`.

## Alternatives considered
- Plain `EntityManager` repositories: no extra extension and fully explicit, but typed
  queries, paging and counting are written by hand for every repository.
- Panache active record (`PanacheEntity`): least code, but puts static data-access methods
  on entities, which P-0014 keeps logic-free, and spreads persistence calls across layers.
- `quarkus-hibernate-panache-next`: present in the platform BOM but ships no
  implementation in the evaluated release; reconsider when it is stable.
- Jakarta Data / Hibernate Data repositories: type-safe, generated at build time, but a
  second programming model next to Panache; reconsider if Quarkus makes it the default.

## Consequences
- Repositories inherit Panache's full public API (`deleteAll`, `update`, …). Services use
  only the named repository methods and the basic `persist`/`flush`/`findByIdOptional`;
  code review watches for ad-hoc query strings in services.
- Panache enhances repository bytecode at build time; tests run against the enhanced
  classes as in production.
