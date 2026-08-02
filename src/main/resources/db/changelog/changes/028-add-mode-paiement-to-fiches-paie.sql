--liquibase formatted sql
--changeset khafifi:028-add-mode-paiement-to-fiches-paie

ALTER TABLE fiches_paie ADD COLUMN mode_paiement VARCHAR(20) NOT NULL DEFAULT 'CAISSE';

--rollback ALTER TABLE fiches_paie DROP COLUMN mode_paiement;
