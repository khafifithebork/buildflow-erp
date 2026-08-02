--liquibase formatted sql
--changeset khafifi:021-create-bpu-lignes-table

CREATE TABLE bpu_lignes (
                             id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             chantier_id        UUID          NOT NULL REFERENCES chantiers(id) ON DELETE CASCADE,
                             ref                VARCHAR(50)   NOT NULL,
                             designation        VARCHAR(255)  NOT NULL,
                             unite              VARCHAR(20)   NOT NULL,
                             qte_prevue         DECIMAL(15,3) NOT NULL,
                             pu_ht              DECIMAL(15,2) NOT NULL,
                             budget_prevu_ht    DECIMAL(15,2) NOT NULL,
                             created_at         TIMESTAMP     NOT NULL DEFAULT now(),
                             updated_at         TIMESTAMP     NOT NULL DEFAULT now(),
                             CONSTRAINT uk_bpu_lignes_chantier_ref UNIQUE (chantier_id, ref)
);

CREATE INDEX idx_bpu_lignes_chantier_id ON bpu_lignes(chantier_id);

--rollback DROP TABLE bpu_lignes;
