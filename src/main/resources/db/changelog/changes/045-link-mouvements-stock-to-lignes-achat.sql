--liquibase formatted sql

-- ============================================================================
-- Rattacher chaque entrée de stock à la ligne de commande qui l'a produite.
--
-- Le stock n'est valorisé qu'en moyenne pondérée par ligne (cout_unitaire),
-- donc aucune unité détenue ne porte d'origine. Le calcul 1 ne pouvait pas
-- écarter le stock né d'un achat à effet fiscal, et prenait le stock global
-- faute de mieux.
--
-- document_ref portait déjà achats.ref, et achats.ref est unique : le lien
-- existait en texte libre. Une clé le remplace, pour deux raisons.
--
--  1. Le prix. Le prorata retenu est en valeur, pas en quantité — deux
--     fournisseurs ne vendent pas le même article au même prix. Il faut donc
--     le prix d'entrée, que mouvements_stock ne porte pas et que seule la
--     ligne de commande connaît.
--  2. L'ambiguïté. Une commande peut porter deux lignes du même article.
--     Joindre sur (ref, article) apparie alors 2 mouvements à 2 lignes, soit
--     4 paires, et double le montant. Une clé par mouvement ferme ça.
--
-- Nullable à dessein : une entrée saisie à la main, un transfert ou un
-- ajustement n'a pas de commande d'origine, et ne doit pas s'en inventer une.
-- ON DELETE SET NULL — le journal des mouvements survit à la commande, c'est
-- un registre, pas une vue.
-- ============================================================================

--changeset khafifi:045-add-ligne-achat-id-to-mouvements-stock
ALTER TABLE mouvements_stock
    ADD COLUMN ligne_achat_id UUID REFERENCES lignes_achat(id) ON DELETE SET NULL;

CREATE INDEX idx_mouvements_stock_ligne_achat ON mouvements_stock(ligne_achat_id);

--rollback DROP INDEX IF EXISTS idx_mouvements_stock_ligne_achat;
--rollback ALTER TABLE mouvements_stock DROP COLUMN ligne_achat_id;

-- ----------------------------------------------------------------------------
-- Reprise de l'historique, en deux passes.
--
-- L'article de la ligne de stock se lit en sous-requête et non en jointure :
-- PostgreSQL n'admet pas la table cible d'un UPDATE dans le ON du FROM.
--
-- Passe 1, sur (ref, article, quantité) : l'appariement exact. La quantité du
-- mouvement est la quantité de la ligne arrondie à trois décimales par
-- approvisionnerDepuisAchat, d'où le ROUND des deux côtés.
--
-- Passe 2, sur (ref, article) : ce que la première n'a pas atteint. Deux
-- lignes du même article et de quantités différentes y restent arbitraires —
-- PostgreSQL en choisit une. L'arbitraire est sans effet quand les deux
-- lignes partagent leur prix, et sans remède sinon : rien n'enregistre quelle
-- ligne a produit quel mouvement avant cette migration.
-- ----------------------------------------------------------------------------

--changeset khafifi:045-backfill-ligne-achat-id-exact
UPDATE mouvements_stock m
SET ligne_achat_id = l.id
FROM achats a
     JOIN lignes_achat l ON l.achat_id = a.id
WHERE m.type_mouvement = 'ENTREE'
  AND m.ligne_achat_id IS NULL
  AND a.ref = m.document_ref
  AND l.article_id = (SELECT s.article_id FROM stock_articles s
                      WHERE s.id = m.stock_article_id)
  AND ROUND(l.quantite::numeric, 3) = ROUND(m.quantite, 3);

--rollback UPDATE mouvements_stock SET ligne_achat_id = NULL;

--changeset khafifi:045-backfill-ligne-achat-id-par-article
UPDATE mouvements_stock m
SET ligne_achat_id = l.id
FROM achats a
     JOIN lignes_achat l ON l.achat_id = a.id
WHERE m.type_mouvement = 'ENTREE'
  AND m.ligne_achat_id IS NULL
  AND a.ref = m.document_ref
  AND l.article_id = (SELECT s.article_id FROM stock_articles s
                      WHERE s.id = m.stock_article_id);

--rollback SELECT 1;
