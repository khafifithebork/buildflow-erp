--liquibase formatted sql
--changeset khafifi:029-add-advanced-fields-to-contrats-sous-traitant

ALTER TABLE contrats_sous_traitant ADD COLUMN avance_demandee_ht DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE contrats_sous_traitant ADD COLUMN retenue_garantie_ht DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE contrats_sous_traitant ADD COLUMN montant_realise_ht DECIMAL(15,2) NOT NULL DEFAULT 0;
ALTER TABLE contrats_sous_traitant ADD COLUMN dossier_statut VARCHAR(20) NOT NULL DEFAULT 'INCOMPLET';

--rollback ALTER TABLE contrats_sous_traitant DROP COLUMN avance_demandee_ht, DROP COLUMN retenue_garantie_ht, DROP COLUMN montant_realise_ht, DROP COLUMN dossier_statut;
