-- Phase 02 / Lab 1: optimistic version column for wallet balance races.
-- Production money path still uses SELECT … FOR UPDATE; version enables
-- optimistic comparison experiments and a second integrity check on UPDATE.

ALTER TABLE wallets
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN wallets.version IS
  'Optimistic concurrency token; incremented on every balance mutation.';
