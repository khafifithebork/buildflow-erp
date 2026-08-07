--liquibase formatted sql

-- ============================================================================
-- Unit prices move from DECIMAL(15,2) to DOUBLE PRECISION, so a rate can carry
-- more than two decimals (per kg, per m³, per ml …).
--
-- Scope is deliberately the three UNIT PRICE columns only. Line totals, HT/TVA/
-- TTC, caisse balances, salaries and contract amounts stay DECIMAL: those are
-- the figures that get invoiced, paid and reconciled, and they must stay exact.
-- A unit price feeds a total that is still rounded HALF_UP to two decimals, so
-- the extra precision is used in the calculation without leaking sub-centime
-- values into the ledger.
--
-- Trade-off accepted with this change: DOUBLE is binary floating point, so a
-- value such as 0.1 is stored approximately and prices should not be compared
-- for exact equality. Widening the DECIMAL scale instead would have avoided
-- that; DOUBLE was chosen deliberately.
-- ============================================================================

--changeset khafifi:033-articles-prix-achat-ref-to-double
ALTER TABLE articles
    ALTER COLUMN prix_achat_ref TYPE DOUBLE PRECISION;

--rollback ALTER TABLE articles ALTER COLUMN prix_achat_ref TYPE DECIMAL(15,2);

--changeset khafifi:033-lignes-achat-prix-unitaire-to-double
ALTER TABLE lignes_achat
    ALTER COLUMN prix_unitaire TYPE DOUBLE PRECISION;

--rollback ALTER TABLE lignes_achat ALTER COLUMN prix_unitaire TYPE DECIMAL(15,2);

--changeset khafifi:033-bpu-lignes-pu-ht-to-double
ALTER TABLE bpu_lignes
    ALTER COLUMN pu_ht TYPE DOUBLE PRECISION;

--rollback ALTER TABLE bpu_lignes ALTER COLUMN pu_ht TYPE DECIMAL(15,2);
