--liquibase formatted sql
--changeset khafifi:027-create-attachement-lignes-table

CREATE TABLE attachement_lignes (
                                     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     attachement_id  UUID          NOT NULL REFERENCES attachements(id) ON DELETE CASCADE,
                                     bpu_ligne_id    UUID          NOT NULL REFERENCES bpu_lignes(id) ON DELETE RESTRICT,
                                     ancien_cumul    DECIMAL(15,3) NOT NULL,
                                     nouveau_cumul   DECIMAL(15,3) NOT NULL,
                                     montant_ht      DECIMAL(15,2) NOT NULL,
                                     created_at      TIMESTAMP     NOT NULL DEFAULT now(),
                                     updated_at      TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachement_lignes_attachement_id ON attachement_lignes(attachement_id);
CREATE INDEX idx_attachement_lignes_bpu_ligne_id ON attachement_lignes(bpu_ligne_id);

--rollback DROP TABLE attachement_lignes;
