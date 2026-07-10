--liquibase formatted sql
--changeset khafifi:017-create-contrats-sous-traitant-table
CREATE TABLE contrats_sous_traitant (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference          VARCHAR(50)   NOT NULL UNIQUE,
    sous_traitant_id   UUID          NOT NULL REFERENCES sous_traitants(id),
    chantier_id        UUID          NOT NULL REFERENCES chantiers(id),
    objet              VARCHAR(255)  NOT NULL,
    montant_ht         DECIMAL(15,2) NOT NULL,
    tva                DECIMAL(15,2) NOT NULL DEFAULT 0,
    montant_ttc        DECIMAL(15,2) NOT NULL,
    montant_paye       DECIMAL(15,2) NOT NULL DEFAULT 0,
    date_debut         DATE          NOT NULL,
    date_fin           DATE          NOT NULL,
    statut             VARCHAR(20)   NOT NULL DEFAULT 'EN_COURS',
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_contrats_st_sous_traitant ON contrats_sous_traitant(sous_traitant_id);
CREATE INDEX idx_contrats_st_chantier ON contrats_sous_traitant(chantier_id);
--rollback DROP TABLE contrats_sous_traitant;
