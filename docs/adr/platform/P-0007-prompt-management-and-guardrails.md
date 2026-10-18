# P-0007: Prompt management and guardrails

- Status: Accepted
- Date: 2026-10-18

## Context
Prompts define behaviour as much as code. Hard-coded prompt strings in annotations are hard
to review, reuse and trace. Inbound payloads and retrieved documents may contain
prompt-injection attempts.

## Decision
- Prompts live in `src/main/resources/prompts/<ai-service>/` as Qute templates and are
  referenced with `@SystemMessage(fromResource = ...)` / `@UserMessage(fromResource = ...)`.
- Each prompt directory has a `prompt.properties` with `id` and `version`. The version is
  bumped on every behavioural change and recorded on every workflow step row and as an
  OpenTelemetry span attribute.
- Shared fragments (safety rules, output format rules) are included, not copied.
- Stable instructions go into the system message; case-specific data goes into the user
  message, wrapped in clearly delimited sections marked as untrusted data.
- All step outputs use structured output (Java records). Output guardrails validate them
  (enum values, allowlists, required evidence, length limits) and reprompt once before
  failing the step.
- An input guardrail rejects or truncates oversized inputs.
- No secrets or internal credentials in prompts.

## Alternatives considered
- Runtime prompt registry (e.g. Langfuse): decouples prompt and code versions and needs its
  own release process. Revisit if non-developers edit prompts.

## Consequences
- Prompt changes are reviewed in PRs and must pass the evaluation suite (P-0010).
