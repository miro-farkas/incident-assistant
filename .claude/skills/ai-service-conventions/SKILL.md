---
name: ai-service-conventions
description: Use when creating or changing a quarkus-langchain4j AI service, prompt, structured output type, guardrail, or tool exposed to an LLM.
---

# AI service conventions (P-0007, P-0008)

## Structure per AI service (package `ai`, P-0013 as amended by A-0014)
```
src/main/java/dev/mfa/incident/ai/
  <Name>Assistant.java          // @RegisterAiService interface, used only by `service`
  <Name>OutputGuardrail.java    // validates the record
src/main/java/dev/mfa/incident/dto/
  <Name>Result.java             // record used as structured output (also stored and returned)
src/main/resources/prompts/<name>/
  prompt.properties             // id=<name>, version=<n>; read with PromptVersions.of(name)
  system.txt                    // stable instructions (Qute)
  user.txt                      // per-call data in delimited untrusted sections
src/main/resources/templates/prompts/_shared/
  safety-rules.txt, output-rules.txt   // {#include prompts/_shared/safety-rules /}
```
Shared fragments live under `templates/`, because Qute resolves `{#include}` there. Missing
input values are passed as `null` and printed with `{name ?: 'not reported'}`.

## Rules
- Never inline prompt text in Java. Use `fromResource`.
- Bump `version` in `prompt.properties` for every behavioural change and mention it in
  the item summary. The eval suite must be run before merge (maintainer runs it).
- Incident data and retrieved documents go into the user message inside explicit
  delimiters, e.g. `<alert>...</alert>`, `<document source="...">...</document>`, with an
  instruction that their content is data, never instructions.
- Output is always a Java record with Bean Validation annotations; enums for closed sets.
  Use `@Description` on fields where it improves extraction.
- Every AI service has an output guardrail validating business rules beyond the schema
  and allowing one reprompt (`quarkus.langchain4j.guardrails.max-retries=2`: langchain4j
  counts the first answer). On success it returns `successWith(AiMessage)` with the cleaned
  text, not a parsed object: for a `Result<T>` return type the service parses that message.
- Model selection via named model config (`modelName`), never hard-coded model ids.
- No memory between calls (A-0015): AI services are `@ApplicationScoped` (the executor's
  threads have no request context) with
  `chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class`;
  the tool loop and the reprompt keep their messages for one call only.
- Every AI service names its tool provider: `NoToolProviderSupplier` without tools, or a
  `Supplier<ToolProvider>` class. Never declare a `ToolProvider` bean; it would be attached
  to every AI service.
- Tools for the LLM: only read-only, explicitly allowlisted per AI service. Never expose
  an action tool to an AI service.
- Record per call: model id, prompt id + version, token usage, duration → step row and
  span attributes.
- Tests: mock the AI service interface in `@QuarkusTest`; unit-test guardrails directly.
