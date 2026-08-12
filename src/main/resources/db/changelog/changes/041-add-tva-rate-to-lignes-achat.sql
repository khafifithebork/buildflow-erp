--liquibase formatted sql

-- ============================================================================
-- La ligne de commande porte son taux de TVA, comme elle porte déjà sa
-- désignation, son unité et son prix.
--
-- articles.tva_rate existait, était saisi, validé, exporté — et ignoré. Trois
-- services calculaient la taxe avec une constante en dur. Tant que tout le
-- catalogue est au même taux, les deux nombres coïncident et le champ paraît
-- utilisé. Un article à 14 ou 7 % donnait un TTC faux, donc un débit de caisse
-- faux, donc des décaissements faux.
--
-- Le taux est figé à la commande et non relu chez l'article : une commande
-- passée à 14 % doit rester à 14 % même si le référentiel change ensuite. Même
-- raison que pour la désignation — la ligne est un instantané, pas un renvoi.
--
-- Backfill à 0.20 : c'est le taux auquel les lignes existantes ont réellement
-- été calculées, pas le taux courant. Les recalculer à 10 % changerait des
-- montants déjà facturés.
-- ============================================================================

--changeset khafifi:041-add-tva-rate-to-lignes-achat
ALTER TABLE lignes_achat
    ADD COLUMN tva_rate DECIMAL(5,4) NOT NULL DEFAULT 0.20;

--rollback ALTER TABLE lignes_achat DROP COLUMN tva_rate;
