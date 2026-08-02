--liquibase formatted sql
--changeset khafifi:026-create-attachements-table

CREATE TABLE attachements (
                               id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               chantier_id        UUID          NOT NULL REFERENCES chantiers(id) ON DELETE CASCADE,
                               reference          VARCHAR(50)   NOT NULL,
                               date_attachement   DATE          NOT NULL,
                               montant_ht         DECIMAL(15,2) NOT NULL,
                               tva                DECIMAL(15,2) NOT NULL,
                               montant_ttc        DECIMAL(15,2) NOT NULL,
                               statut             VARCHAR(20)   NOT NULL DEFAULT 'SOUMIS',
                               date_encaissement  TIMESTAMP,
                               created_at         TIMESTAMP     NOT NULL DEFAULT now(),
                               updated_at         TIMESTAMP     NOT NULL DEFAULT now(),
                               CONSTRAINT uk_attachements_chantier_ref UNIQUE (chantier_id, reference)
);

CREATE INDEX idx_attachements_chantier_id ON attachements(chantier_id);

--rollback DROP TABLE attachements;
