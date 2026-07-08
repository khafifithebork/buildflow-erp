--liquibase formatted sql
--changeset khafifi:009-create-achat-table

CREATE TABLE achats (
                        id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        ref                     VARCHAR(50)  NOT NULL UNIQUE,
                        fournisseur_id          UUID         NOT NULL REFERENCES fournisseurs(id),
                        chantier_id             UUID         NOT NULL REFERENCES chantiers(id),
                        date_commande           DATE         NOT NULL,
                        date_livraison_prevue   DATE         NOT NULL,
                        statut                  VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
                        ht                      DECIMAL(15,2) NOT NULL DEFAULT 0,
                        tva                     DECIMAL(15,2) NOT NULL DEFAULT 0,
                        ttc                     DECIMAL(15,2) NOT NULL DEFAULT 0,
                        bon_livraison_ref       VARCHAR(50),
                        facture_ref             VARCHAR(50),
                        created_at              TIMESTAMP    NOT NULL DEFAULT now(),
                        updated_at              TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_achats_fournisseur_id ON achats(fournisseur_id);
CREATE INDEX idx_achats_chantier_id ON achats(chantier_id);
CREATE INDEX idx_achats_statut ON achats(statut);

--rollback DROP TABLE achats;