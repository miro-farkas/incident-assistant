---
service: inventory
type: postmortem
title: "Postmortem: inventory pods killed out of memory"
updated: 2026-09-05
---

# Inventory pods killed out of memory

## Summary

On 2026-09-02, inventory pods were killed out of memory three times between 06:10 and
07:30 UTC. Stock reservations failed for 3 % of orders during each restart. The stock
cache introduced in release 1.8.0 never evicted entries for discontinued products, so the
heap grew with every catalogue import.

## Timeline

- 02:00 Nightly catalogue import added 40 000 discontinued products.
- 05:30 Heap usage passed 85 % on all replicas and kept rising.
- 06:10 First `OOMKilled` restart; alert `PodCrashLooping` fired.
- 06:40 On-call restarted all replicas, which brought memory back to 45 %.
- 07:30 Memory had risen again; the team rolled back to 1.7.1.

## Root cause

The cache keyed entries by product id without a size limit or expiry. Catalogue imports
only ever add products, so the cache only grew.

## What went well

The restart recovered the service within minutes and gave time to decide on the
rollback.

## What went wrong

Scaling out was tried first and made it worse: new replicas filled their caches at
once and put load on `inventory-db`.

## Actions

- Restart to recover, then roll back the release that introduced the leak.
- Give the cache a size limit and an expiry (done in 1.8.1).
- Alert on heap usage above 80 % for 30 minutes, before pods are killed.
