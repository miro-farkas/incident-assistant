---
name: write-adr
description: Use when a decision affects architecture, dependencies, security boundaries or data formats and is not covered by an existing ADR in docs/adr/, or when asked to write or update an ADR.
---

# Write an ADR

ADRs come in two sets, each numbered on its own (see `docs/adr/README.md`):

- `docs/adr/platform/P-NNNN-*.md` — generic, reusable in any similar project. No domain
  terms (incident, alert, …), no backlog items, no application or module names.
- `docs/adr/app/A-NNNN-*.md` — specific to this application. Lists the platform ADRs it
  applies in an `Applies:` line.

1. Read existing ADRs in both sets to avoid overlap; if one covers the topic, propose an
   amendment or a superseding ADR instead of a new one.
2. Decide the set. If the decision has a generic rule and an app-specific application,
   write both: the rule as a `P-` ADR, the application as an `A-` ADR that applies it.
3. Copy `docs/adr/platform/template.md` to the set's folder with the next free number of
   that set and a kebab-case title.
4. Status `Proposed`, today's date. Keep it short: context (forces), decision (active
   voice, concrete), at least two alternatives with the reason for rejection,
   consequences including what is explicitly out of scope.
5. Add the ADR to the index in `docs/adr/README.md`.
6. If it supersedes an ADR, set the old one to `Superseded by P-NNNN` / `A-NNNN`.
7. Stop and ask the maintainer to accept it. Only `Accepted` ADRs may be implemented.
