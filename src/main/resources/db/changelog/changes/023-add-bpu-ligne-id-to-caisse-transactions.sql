--liquibase formatted sql
--changeset khafifi:023-add-bpu-ligne-id-to-caisse-transactions

ALTER TABLE caisse_transactions ADD COLUMN IF NOT EXISTS bpu_ligne_id UUID NULL REFERENCES bpu_lignes(id) ON DELETE SET NULL;
CREATE INDEX idx_caisse_transactions_bpu_ligne_id ON caisse_transactions(bpu_ligne_id);

--rollback ALTER TABLE caisse_transactions DROP COLUMN bpu_ligne_id;
