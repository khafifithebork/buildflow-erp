--liquibase formatted sql
--changeset khafifi:025-add-bpu-ligne-id-to-contrats-sous-traitant

ALTER TABLE contrats_sous_traitant ADD COLUMN IF NOT EXISTS bpu_ligne_id UUID NULL REFERENCES bpu_lignes(id) ON DELETE SET NULL;
CREATE INDEX idx_contrats_sous_traitant_bpu_ligne_id ON contrats_sous_traitant(bpu_ligne_id);

--rollback ALTER TABLE contrats_sous_traitant DROP COLUMN bpu_ligne_id;
