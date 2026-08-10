--liquibase formatted sql

-- ============================================================================
-- Order line quantities move to DOUBLE PRECISION, matching what migration 033
-- did for unit prices.
--
-- DECIMAL(15,3) capped a quantity at three decimals, which is short for the
-- units this actually gets used with — tonnage, m³, ml, coefficients — and
-- meant a line could carry a price finer than the quantity it multiplied.
-- Both sides of the line total now hold the same kind of value.
--
-- The line total itself stays DECIMAL(15,2): it is the invoiced figure and
-- must stay exact, exactly as in 033.
--
-- Scope is lignes_achat only. Stock quantities (stock_articles,
-- mouvements_stock) and BPU quantities stay DECIMAL(15,3) — they are counted
-- and reconciled rather than multiplied out, and widening them would change
-- how stock movements reconcile.
-- ============================================================================

--changeset khafifi:036-lignes-achat-quantite-to-double
ALTER TABLE lignes_achat
    ALTER COLUMN quantite TYPE DOUBLE PRECISION;

--rollback ALTER TABLE lignes_achat ALTER COLUMN quantite TYPE DECIMAL(15,3);
