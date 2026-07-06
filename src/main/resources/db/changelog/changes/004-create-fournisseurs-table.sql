--liquibase formatted sql

CREATE TABLE fournisseurs (
                              id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              code                 VARCHAR(50)  NOT NULL UNIQUE,
                              raison_sociale       VARCHAR(255) NOT NULL,
                              ice                  VARCHAR(50),
                              contact              VARCHAR(255),
                              telephone            VARCHAR(20),
                              email                VARCHAR(255),
                              ville                VARCHAR(100),
                              adresse              TEXT,
                              rib                  VARCHAR(50),
                              banque               VARCHAR(100),
                              statut               VARCHAR(20) NOT NULL DEFAULT 'ACTIF',
                              categorie_articles   TEXT[] NULL,
                              total_achats_annee   DECIMAL(15,2) NOT NULL DEFAULT 0,
                              solde_impaye         DECIMAL(15,2) NOT NULL DEFAULT 0,
                              created_at           TIMESTAMP NOT NULL DEFAULT now(),
                              updated_at           TIMESTAMP NOT NULL DEFAULT now()
);
--rollback DROP TABLE fournisseurs;