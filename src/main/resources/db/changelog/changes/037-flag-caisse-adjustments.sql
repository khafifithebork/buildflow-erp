--liquibase formatted sql

-- ============================================================================
-- Marks a caisse movement as a correction of an earlier one.
--
-- When a settled order is re-priced the difference is posted back to the
-- caisse. The balance was already right, but the décaissements KPI sums DEBIT
-- rows only, so a correcting CREDIT was never netted out: an order paid 1200
-- and refunded 600 still reported 1200 of outflow.
--
-- Netting every CREDIT would be wrong — funding the caisse (an
-- "Approvisionnement") is money coming in, not negative spend. Only movements
-- flagged here are corrections, so only they are netted.
--
-- Existing rows are all genuine movements, never corrections, so FALSE is the
-- correct backfill.
-- ============================================================================

--changeset khafifi:037-add-ajustement-to-caisse-transactions
ALTER TABLE caisse_transactions
    ADD COLUMN ajustement BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_caisse_txn_ajustement ON caisse_transactions(ajustement);

--rollback DROP INDEX idx_caisse_txn_ajustement;
--rollback ALTER TABLE caisse_transactions DROP COLUMN ajustement;
