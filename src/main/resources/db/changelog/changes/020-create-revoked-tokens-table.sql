--liquibase formatted sql

--changeset security:020-create-revoked-tokens-table
CREATE TABLE revoked_tokens (
    jti        VARCHAR(255) PRIMARY KEY,
    expires_at TIMESTAMP    NOT NULL
);

--rollback DROP TABLE revoked_tokens;
