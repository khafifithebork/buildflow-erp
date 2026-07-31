--liquibase formatted sql
--changeset khafifi:019-add-status-to-users
ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
--rollback ALTER TABLE users DROP COLUMN status;
