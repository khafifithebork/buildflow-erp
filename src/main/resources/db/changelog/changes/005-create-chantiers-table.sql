--liquibase formatted sql
--changeset khafifi:005-create-chantiers-table
CREATE TABLE chantiers (
                           id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           code                   VARCHAR(50)  NOT NULL UNIQUE,
                           nom                    VARCHAR(255) NOT NULL,
                           client                 VARCHAR(255) NOT NULL,
                           adresse                TEXT,
                           ville                  VARCHAR(100),
                           statut                 VARCHAR(50)  NOT NULL DEFAULT 'EN_PREPARATION',
                           date_debut             DATE         NOT NULL,
                           date_fin               DATE         NOT NULL,
                           budget_ht              DECIMAL(15,2) NOT NULL DEFAULT 0,
                           depenses_ht            DECIMAL(15,2) NOT NULL DEFAULT 0,
                           avancement             INT          NOT NULL DEFAULT 0,
                           chef_projet_nom        VARCHAR(255),
                           nombre_ouvriers        INT          NOT NULL DEFAULT 0,
                           soustraitants_actifs   TEXT[] NULL,
                           created_at             TIMESTAMP    NOT NULL DEFAULT now(),
                           updated_at             TIMESTAMP    NOT NULL DEFAULT now()
);
--rollback DROP TABLE chantiers;