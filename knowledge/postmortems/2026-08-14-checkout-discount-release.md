---
service: checkout
type: postmortem
title: "Postmortem: checkout errors after the discount release"
updated: 2026-08-20
---

# Checkout errors after the discount release

## Summary

On 2026-08-14 between 10:05 and 10:41 UTC, 11 % of checkout requests failed with HTTP
500. Release 2.11.0, deployed at 10:04, contained a defect in the discount calculation
for carts that combine a voucher with a gift card. A rollback to 2.10.2 ended the errors.

## Timeline

- 10:04 Release 2.11.0 rolled out to all replicas.
- 10:07 Alert `HighErrorRate` fired for checkout.
- 10:15 On-call saw `NullPointerException` in `DiscountCalculator` in the logs.
- 10:38 Rollback to 2.10.2 started; finished at 10:41. Error rate back below 1 %.

## Root cause

The new discount code assumed every cart line has a price list. Lines bought with a gift
card have none, so the calculation failed for those carts.

## What went well

The release time and the start of the errors matched exactly, which pointed to the
release within minutes.

## What went wrong

The rollback was delayed by 20 minutes while the team tried a restart, which cannot fix
a code defect.

## Actions

- Roll back first when errors start with a release; investigate afterwards.
- Add test cases for voucher and gift card combinations.
