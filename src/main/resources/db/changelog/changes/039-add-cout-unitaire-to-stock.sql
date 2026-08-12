--liquibase formatted sql

-- ============================================================================
-- Stock is valued at what was paid for it, not at the article's list price.
--
-- Until now every valuation query multiplied the quantity by
-- articles.prix_achat_ref — a reference price that never moves. So an order
-- received at 150/u and later re-priced kept its stock valued at 100/u, and
-- the marge nette comptable carried the difference as a loss that never
-- happened: buy 10 units, re-price 100 -> 150, and the dashboard drops 500
-- with nothing to explain it.
--
-- cout_unitaire is a weighted average of what the material on this line
-- actually cost. Receiving goods folds the purchase price into the average;
-- re-pricing an order that has already been received corrects it.
--
-- Backfilled from prix_achat_ref so every existing figure is unchanged the
-- moment this runs — the column only starts to differ as new stock arrives.
-- ============================================================================

--changeset khafifi:039-add-cout-unitaire-to-stock-articles
ALTER TABLE stock_articles
    ADD COLUMN cout_unitaire DOUBLE PRECISION NOT NULL DEFAULT 0;

UPDATE stock_articles s
SET cout_unitaire = COALESCE(a.prix_achat_ref, 0)
FROM articles a
WHERE a.id = s.article_id;

--rollback ALTER TABLE stock_articles DROP COLUMN cout_unitaire;
