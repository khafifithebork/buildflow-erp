--liquibase formatted sql

-- ============================================================================
-- Deuxième réinitialisation des données opérationnelles.
--
-- Même périmètre que 043 : tout est vidé sauf les comptes utilisateurs. Comme
-- Liquibase n'exécute un changeset qu'une seule fois, 043 ne peut pas être
-- rejoué ; celui-ci répète l'opération avec un identifiant neuf.
--
-- Au passage : ne jamais commencer une ligne de commentaire par « changeset »
-- ou « rollback ». Liquibase tolère l'espace après les deux tirets et prend
-- « -- changeset ... » pour la directive, puis échoue à lire la suite comme
-- auteur:identifiant. C'est ce qui a fait planter la première version de ce
-- fichier.
--
-- PRÉSERVÉ — deux tables, et seulement deux :
--   users           les comptes et leurs rôles (le rôle est une colonne enum
--                   sur users ; il n'existe ni table roles ni user_role)
--   revoked_tokens  l'authentification
--
-- SUPPRIMÉ — tout le reste, y compris le référentiel : chantiers,
-- fournisseurs, sous-traitants, employés, articles et catégories. Ce n'est pas
-- un vidage des seules écritures : après ce changeset, un utilisateur se
-- connecte dans une base vide.
--
-- code_sequences est vidée, donc la numérotation repart à FRN-001,
-- CH-2026-001, etc. Voulu, et irréversible.
--
-- IRRÉVERSIBLE : le rollback est un no-op, et s'exécute tout seul au prochain
-- déploiement. Prendre une sauvegarde avant de fusionner.
--
-- Écart avec 043 : paiements_achat, créée par 044 donc absente de 043. Sa clé
-- vers achats est ON DELETE CASCADE, mais elle est listée explicitement — se
-- reposer sur la cascade marche aujourd'hui et cesserait de marcher sans bruit
-- le jour où cette clé change. Même raisonnement que pour caisses dans 043.
--
-- Les suppressions sont ordonnées des tables feuilles vers les tables racines,
-- pour ne violer aucune contrainte de clé étrangère.
-- ============================================================================

--changeset khafifi:046-reset-operational-data
-- Tables feuilles (dépendent d'autres tables opérationnelles)
DELETE FROM paiements_achat;
DELETE FROM paiements_sous_traitant;
DELETE FROM caisse_transactions;
DELETE FROM mouvements_stock;
DELETE FROM lignes_achat;
DELETE FROM attachement_lignes;
DELETE FROM fiches_paie;
DELETE FROM contrats_sous_traitant;
DELETE FROM demandes_paie;
-- L'audit des modes de paiement ne porte aucune clé étrangère : il désigne son
-- document par (type, id) plutôt que par une FK, donc aucune cascade ne le
-- vide. Sans cette ligne, il resterait des écritures d'audit pointant vers des
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
-- Listée explicitement bien que caisses.chantier_id soit ON DELETE CASCADE,
-- pour la même raison que paiements_achat ci-dessus. Les écritures de caisse
-- sont déjà parties plus haut, l'ordre tient.
DELETE FROM caisses;
DELETE FROM chantiers;
DELETE FROM fournisseurs;

-- Tables racines (sans dépendance)
DELETE FROM articles;
DELETE FROM categories_articles;
DELETE FROM code_sequences;
--rollback SELECT 1;
