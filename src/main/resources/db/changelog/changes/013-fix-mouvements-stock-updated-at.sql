--liquibase formatted sql
--changeset khafifi:013-fix-mouvements-stock-updated-at

ALTER TABLE mouvements_stock
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT now();

--rollback ALTER TABLE mouvements_stock DROP COLUMN updated_at;