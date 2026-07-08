--liquibase formatted sql
--changeset khafifi:014-create-caisses-table
CREATE TABLE caisses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL UNIQUE,
    libelle         VARCHAR(255) NOT NULL,
    chantier_id     UUID         NOT NULL REFERENCES chantiers(id),
    solde           DECIMAL(15,2) NOT NULL DEFAULT 0,
    seuil_minimum   DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
--rollback DROP TABLE caisses;
