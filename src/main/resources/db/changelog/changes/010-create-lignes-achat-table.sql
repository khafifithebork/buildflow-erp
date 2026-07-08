--liquibase formatted sql
--changeset khafifi:010-create-lignes-achat-table

CREATE TABLE lignes_achat (
                              id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              achat_id        UUID         NOT NULL REFERENCES achats(id) ON DELETE CASCADE,
                              article_id      UUID         NOT NULL REFERENCES articles(id),
                              designation     VARCHAR(255) NOT NULL, -- Snapshot for audit integrity
                              unite           VARCHAR(20)  NOT NULL, -- Snapshot for audit integrity
                              quantite        DECIMAL(15,3) NOT NULL,
                              prix_unitaire   DECIMAL(15,2) NOT NULL,
                              total           DECIMAL(15,2) NOT NULL,
                              created_at      TIMESTAMP    NOT NULL DEFAULT now(),
                              updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_lignes_achat_achat_id ON lignes_achat(achat_id);

--rollback DROP TABLE lignes_achat;