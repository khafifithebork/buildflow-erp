--liquibase formatted sql
--changeset khafifi:012-create-mouvements-stock-table

CREATE TABLE mouvements_stock (
                                  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  stock_article_id  UUID NOT NULL REFERENCES stock_articles(id),
                                  type_mouvement    VARCHAR(20) NOT NULL, -- ENTREE, SORTIE, TRANSFERT
                                  quantite          DECIMAL(15,3) NOT NULL,
                                  document_ref      VARCHAR(100), -- e.g., "CMD-2026-001" (Achat ref)
                                  created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mouvements_stock_article ON mouvements_stock(stock_article_id);

--rollback DROP TABLE mouvements_stock;