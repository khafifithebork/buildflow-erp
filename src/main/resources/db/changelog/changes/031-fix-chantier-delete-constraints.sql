--liquibase formatted sql

-- ============================================================================
-- Bug fix: a Chantier could never be deleted.
--
-- ChantierServiceImpl.create() auto-provisions one Caisse per chantier, and
-- caisses.chantier_id was created with the PostgreSQL default FK action
-- (NO ACTION / RESTRICT). So even a brand-new chantier with no business data
-- had a child row, and DELETE always raised a foreign-key violation.
--
-- Business rule applied here:
--   * A Caisse is an artifact OF the chantier (auto-created, never created
--     independently) -> it must follow the chantier. ON DELETE CASCADE.
--   * A CaisseTransaction cannot outlive its Caisse -> ON DELETE CASCADE.
--   * Real business documents (achats, fiches de paie, contrats de
--     sous-traitance, lignes de stock) deliberately KEEP their restrictive FK.
--     The service layer pre-checks them and returns an explicit 409 telling the
--     user what to remove first, instead of silently destroying accounting data.
--   * employes.chantier_actuel_id and jalons/bpu_lignes/attachements already
--     had SET NULL / CASCADE and are unchanged.
-- ============================================================================

--changeset khafifi:031-caisses-chantier-fk-on-delete-cascade
ALTER TABLE caisses DROP CONSTRAINT caisses_chantier_id_fkey;
ALTER TABLE caisses
    ADD CONSTRAINT caisses_chantier_id_fkey
    FOREIGN KEY (chantier_id) REFERENCES chantiers(id) ON DELETE CASCADE;

--rollback ALTER TABLE caisses DROP CONSTRAINT caisses_chantier_id_fkey;
--rollback ALTER TABLE caisses ADD CONSTRAINT caisses_chantier_id_fkey FOREIGN KEY (chantier_id) REFERENCES chantiers(id);

--changeset khafifi:031-caisse-transactions-caisse-fk-on-delete-cascade
ALTER TABLE caisse_transactions DROP CONSTRAINT caisse_transactions_caisse_id_fkey;
ALTER TABLE caisse_transactions
    ADD CONSTRAINT caisse_transactions_caisse_id_fkey
    FOREIGN KEY (caisse_id) REFERENCES caisses(id) ON DELETE CASCADE;

--rollback ALTER TABLE caisse_transactions DROP CONSTRAINT caisse_transactions_caisse_id_fkey;
--rollback ALTER TABLE caisse_transactions ADD CONSTRAINT caisse_transactions_caisse_id_fkey FOREIGN KEY (caisse_id) REFERENCES caisses(id);

--changeset khafifi:031-index-caisses-chantier-id
-- The pre-delete reference check and getOrCreateCaisse() both filter on this.
CREATE INDEX IF NOT EXISTS idx_caisses_chantier_id ON caisses(chantier_id);

--rollback DROP INDEX idx_caisses_chantier_id;
