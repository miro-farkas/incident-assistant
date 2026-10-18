# P-0002: Imperative code on virtual threads

- Status: Accepted
- Date: 2026-10-18

## Context
Most work in an LLM-backed service is I/O-bound and slow (model calls take seconds).
LangChain4j tool calling and Hibernate ORM are blocking APIs. Reactive code (Mutiny) would
add complexity without a throughput benefit at the expected volume.

## Decision
- Business logic is imperative and blocking.
- Kafka consumers, REST endpoints and background workers run on virtual threads
  (`@RunOnVirtualThread`).
- Mutiny is not used in application code. Streaming responses are out of scope.

## Alternatives considered
- Reactive (Mutiny + Hibernate Reactive): no benefit for this workload and a poor fit with
  blocking LLM tool calling.
- Platform worker threads: virtual threads scale long-waiting calls more simply.

## Consequences
- Avoid pinning: no `synchronized` around blocking I/O; use `ReentrantLock` if locking is
  needed.
- Concurrency limits towards the LLM provider are enforced explicitly (bulkhead, P-0008),
  not by thread pool size.
