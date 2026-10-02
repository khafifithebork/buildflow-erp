--liquibase formatted sql

-- ============================================================================
-- Le reglement partiel d'une commande fournisseur.
--
-- Jusqu'ici une commande etait payee ou ne l'etait pas : AchatStatut passait a
-- PAYE et la totalite du TTC basculait d'un coup de la dette vers le deja
-- paye. Regler 10 000 sur une dette de 100 000 n'existait pas.
--
-- Deux ajouts, calques sur ce que la sous-traitance fait deja :
--
--   achats.montant_paye   le cumul regle, comme contrats_sous_traitant
--   paiements_achat       le detail date de chaque reglement
--
-- Le cumul seul ne suffisait pas : les decaissements du tableau de bord sont
-- bornes par periode, et une colonne cumulative ne porte aucune date. Le
-- registre repond a cette question, le cumul repond a « combien reste-t-il du ».
--
-- Backfill : les commandes deja PAYE voient montant_paye prendre leur TTC.
-- Sans quoi elles reapparaitraient integralement en dette au premier calcul.
-- Elles n'ont en revanche aucune ligne de reglement datee — l'information
-- n'existe nulle part retroactivement, et l'inventer fausserait les periodes.
-- ============================================================================

--changeset khafifi:044-add-montant-paye-to-achats
ALTER TABLE achats
    ADD COLUMN montant_paye DECIMAL(15,2) NOT NULL DEFAULT 0;

UPDATE achats SET montant_paye = ttc WHERE statut = 'PAYE';
--rollback ALTER TABLE achats DROP COLUMN montant_paye;

--changeset khafifi:044-create-paiements-achat-table
CREATE TABLE paiements_achat (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference     VARCHAR(50)   NOT NULL UNIQUE,
    achat_id      UUID          NOT NULL REFERENCES achats(id) ON DELETE CASCADE,
    montant       DECIMAL(15,2) NOT NULL,
    date_paiement DATE          NOT NULL,
    mode_paiement VARCHAR(20),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_paiements_achat_achat ON paiements_achat(achat_id);
CREATE INDEX idx_paiements_achat_date ON paiements_achat(date_paiement);
--rollback DROP TABLE paiements_achat;
