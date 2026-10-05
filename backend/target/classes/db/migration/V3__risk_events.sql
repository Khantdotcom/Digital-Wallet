CREATE TABLE risk_events (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  wallet_id BIGINT REFERENCES wallets(id) ON DELETE SET NULL,
  operation VARCHAR(30) NOT NULL,
  risk_score INTEGER NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  reasons TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_events_user_created_at ON risk_events (user_id, created_at DESC);
