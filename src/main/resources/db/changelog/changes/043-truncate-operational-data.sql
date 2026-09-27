--liquibase formatted sql

-- ============================================================================
-- Réinitialisation des données opérationnelles.
--
-- Ce changeset vide toutes les tables métier (achats, stock, paie, caisse,
-- sous-traitance, chantiers, etc.) tout en préservant intégralement les
-- comptes utilisateurs et les données d'authentification/autorisation :
-- users, roles, user_role et revoked_tokens ne sont jamais touchées.
--
-- Les suppressions sont ordonnées en respectant les dépendances de clés
-- étrangères, des tables feuilles vers les tables racines, afin d'éviter
-- toute violation de contrainte.
-- ============================================================================

--changeset khafifi:043-truncate-operational-data
-- Tables feuilles (dépendent d'autres tables opérationnelles)
DELETE FROM paiements_sous_traitant;
DELETE FROM caisse_transactions;
DELETE FROM mouvements_stock;
DELETE FROM lignes_achat;
DELETE FROM attachement_lignes;
DELETE FROM fiches_paie;
DELETE FROM contrats_sous_traitant;
DELETE FROM demandes_paie;

-- Tables intermédiaires
DELETE FROM achats;
DELETE FROM attachements;
DELETE FROM bpu_lignes;
DELETE FROM stock_articles;
DELETE FROM sous_traitants;
DELETE FROM employes;
DELETE FROM jalons;
DELETE FROM chantiers;
DELETE FROM fournisseurs;

-- Tables racines (sans dépendance)
DELETE FROM articles;
DELETE FROM categories_articles;
DELETE FROM code_sequences;
--rollback SELECT 1;
