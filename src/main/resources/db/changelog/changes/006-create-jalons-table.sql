--liquibase formatted sql
--changeset khafifi:006-create-jalons-table
CREATE TABLE jalons (
                        id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        chantier_id   UUID         NOT NULL REFERENCES chantiers(id) ON DELETE CASCADE,
                        libelle       VARCHAR(255) NOT NULL,
                        date_prevue   DATE         NOT NULL,
                        date_reelle   DATE,
                        statut        VARCHAR(50)  NOT NULL DEFAULT 'A_FAIRE',
                        created_at    TIMESTAMP    NOT NULL DEFAULT now(),
                        updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_jalons_chantier_id ON jalons(chantier_id);
--rollback DROP TABLE jalons;