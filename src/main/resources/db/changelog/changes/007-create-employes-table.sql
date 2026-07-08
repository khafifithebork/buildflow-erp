--liquibase formatted sql
--changeset khafifi:007-create-employes-table

CREATE TABLE employes (
                          id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          matricule           VARCHAR(50)   NOT NULL UNIQUE,
                          nom                 VARCHAR(100)  NOT NULL,
                          prenom              VARCHAR(100)  NOT NULL,
                          role                VARCHAR(50)   NOT NULL,
                          poste               VARCHAR(255)  NOT NULL,
                          departement         VARCHAR(100)  NOT NULL,
                          telephone           VARCHAR(20),
                          email               VARCHAR(255),
                          date_embauche       DATE          NOT NULL,
                          chantier_actuel_id  UUID          NULL REFERENCES chantiers(id) ON DELETE SET NULL,
                          statut              VARCHAR(20)   NOT NULL DEFAULT 'ACTIF',
                          salaire_brut        DECIMAL(15,2) NOT NULL DEFAULT 0,
                          type_contrat        VARCHAR(20)   NOT NULL,
                          created_at          TIMESTAMP     NOT NULL DEFAULT now(),
                          updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_employes_chantier_actuel_id ON employes(chantier_actuel_id);

--rollback DROP TABLE employes;