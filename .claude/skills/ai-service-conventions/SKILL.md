---
name: ai-service-conventions
description: Use when creating or changing a quarkus-langchain4j AI service, prompt, structured output type, guardrail, or tool exposed to an LLM.
---

# AI service conventions (ADR-0007, ADR-0008)

## Structure per AI service
```
src/main/java/dev/mfa/incident/<feature>/
  <Name>Assistant.java          // @RegisterAiService interface
  <Name>Result.java             // record used as structured output
  <Name>OutputGuardrail.java    // validates the record
src/main/resources/prompts/<name>/
  prompt.properties             // id=<name>, version=<n>
  system.txt                    // stable instructions (Qute)
  user.txt                      // per-call data in delimited untrusted sections
src/main/resources/prompts/_shared/
  safety-rules.txt, output-rules.txt
```

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
  and allowing one reprompt.
- Model selection via named model config (`modelName`), never hard-coded model ids.
- No memory between incidents: AI services are request-scoped/stateless; any chat memory
  is keyed by incident id and discarded after the step.
- Tools for the LLM: only read-only, explicitly allowlisted per AI service. Never expose
  an action tool to an AI service.
- Record per call: model id, prompt id + version, token usage, duration → step row and
  span attributes.
- Tests: mock the AI service interface in `@QuarkusTest`; unit-test guardrails directly.
