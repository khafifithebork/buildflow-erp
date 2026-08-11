--liquibase formatted sql

-- ============================================================================
-- Stock gains a second quantity: what has been posé (incorporated into the
-- works) as opposed to what is still available.
--
-- Migration 035 gave stock a LOCATION — dépôt central or chantier. That answers
-- "where is it". It does not answer "is it still available or already
-- consumed", which is what the Dépôts / En Travaux split on the dashboard is
-- actually asking:
--
--   quantite_theorique : still available at that location  ("Stock Dispo")
--   quantite_travaux   : incorporated into the works        ("Stock Travaux / Posé")
--
-- The two axes are independent. A chantier holds both: material delivered and
-- waiting, and material already laid. Affecting to the works moves quantity
-- from the first to the second WITHOUT moving location, so the total value of
-- stock is unchanged — only its split.
--
-- Existing rows are all un-posé material, so 0 is the correct backfill.
-- ============================================================================

--changeset khafifi:038-add-quantite-travaux-to-stock-articles
ALTER TABLE stock_articles
    ADD COLUMN quantite_travaux DECIMAL(15,3) NOT NULL DEFAULT 0;

--rollback ALTER TABLE stock_articles DROP COLUMN quantite_travaux;

--changeset khafifi:038-add-annule-to-caisse-transactions
-- A cash movement can be cancelled when it should not have happened — a
-- mistyped amount, a duplicate, a payment that never cleared. The row is kept
-- and flagged rather than deleted: a reversing entry puts the balance back, so
-- the ledger shows both what was recorded and what corrected it.
ALTER TABLE caisse_transactions
    ADD COLUMN annule BOOLEAN NOT NULL DEFAULT FALSE;

--rollback ALTER TABLE caisse_transactions DROP COLUMN annule;
