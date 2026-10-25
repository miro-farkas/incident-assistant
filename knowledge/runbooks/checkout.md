---
service: checkout
type: runbook
title: Checkout service runbook
updated: 2026-10-20
---

# Checkout service

Checkout turns a cart into an order. It calls `payments` to charge the customer and
`inventory` to reserve stock. It runs with three replicas; normal error rate is below 1 %
and p95 latency around 200 ms.

## Symptoms

- `HighErrorRate`: more than 5 % of checkout requests fail with HTTP 500.
- `HighLatency`: p95 latency above 800 ms.
- Customers report that the order button spins and then shows a generic error.

## Diagnosis

1. Check recent deployments (`get_recent_deployments`). A release in the last hour that
   lines up with the start of the errors is the most likely cause; checkout releases
   change discount, tax and price logic often.
2. Compare error rate and latency before and after the release
   (`get_service_metrics`). A step change at the rollout time points to the release; a
   gradual rise points to a dependency or to load.
3. Search the logs for exceptions (`search_logs`). `NullPointerException` or
   `IllegalStateException` in discount or price calculation after a release confirms a
   code defect.
4. Check the health of `payments` and `inventory` (`get_service_health`). If a dependency
   is down or slow, follow that service's runbook instead; rolling back checkout will not
   help.

## Remediation

- Defect in the latest release: roll back to the previous version
  (`rollback_deployment`). This is safe for checkout: it has no database migrations of
  its own, and orders already placed are not affected.
- Saturation without a release (CPU above 80 % on all replicas): scale out by two
  replicas (`scale_service`).
- A restart only helps for stuck connections after a network incident; it does not fix
  a bad release.

## Escalation

If errors continue after a rollback, or a dependency is the cause, page the team that
owns the dependency. Checkout owners: team "Storefront".
