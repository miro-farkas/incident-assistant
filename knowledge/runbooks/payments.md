---
service: payments
type: runbook
title: Payments service runbook
updated: 2026-10-20
---

# Payments service

Payments authorises and captures card payments. Each request calls `fraud-check` first
and then the external `bank-gateway`. The client timeout for both is 3 seconds; one retry
is made for bank timeouts since release 5.3.1.

## Symptoms

- `HighErrorRate` with errors that are mostly timeouts, not HTTP 500 from our code.
- Latency of failing requests sits exactly at the client timeout (about 3000 ms).
- CPU usage drops while errors rise: the service is waiting, not working.

## Diagnosis

1. Check the health of the dependencies (`get_service_health`). A dependency reported
   `DOWN` with a response time at the timeout is the cause.
2. Search the logs for `TimeoutException` or `connect timed out` and note which
   dependency they name (`search_logs`).
3. Check recent deployments (`get_recent_deployments`). A payments release is only the
   cause when the errors started with it and dependencies are healthy.

## Remediation

- A downstream timeout cannot be fixed in payments. Do not roll back or restart
  payments for it: a restart drops in-flight authorisations, and a rollback removes the
  bank retry.
- Do not scale out: more replicas send more requests to a dependency that is already
  failing.
- If `fraud-check` is down, its owners can switch it to fail-open mode; this is their
  decision, not an automated action.

## Escalation

`bank-gateway` is operated by the acquiring bank: open a priority ticket with the bank
and inform customer support. `fraud-check` is owned by team "Risk". Payments owners:
team "Money".
