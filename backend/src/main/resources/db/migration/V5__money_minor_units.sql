-- Phase 01 follow-up: money as integer minor units (cents), not DECIMAL.

-- Drop money CHECKs before type change; recreate afterward.
ALTER TABLE wallets DROP CONSTRAINT wallets_balance_non_negative;
ALTER TABLE transactions DROP CONSTRAINT transactions_amount_positive;
ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_amount_positive;

-- Convert major-unit NUMERIC(19,2) → BIGINT minor units (×100).
-- Safe for this app: amounts were always scale-2 currency values.
ALTER TABLE wallets
  ALTER COLUMN balance TYPE BIGINT
  USING ROUND(balance * 100)::BIGINT;

ALTER TABLE wallets
  ALTER COLUMN balance SET DEFAULT 0;

ALTER TABLE transactions
  ALTER COLUMN amount TYPE BIGINT
  USING ROUND(amount * 100)::BIGINT;

ALTER TABLE ledger_entries
  ALTER COLUMN amount TYPE BIGINT
  USING ROUND(amount * 100)::BIGINT;

ALTER TABLE wallets
  ADD CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0);

ALTER TABLE transactions
  ADD CONSTRAINT transactions_amount_positive CHECK (amount > 0);

ALTER TABLE ledger_entries
  ADD CONSTRAINT ledger_entries_amount_positive CHECK (amount > 0);

COMMENT ON COLUMN wallets.balance IS 'Cached wallet balance in integer minor units (cents).';
COMMENT ON COLUMN transactions.amount IS 'Movement amount in integer minor units (cents).';
COMMENT ON COLUMN ledger_entries.amount IS 'Ledger leg amount in integer minor units (cents); always positive.';
