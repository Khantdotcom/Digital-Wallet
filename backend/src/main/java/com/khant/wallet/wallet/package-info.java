/**
 * Wallet module boundary (modular monolith).
 *
 * <p>Owns wallet balances, user-facing money movements ({@code transactions}),
 * and the append-only double-entry {@code ledger_entries} used to prove money
 * conservation. Later phases (concurrency, idempotency, outbox) build on this
 * package without extracting a separate deployable yet.
 */
package com.khant.wallet.wallet;
