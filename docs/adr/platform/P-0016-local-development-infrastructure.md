# P-0016: Local development infrastructure

- Status: Proposed
- Date: 2026-10-19

## Context
Dev mode starts its backing services (database, broker, later identity provider and local
model server) as per-session Dev Services containers. They get random host ports and are
removed with their anonymous volumes when dev mode stops, so local data, topic contents and
downloaded models are lost on every restart, and external tools (database client, Kafka
CLI) need a new address each time. Running the packaged application locally needs the same
services without Quarkus starting them. Tests, in contrast, need empty, isolated services on
every run so that results do not depend on earlier runs or on local data (P-0010).

## Decision
- Dev mode provisions backing services with Quarkus Compose Dev Services from one
  `compose-devservices.yml` in the repository root. It is the single place where local
  infrastructure is defined; new backing services are added there.
- Every stateful service stores its data in a named volume. Services keep running when dev
  mode stops (`quarkus.compose.devservices.stop-services=false`), and the next dev mode
  session finds them through a fixed compose project name
  (`quarkus.compose.devservices.project-name`) instead of starting new ones. Volumes are
  never removed by Quarkus (`quarkus.compose.devservices.remove-volumes=false`).
- Services publish fixed host ports, so the same addresses work for external tools and for
  running the packaged application with `docker compose -f compose-devservices.yml up`.
- Credentials in the compose file are fixed, local-only values. Production configuration
  never reads them; it comes from environment variables as before.
- Tests do not start, discover or reuse compose services. `@QuarkusTest` keeps using
  per-run Dev Services containers (Testcontainers), empty on every run (P-0010).
- Images in the compose file and in the test Dev Services configuration use the same
  versions, so dev and test run against the same server versions.

## Alternatives considered
- Keep per-session Dev Services and enable Testcontainers container reuse
  (`testcontainers.reuse.enable=true`): keeps data, but it is a per-developer setting outside
  the repository, ports stay random, and the packaged application still has no services.
- A plain `docker-compose.yml` started by hand, with Dev Services disabled: the same
  persistence, but every developer has to start it before dev mode and keep connection
  configuration in sync manually; Compose Dev Services starts it and wires the
  configuration automatically.
- Compose services for tests too: shares state between dev and test runs or needs extra
  project isolation, and tests would depend on the compose file instead of the declared
  Dev Services configuration.

## Consequences
- Local data survives restarts, and dev mode starts faster because the services are already
  up. The services keep using memory after dev mode stops until the developer runs
  `docker compose -f compose-devservices.yml down` (data kept) or `down -v` (clean start).
  Changing an applied database migration locally requires the clean start.
- Fixed ports can collide with services already running on the developer's machine; the
  host side of a mapping can be changed locally without affecting tests.
- The compose file must be kept in step with the configuration the application expects
  (topic names, partition counts, image versions); tests do not catch drift because they do
  not use it.
- Not in scope: production or shared-environment deployment manifests.
