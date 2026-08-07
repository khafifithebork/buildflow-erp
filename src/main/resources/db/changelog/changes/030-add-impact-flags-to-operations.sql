--liquibase formatted sql

--changeset khafifi:030-add-impact-flags-to-achats
-- Operational billing indicators on purchase orders.
--   impact_analytique_chantier : "L'achat a-t-il réellement servi au chantier ?"
--   impact_comptable_fiscal    : "Y a-t-il une facture officielle à déclarer ?"
-- NOT NULL + DEFAULT FALSE so existing rows are backfilled to false and old
-- INSERTs that omit the columns keep working.
ALTER TABLE achats
    ADD COLUMN impact_analytique_chantier BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN impact_comptable_fiscal    BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_achats_impact_analytique_chantier ON achats(impact_analytique_chantier);
CREATE INDEX idx_achats_impact_comptable_fiscal ON achats(impact_comptable_fiscal);

--rollback DROP INDEX idx_achats_impact_comptable_fiscal;
--rollback DROP INDEX idx_achats_impact_analytique_chantier;
--rollback ALTER TABLE achats DROP COLUMN impact_comptable_fiscal;
--rollback ALTER TABLE achats DROP COLUMN impact_analytique_chantier;

--changeset khafifi:030-add-impact-flags-to-caisse-transactions
-- Same two indicators on cash operations (Caisse view).
ALTER TABLE caisse_transactions
    ADD COLUMN impact_analytique_chantier BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN impact_comptable_fiscal    BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_caisse_txn_impact_analytique_chantier ON caisse_transactions(impact_analytique_chantier);
CREATE INDEX idx_caisse_txn_impact_comptable_fiscal ON caisse_transactions(impact_comptable_fiscal);

--rollback DROP INDEX idx_caisse_txn_impact_comptable_fiscal;
--rollback DROP INDEX idx_caisse_txn_impact_analytique_chantier;
--rollback ALTER TABLE caisse_transactions DROP COLUMN impact_comptable_fiscal;
--rollback ALTER TABLE caisse_transactions DROP COLUMN impact_analytique_chantier;
