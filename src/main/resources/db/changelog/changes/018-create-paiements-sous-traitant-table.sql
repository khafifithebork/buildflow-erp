--liquibase formatted sql
--changeset khafifi:018-create-paiements-sous-traitant-table
CREATE TABLE paiements_sous_traitant (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference          VARCHAR(50)   NOT NULL UNIQUE,
    contrat_id         UUID          NOT NULL REFERENCES contrats_sous_traitant(id),
    montant            DECIMAL(15,2) NOT NULL,
    motif              VARCHAR(255)  NOT NULL,
    statut             VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    date_paiement      DATE,
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_paiements_st_contrat ON paiements_sous_traitant(contrat_id);
--rollback DROP TABLE paiements_sous_traitant;
