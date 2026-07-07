--liquibase formatted sql

--changeset khafifi:001-create-users-table
CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role          VARCHAR(50)  NOT NULL,
                       created_at    TIMESTAMP    NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

--rollback DROP TABLE users;