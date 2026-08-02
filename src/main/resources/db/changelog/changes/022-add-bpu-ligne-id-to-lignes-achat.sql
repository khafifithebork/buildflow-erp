--liquibase formatted sql
--changeset khafifi:022-add-bpu-ligne-id-to-lignes-achat

ALTER TABLE lignes_achat ADD COLUMN IF NOT EXISTS bpu_ligne_id UUID NULL REFERENCES bpu_lignes(id) ON DELETE SET NULL;
CREATE INDEX idx_lignes_achat_bpu_ligne_id ON lignes_achat(bpu_ligne_id);

--rollback ALTER TABLE lignes_achat DROP COLUMN bpu_ligne_id;
