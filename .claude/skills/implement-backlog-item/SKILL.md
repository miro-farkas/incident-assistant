---
name: implement-backlog-item
description: Use when asked to implement, continue or finish a backlog item (INC-xxx) from docs/backlog.md. Defines the workflow from reading context to the final summary.
---

# Implement a backlog item

1. **Load context.** Read `CLAUDE.md`, `docs/architecture.md`, the item in
   `docs/backlog.md`, and every ADR the item references. Inspect existing code in the
   affected features before writing new code.
2. **Check preconditions.** All previous items must be `DONE`. If not, stop and say so.
3. **Plan.** Write a short plan: files to add/change, tests to add, open questions.
   If something needs a decision not covered by an ADR, stop and propose an ADR
   (skill `write-adr`). If the plan is non-trivial, show it and wait for confirmation.
4. **Mark** the item `IN PROGRESS` in `docs/backlog.md`.
5. **Implement in small steps**, tests alongside code. Follow conventions in `CLAUDE.md`
   and the relevant skills (`ai-service-conventions`, `mcp-tool-conventions`).
   Verify library APIs against current documentation instead of guessing — especially
   quarkus-langchain4j and quarkus-mcp-server, which change fast.
6. **Verify.** Run `./mvnw spotless:apply` and `./mvnw verify`. Fix failures; never
   disable or skip tests to make the build pass.
7. **Self-review** the diff against the acceptance criteria and this checklist:
   - No secrets, no PII in logs, timeouts on every external call.
   - No TODOs, no dead code, no unused dependencies.
   - Config documented with defaults; README updated if run instructions changed.
8. **Mark** the item `DONE` and update ADR text if the implementation refined it.
9. **Report** to the maintainer: summary of changes, how to try it locally, suggested
   branch name (`<type>/inc-xxx-<short-slug>`, never `master`), suggested Conventional
   Commit message(s), open questions. Do not create the branch, commit or push.
