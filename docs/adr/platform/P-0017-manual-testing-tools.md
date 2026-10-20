# P-0017: Manual testing tools

- Status: Accepted
- Date: 2026-10-20

## Context
Manual testing needs small helper programs, for example one that produces messages to an
input topic at a fixed rate. Kept outside the build they break silently when formats or
tooling change and cannot be shared; kept inside a service module they would ship with the
service and fall under its layering and configuration rules, which do not fit a throwaway
command-line program.

## Decision
- Each tool is its own module in the Maven reactor, named after the tool, so that
  `./mvnw verify` compiles and format-checks it.
- A tool is plain Java (a `main` class), with no framework. Its dependencies are limited to
  the client library it needs, with the version managed by the platform BOM.
- Tools are never a dependency of a service module and are not deployed. They are run by
  the developer with `./mvnw -q -pl <tool> compile exec:java`; the `exec-maven-plugin`
  version is pinned in the tool's POM.
- Configuration is constants at the top of the main class, edited before a run. The service
  conventions for `@ConfigMapping`, layering and packages do not apply to tools; the
  package root, formatting and the Java version do.
- Tools target local development services only and contain no credentials beyond the
  local-only ones of the compose file (P-0016).

## Alternatives considered
- Scripts or single-file programs outside the build: no compile check, so they break
  unnoticed when message formats or client libraries change.
- Main classes in a service module's test sources: no build-tool way to run them, and they
  mix with tests that the test rules govern.
- Framework-based command-line applications: start-up and configuration machinery out of
  proportion to a program of one class.

## Consequences
- `./mvnw verify` builds tool modules too; they have no tests unless their logic warrants
  them.
- `exec-maven-plugin` is a build plugin from outside the platform BOM, used only by tools.
- Not in scope: load testing tools with reporting; those need their own decision.
