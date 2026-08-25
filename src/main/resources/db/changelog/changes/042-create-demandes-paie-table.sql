--liquibase formatted sql

-- ============================================================================
-- La demande de paie : un décaissement de paie qui ne passe pas par une fiche.
--
-- Une fiche de paie part d'un employé et d'un mois, et le net se calcule à
-- partir des jours, des primes et des retenues. Certains décaissements de paie
-- n'ont ni employé ni calcul — une avance à une équipe, un règlement de
-- main-d'oeuvre au forfait — et n'entrent pas dans ce moule. Les faire passer
-- pour des fiches obligerait à inventer un employé et à saisir un net déjà
-- connu dans des colonnes qui ne le décrivent pas.
--
-- D'où une table à part, volontairement courte : un libellé, une période, un
-- montant. Le chantier et la ligne BPU restent facultatifs, parce qu'une
-- demande n'est pas toujours imputable au moment où on la saisit.
--
-- Le montant est porté tel quel, jamais recalculé : c'est une donnée d'entrée
-- ici, contrairement à fiches_paie.net_a_payer qui est un résultat.
-- ============================================================================

--changeset khafifi:042-create-demandes-paie-table
CREATE TABLE demandes_paie (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference     VARCHAR(50)   NOT NULL UNIQUE,
    libelle       VARCHAR(255)  NOT NULL,
    periode       VARCHAR(7)    NOT NULL,
    chantier_id   UUID          REFERENCES chantiers(id),
    bpu_ligne_id  UUID          REFERENCES bpu_lignes(id),
    montant_net   DECIMAL(15,2) NOT NULL,
    statut        VARCHAR(20)   NOT NULL DEFAULT 'SOUMISE',
    mode_paiement VARCHAR(20),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_demandes_paie_periode ON demandes_paie(periode);
CREATE INDEX idx_demandes_paie_chantier ON demandes_paie(chantier_id);
--rollback DROP TABLE demandes_paie;
