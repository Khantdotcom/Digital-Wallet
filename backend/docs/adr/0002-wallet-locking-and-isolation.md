# ADR 0002 — Wallet locking & isolation (Lab 1 / Phase 02)

**Status:** Accepted  
**Date:** 2026-10-06  
**Product:** Vinter Ledger  
**Deciders:** Lab 1 concurrency evidence

---

## Context

The wallet transfer/withdraw path is already hit concurrently by HTTP (multi-device, double-tap, overlapping requests). Phase 01 built a correct ledger (integer minor units, double-entry). Lab 1 must prove that concurrent mutations cannot:

- drive `balance < 0`
- double-spend the same funds
- unbalance the ledger
- hang forever on A↔B / B↔A deadlocks

## Decision

**Production money path:** PostgreSQL **pessimistic row locks** (`SELECT … FOR UPDATE` via JPA `LockModeType.PESSIMISTIC_WRITE`) plus **deterministic lock order** (ascending wallet id) for two-wallet transfers, with **bounded deadlock retry** (max 5 attempts, linear backoff) outside the transactional boundary (`WalletMoneyCommands`).

**Optimistic `version` column:** present and incremented on every balance mutation so Lab 1 can compare strategies; **not** the primary production control plane.

## Alternatives considered

| Alternative | Hypothesis | Lab 1 measurement (100 workers, withdraw 100¢ from 5000¢) | Verdict |
|-------------|------------|-----------------------------------------------------------|---------|
| Pessimistic `FOR UPDATE` + id-ordered locks | Lowest violation risk; lock waits instead of abort storms | **0** violations; successes = 50; final balance = 0 | **Chosen** for hot wallet spend path |
| Optimistic `version` + retry | Better read concurrency; abort/retry on hot keys | **0** violations; non-zero retries on hot wallet | Valid secondary; higher retry noise on hot keys |
| Naive read-modify-write (no lock) | “Looks fine” under sequential tests | **>0** violations; final balance **negative** (lost updates) | Rejected — proves the race |
| SERIALIZABLE isolation alone | Strongest isolation without app lock order | Not selected as sole strategy; abort rate / complexity not needed when ordered `FOR UPDATE` already yields 0 violations | Deferred |
| App mutex / single-thread queue per wallet | Simple on one JVM | Not measured as primary — fails multi-instance; compared conceptually only | Rejected as sole strategy |

Exact numbers from the latest green run live in the project store: `docs/labs/lab-01-results.md`. Tests: `ConcurrentWalletStressTest`, `LockingStrategyComparisonTest`.

## Consequences

- Transfer always locks `min(id)` then `max(id)` — A→B and B→A cannot deadlock by lock order.
- Deadlock victims (or rare lock acquisition failures) retry ≤5 times via `DeadlockRetryExecutor`; they do not hang forever.
- `CHECK (balance >= 0)` remains a DB safety net, not the primary concurrency control.
- Merchant charge API concurrency is out of scope until Phase 07; Gatling HTTP stress is Lab 4.

## Related

- Lab plan: project store `docs/plans/lab-01-concurrency.md`
- Phase plan: `docs/plans/phase-02-concurrency.md`
- Blog: `docs/blog/concurrency-why-wallet-api-first.md`
