--liquibase formatted sql

CREATE TABLE articles (
                          id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          code                        VARCHAR(50)  NOT NULL UNIQUE,
                          designation                 VARCHAR(255) NOT NULL,
                          description                 TEXT NULL,
                          categorie_id                UUID NOT NULL REFERENCES categories_articles(id),
                          unite                       VARCHAR(20)  NOT NULL,
                          prix_achat_ref              DECIMAL(15,2) NOT NULL,
                          tva_rate                    DECIMAL(5,2)  NOT NULL,
                          actif                       BOOLEAN NOT NULL DEFAULT true,
                          fournisseurs_preferentiels  TEXT[] NULL,
                          created_at                  TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at                  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_articles_categorie_id ON articles(categorie_id);

--rollback DROP TABLE articles;