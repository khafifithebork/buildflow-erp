--liquibase formatted sql

-- ============================================================================
-- Stock gains a location, so "Dépôts" and "En Travaux" can finally mean
-- something.
--
-- Until now every stock_articles row carried a NOT NULL chantier_id: stock only
-- ever existed against a construction site, there was no warehouse, and the
-- dashboard's Dépôts / En Travaux split was hardcoded to 0 / 0 because nothing
-- could compute it.
--
-- The location is now expressed by chantier_id itself:
--
--   chantier_id IS NULL      -> the central dépôt
--   chantier_id IS NOT NULL  -> allocated to that chantier ("en travaux")
--
-- A nullable column rather than a separate `depots` table: the split the
-- dashboard asks for needs one warehouse, and modelling several named dépôts
-- would be speculation. Adding them later means turning this into a FK to a
-- depots table without disturbing the chantier side.
--
-- Existing rows are untouched: they all keep their chantier_id and therefore
-- all count as "en travaux", which is exactly what they were.
-- ============================================================================

--changeset khafifi:035-stock-articles-chantier-nullable
ALTER TABLE stock_articles ALTER COLUMN chantier_id DROP NOT NULL;

--rollback DELETE FROM stock_articles WHERE chantier_id IS NULL;
--rollback ALTER TABLE stock_articles ALTER COLUMN chantier_id SET NOT NULL;

--changeset khafifi:035-stock-articles-unique-per-location
-- UNIQUE (article_id, chantier_id) no longer does the job: PostgreSQL treats
-- NULLs as distinct, so it would happily allow several dépôt rows for the same
-- article. Two partial indexes instead — one per side of the split.
ALTER TABLE stock_articles DROP CONSTRAINT uk_stock_article_chantier;

CREATE UNIQUE INDEX uk_stock_article_chantier
    ON stock_articles(article_id, chantier_id)
    WHERE chantier_id IS NOT NULL;

CREATE UNIQUE INDEX uk_stock_article_depot
    ON stock_articles(article_id)
    WHERE chantier_id IS NULL;

--rollback DROP INDEX uk_stock_article_depot;
--rollback DROP INDEX uk_stock_article_chantier;
--rollback ALTER TABLE stock_articles ADD CONSTRAINT uk_stock_article_chantier UNIQUE (article_id, chantier_id);
