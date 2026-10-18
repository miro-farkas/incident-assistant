# ADR-0002: Imperative code on virtual threads

- Status: Accepted
- Date: 2026-10-18

## Context
Most work in this service is I/O-bound and slow (LLM calls take seconds). LangChain4j
tool calling and Hibernate ORM are blocking APIs. Reactive code (Mutiny) would add
complexity without a throughput benefit at the expected volume.

## Decision
- Business logic is imperative and blocking.
- Kafka consumers, REST endpoints and step executors run on virtual threads
  (`@RunOnVirtualThread`).
- Mutiny is not used in application code. Streaming responses are out of scope.

## Alternatives considered
- Reactive (Mutiny + Hibernate Reactive): rejected — no benefit for this workload and
  poor fit with blocking LLM tool calling.
- Platform worker threads: rejected — virtual threads give simpler scaling of
  long-waiting calls.

## Consequences
- Avoid pinning: no `synchronized` around blocking I/O; use `ReentrantLock` if locking
  is needed.
- Concurrency limits to the LLM provider are enforced explicitly (Bulkhead, ADR-0008),
  not by thread pool size.
