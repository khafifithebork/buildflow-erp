--liquibase formatted sql

-- ============================================================================
-- Payment mode becomes a first-class, chosen value on all three payable
-- documents instead of being implicitly "par caisse".
--
--   VIREMENT | CHEQUE | EFFET | CAISSE
--
-- Only CAISSE moves the chantier's cash balance. The other three settle
-- outside the caisse and must not debit it.
--
-- Backward compatibility:
--   * fiches_paie already had mode_paiement with the legacy CAISSE default;
--     existing rows keep whatever they hold and stay readable.
--   * achats and paiements_sous_traitant always debited the caisse, so they
--     were implicitly CAISSE. Existing rows are backfilled to CAISSE to record
--     what actually happened; the columns are nullable so a document that has
--     not been paid yet carries no mode at all rather than a misleading default.
-- ============================================================================

--changeset khafifi:034-add-mode-paiement-to-achats
-- Nullable on purpose: an unpaid order has no payment mode yet.
ALTER TABLE achats ADD COLUMN mode_paiement VARCHAR(20);

-- Orders already settled were, by definition, paid out of the caisse.
UPDATE achats SET mode_paiement = 'CAISSE' WHERE statut = 'PAYE';

--rollback ALTER TABLE achats DROP COLUMN mode_paiement;

--changeset khafifi:034-add-mode-paiement-to-paiements-sous-traitant
ALTER TABLE paiements_sous_traitant ADD COLUMN mode_paiement VARCHAR(20);

UPDATE paiements_sous_traitant SET mode_paiement = 'CAISSE' WHERE statut = 'PAYE';

--rollback ALTER TABLE paiements_sous_traitant DROP COLUMN mode_paiement;

--changeset khafifi:034-fiches-paie-mode-paiement-no-longer-defaults-to-caisse
-- CAISSE stops being the implicit default. The column becomes nullable so an
-- unpaid payslip carries no mode at all, and the meaningless 'CAISSE' sitting
-- on not-yet-paid rows is cleared. Rows already PAYEE keep their real mode.
ALTER TABLE fiches_paie ALTER COLUMN mode_paiement DROP DEFAULT;
ALTER TABLE fiches_paie ALTER COLUMN mode_paiement DROP NOT NULL;

UPDATE fiches_paie SET mode_paiement = NULL WHERE statut <> 'PAYEE';

--rollback UPDATE fiches_paie SET mode_paiement = 'CAISSE' WHERE mode_paiement IS NULL;
--rollback ALTER TABLE fiches_paie ALTER COLUMN mode_paiement SET NOT NULL;
--rollback ALTER TABLE fiches_paie ALTER COLUMN mode_paiement SET DEFAULT 'CAISSE';

--changeset khafifi:034-create-mode-paiement-historique
-- Audit trail of every mode assignment and change, across all three document
-- types. Identified by (type_document, document_id) rather than a foreign key,
-- since it spans three unrelated tables. Append-only: never updated, never
-- deleted, and intentionally not cascaded when its subject is removed.
CREATE TABLE mode_paiement_historique (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_document  VARCHAR(30)  NOT NULL,
    document_id    UUID         NOT NULL,
    document_ref   VARCHAR(50),
    ancien_mode    VARCHAR(20),
    nouveau_mode   VARCHAR(20)  NOT NULL,
    modifie_par    VARCHAR(255),
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_mode_paiement_hist_document
    ON mode_paiement_historique(type_document, document_id, created_at DESC);

--rollback DROP TABLE mode_paiement_historique;
