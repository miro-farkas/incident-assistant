---
service: inventory
type: runbook
title: Inventory service runbook
updated: 2026-10-20
---

# Inventory service

Inventory keeps stock levels and reserves stock for orders. It reads from `inventory-db`
and, since release 1.8.0, keeps an in-memory cache of stock levels per product. Normal
heap usage stays between 40 % and 55 %.

## Symptoms

- Memory usage that rises steadily over hours and does not fall after garbage
  collection.
- Pods restarted with reason `OOMKilled`; fewer ready replicas than configured.
- Latency and CPU rise before a pod is killed, because the garbage collector runs
  almost all the time.

## Diagnosis

1. Look at memory over the last hours (`get_service_metrics`). A straight upward line
   is a leak; a saw-tooth pattern is normal.
2. Check pod restarts and ready replicas (`get_service_health`).
3. Search the logs for `OutOfMemoryError` and for `GC overhead` warnings
   (`search_logs`).
4. Check recent deployments (`get_recent_deployments`). Cache changes are the usual
   source of leaks in inventory.

## Remediation

- To recover quickly, restart the service (`restart_service`). Memory starts from the
  normal level again, so this buys hours, not a fix.
- If the leak started with a release, roll back to the previous version
  (`rollback_deployment`) after the restart.
- Do not scale out: every new replica leaks the same way.

## Escalation

If memory grows again after a rollback, page the inventory owners, team "Fulfilment",
with the heap graph of the last 12 hours.
