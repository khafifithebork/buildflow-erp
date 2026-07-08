--liquibase formatted sql
--changeset khafifi:015-create-caisse-transactions-table
CREATE TABLE caisse_transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caisse_id         UUID          NOT NULL REFERENCES caisses(id),
    type_transaction  VARCHAR(20)   NOT NULL,
    montant           DECIMAL(15,2) NOT NULL,
    motif             VARCHAR(255)  NOT NULL,
    reference_document VARCHAR(100),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_caisse_transactions_caisse_id ON caisse_transactions(caisse_id);
--rollback DROP TABLE caisse_transactions;
