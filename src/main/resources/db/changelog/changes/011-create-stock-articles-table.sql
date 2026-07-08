--liquibase formatted sql
--changeset khafifi:011-create-stock-articles-table

CREATE TABLE stock_articles (
                                id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                article_id          UUID NOT NULL REFERENCES articles(id),
                                chantier_id         UUID NOT NULL REFERENCES chantiers(id),
                                quantite_theorique  DECIMAL(15,3) NOT NULL DEFAULT 0,
                                seuil_alerte        DECIMAL(15,3) NOT NULL DEFAULT 0,
                                created_at          TIMESTAMP NOT NULL DEFAULT now(),
                                updated_at          TIMESTAMP NOT NULL DEFAULT now(),
                                CONSTRAINT uk_stock_article_chantier UNIQUE (article_id, chantier_id)
);

CREATE INDEX idx_stock_articles_article ON stock_articles(article_id);
CREATE INDEX idx_stock_articles_chantier ON stock_articles(chantier_id);

--rollback DROP TABLE stock_articles;