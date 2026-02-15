CREATE TABLE wallets (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT wallets_user_name_unique UNIQUE (user_id, name)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

CREATE TABLE transactions (
  id BIGSERIAL PRIMARY KEY,
  wallet_id BIGINT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
  related_wallet_id BIGINT REFERENCES wallets(id) ON DELETE SET NULL,
  type VARCHAR(20) NOT NULL,
  amount NUMERIC(19, 2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  note VARCHAR(255)
);

CREATE INDEX idx_transactions_wallet_id_created_at ON transactions (wallet_id, created_at DESC);
