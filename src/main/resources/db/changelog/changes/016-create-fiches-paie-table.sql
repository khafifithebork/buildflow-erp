--liquibase formatted sql
--changeset khafifi:016-create-fiches-paie-table
CREATE TABLE fiches_paie (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference              VARCHAR(50)   NOT NULL UNIQUE,
    employe_id             UUID          NOT NULL REFERENCES employes(id),
    chantier_id            UUID          NOT NULL REFERENCES chantiers(id),
    periode                VARCHAR(7)    NOT NULL,
    jours_travailles       INTEGER       NOT NULL DEFAULT 0,
    salaire_base           DECIMAL(15,2) NOT NULL,
    heures_supplementaires DECIMAL(10,2) NOT NULL DEFAULT 0,
    montant_heures_supp    DECIMAL(15,2) NOT NULL DEFAULT 0,
    prime_transport         DECIMAL(15,2) NOT NULL DEFAULT 0,
    prime_panier            DECIMAL(15,2) NOT NULL DEFAULT 0,
    autres_primes          DECIMAL(15,2) NOT NULL DEFAULT 0,
    avance                 DECIMAL(15,2) NOT NULL DEFAULT 0,
    deductions_cnss        DECIMAL(15,2) NOT NULL DEFAULT 0,
    deductions_ir          DECIMAL(15,2) NOT NULL DEFAULT 0,
    net_a_payer            DECIMAL(15,2) NOT NULL,
    statut                 VARCHAR(20)   NOT NULL DEFAULT 'BROUILLON',
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_fiches_paie_employe ON fiches_paie(employe_id);
CREATE INDEX idx_fiches_paie_periode ON fiches_paie(periode);
CREATE UNIQUE INDEX idx_fiches_paie_employe_periode ON fiches_paie(employe_id, periode);
--rollback DROP TABLE fiches_paie;
