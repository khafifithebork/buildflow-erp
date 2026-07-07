--liquibase formatted sql

--changeset khafifi:002-create-categories-articles-table
CREATE TABLE categories_articles (
                                     id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     code       VARCHAR(10)  NOT NULL UNIQUE,
                                     libelle    VARCHAR(100) NOT NULL,
                                     parent_id  UUID NULL REFERENCES categories_articles(id),
                                     created_at TIMESTAMP NOT NULL DEFAULT now(),
                                     updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_categories_articles_parent_id ON categories_articles(parent_id);

--rollback DROP TABLE categories_articles;