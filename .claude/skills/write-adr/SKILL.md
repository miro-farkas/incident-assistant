---
name: write-adr
description: Use when a decision affects architecture, dependencies, security boundaries or data formats and is not covered by an existing ADR in docs/adr/, or when asked to write or update an ADR.
---

# Write an ADR

1. Read existing ADRs to avoid overlap; if one covers the topic, propose an amendment or
   a superseding ADR instead of a new one.
2. Copy `docs/adr/0000-template.md` to `docs/adr/NNNN-kebab-case-title.md` with the next
   free number.
3. Status `Proposed`, today's date. Keep it short: context (forces), decision (active
   voice, concrete), at least two alternatives with the reason for rejection,
   consequences including what is explicitly out of scope.
4. If it supersedes an ADR, set the old one to `Superseded by ADR-NNNN`.
5. Stop and ask the maintainer to accept it. Only `Accepted` ADRs may be implemented.
