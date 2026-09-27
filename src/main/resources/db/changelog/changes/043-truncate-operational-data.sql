--liquibase formatted sql

-- ============================================================================
-- Réinitialisation des données opérationnelles.
--
-- Ce changeset vide toutes les tables métier (achats, stock, paie, caisse,
-- sous-traitance, chantiers, etc.) tout en préservant les comptes utilisateurs
-- et les données d'authentification : users et revoked_tokens ne sont jamais
-- touchées.
--
-- Ce sont les deux seules tables préservées. Le rôle d'un utilisateur est une
-- colonne enum sur users — il n'existe ni table roles ni table user_role.
--
-- code_sequences est vidée : la numérotation repart donc à FRN-001, CH-2026-001,
-- etc. C'est voulu pour un démarrage propre, mais irréversible.
--
-- Rappel : ce changeset s'exécute tout seul au prochain déploiement, une seule
-- fois, et son rollback est un no-op. Prendre une sauvegarde avant de fusionner.
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
-- L'audit des modes de paiement ne porte aucune clé étrangère : il désigne son
-- document par (type, id) plutôt que par une FK, donc aucune cascade ne le vide.
-- Sans cette ligne, il resterait des écritures d'audit pointant vers des
-- documents supprimés.
DELETE FROM mode_paiement_historique;

-- Tables intermédiaires
DELETE FROM achats;
DELETE FROM attachements;
DELETE FROM bpu_lignes;
DELETE FROM stock_articles;
DELETE FROM sous_traitants;
DELETE FROM employes;
DELETE FROM jalons;
-- Listée explicitement bien que caisses.chantier_id soit ON DELETE CASCADE :
-- s'en remettre à la cascade marche aujourd'hui et cesserait de marcher sans
-- bruit le jour où cette FK devient nullable. Les écritures de caisse sont
-- déjà parties plus haut, l'ordre tient.
DELETE FROM caisses;
DELETE FROM chantiers;
DELETE FROM fournisseurs;

-- Tables racines (sans dépendance)
DELETE FROM articles;
DELETE FROM categories_articles;
DELETE FROM code_sequences;
--rollback SELECT 1;
