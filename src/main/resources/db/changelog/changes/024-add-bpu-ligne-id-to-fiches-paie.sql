--liquibase formatted sql
--changeset khafifi:024-add-bpu-ligne-id-to-fiches-paie

ALTER TABLE fiches_paie ADD COLUMN IF NOT EXISTS bpu_ligne_id UUID NULL REFERENCES bpu_lignes(id) ON DELETE SET NULL;
CREATE INDEX idx_fiches_paie_bpu_ligne_id ON fiches_paie(bpu_ligne_id);

--rollback ALTER TABLE fiches_paie DROP COLUMN bpu_ligne_id;
