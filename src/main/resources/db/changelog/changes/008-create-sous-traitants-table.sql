--liquibase formatted sql
--changeset khafifi:008-create-sous-traitants-table

CREATE TABLE sous_traitants (
                                id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                code                    VARCHAR(50)   NOT NULL UNIQUE,
                                raison_sociale          VARCHAR(255)  NOT NULL,
                                ice                     VARCHAR(50)   NOT NULL UNIQUE,
                                specialite              VARCHAR(100)  NOT NULL,
                                contact                 VARCHAR(255),
                                telephone               VARCHAR(20),
                                email                   VARCHAR(255),
                                ville                   VARCHAR(100),
                                adresse                 TEXT,
                                statut                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIF',
                                nombre_contrats_actifs  INT           NOT NULL DEFAULT 0,
                                montant_total_paye      DECIMAL(15,2) NOT NULL DEFAULT 0,
                                created_at              TIMESTAMP     NOT NULL DEFAULT now(),
                                updated_at              TIMESTAMP     NOT NULL DEFAULT now()
);

--rollback DROP TABLE sous_traitants;