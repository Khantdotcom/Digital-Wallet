-- Phase 01: money correctness — constraints, transaction states, double-entry ledger.

-- Cached wallet balance must never go negative (safety net; app also checks).
ALTER TABLE wallets
  ADD CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0);

-- Harden existing transaction amounts (user-facing money movement records).
ALTER TABLE transactions
  ADD CONSTRAINT transactions_amount_positive CHECK (amount > 0);

ALTER TABLE transactions
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  ADD COLUMN movement_group_id UUID NOT NULL DEFAULT gen_random_uuid(),
  ADD COLUMN created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN failure_reason VARCHAR(255),
  ADD COLUMN completed_at TIMESTAMPTZ,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Existing rows were written as successful movements before status existed.
UPDATE transactions
SET status = 'COMPLETED',
    completed_at = created_at
WHERE status = 'COMPLETED' AND completed_at IS NULL;

ALTER TABLE transactions
  ADD CONSTRAINT transactions_status_allowed
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_transactions_movement_group_id ON transactions (movement_group_id);
CREATE INDEX idx_transactions_wallet_status ON transactions (wallet_id, status);

-- Append-only double-entry legs. Every COMPLETED movement has balanced debit/credit legs.
CREATE TABLE ledger_entries (
  id BIGSERIAL PRIMARY KEY,
  movement_group_id UUID NOT NULL,
  transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
  wallet_id BIGINT REFERENCES wallets(id) ON DELETE RESTRICT,
  account_kind VARCHAR(20) NOT NULL,
  direction VARCHAR(10) NOT NULL,
  amount NUMERIC(19, 2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ledger_entries_amount_positive CHECK (amount > 0),
  CONSTRAINT ledger_entries_direction_allowed CHECK (direction IN ('DEBIT', 'CREDIT')),
  CONSTRAINT ledger_entries_account_kind_allowed CHECK (account_kind IN ('WALLET', 'EXTERNAL')),
  CONSTRAINT ledger_entries_wallet_account_coherence CHECK (
    (account_kind = 'WALLET' AND wallet_id IS NOT NULL)
    OR (account_kind = 'EXTERNAL' AND wallet_id IS NULL)
  )
);

CREATE INDEX idx_ledger_entries_movement_group_id ON ledger_entries (movement_group_id);
CREATE INDEX idx_ledger_entries_wallet_id ON ledger_entries (wallet_id);
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);

-- Immutability: ledger rows are never rewritten or deleted.
CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entries are immutable (append-only)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_no_update
  BEFORE UPDATE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_no_delete
  BEFORE DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

-- Completed / failed money movements must not rewrite financial fields.
CREATE OR REPLACE FUNCTION prevent_terminal_transaction_rewrite()
RETURNS trigger AS $$
BEGIN
  IF OLD.status IN ('COMPLETED', 'FAILED') THEN
    IF NEW.amount IS DISTINCT FROM OLD.amount
       OR NEW.type IS DISTINCT FROM OLD.type
       OR NEW.wallet_id IS DISTINCT FROM OLD.wallet_id
       OR NEW.related_wallet_id IS DISTINCT FROM OLD.related_wallet_id
       OR NEW.movement_group_id IS DISTINCT FROM OLD.movement_group_id
       OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
       OR (OLD.status = 'COMPLETED' AND NEW.status IS DISTINCT FROM OLD.status)
       OR (OLD.status = 'FAILED' AND NEW.status IS DISTINCT FROM OLD.status) THEN
      RAISE EXCEPTION 'terminal transactions are immutable (id=%)', OLD.id;
    END IF;
  END IF;

  -- PENDING may only move to COMPLETED or FAILED.
  IF OLD.status = 'PENDING' AND NEW.status NOT IN ('PENDING', 'COMPLETED', 'FAILED') THEN
    RAISE EXCEPTION 'invalid transaction status transition % -> %', OLD.status, NEW.status;
  END IF;

  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_terminal_immutable
  BEFORE UPDATE ON transactions
  FOR EACH ROW EXECUTE FUNCTION prevent_terminal_transaction_rewrite();
