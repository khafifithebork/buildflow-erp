--liquibase formatted sql

-- ============================================================================
-- Server-side generation of entity codes (FRN-001, CH-2026-001, …).
--
-- One counter per scope. A scope is the entity plus whatever the numbering
-- resets on: nothing (FRN-001), the year (CH-2026-001), the payroll period
-- (FDP-2026-07-001) or the parent chantier (BPU-001 within each chantier).
--
-- next_val always holds the NEXT number to hand out. Allocation is a single
-- atomic statement (see CodeGeneratorImpl):
--
--   INSERT INTO code_sequences (scope, next_val) VALUES (:scope, 2)
--   ON CONFLICT (scope) DO UPDATE SET next_val = code_sequences.next_val + 1
--   RETURNING next_val - 1;
--
-- The row lock is held to commit, so numbering is gapless per committed
-- transaction — which is what accounting document numbering requires.
-- ============================================================================

--changeset khafifi:032-create-code-sequences-table
CREATE TABLE code_sequences (
    scope      VARCHAR(120) PRIMARY KEY,
    next_val   BIGINT    NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

--rollback DROP TABLE code_sequences;

--changeset khafifi:032-seed-code-sequences-from-existing-data
-- Start every counter ABOVE the highest number already in use, so generated
-- codes can never collide with records created before this feature. Rows whose
-- code does not match the expected shape are ignored; a scope with no matching
-- rows is simply absent and starts at 1 on first use.

-- ── Flat counters (master data) ──────────────────────────────────────────
INSERT INTO code_sequences (scope, next_val)
SELECT 'FOURNISSEUR', COALESCE(MAX((regexp_match(code, '^FRN-(\d+)$'))[1]::BIGINT), 0) + 1 FROM fournisseurs
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'SOUS_TRAITANT', COALESCE(MAX((regexp_match(code, '^ST-(\d+)$'))[1]::BIGINT), 0) + 1 FROM sous_traitants
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'EMPLOYE', COALESCE(MAX((regexp_match(matricule, '^EMP-(\d+)$'))[1]::BIGINT), 0) + 1 FROM employes
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'ARTICLE', COALESCE(MAX((regexp_match(code, '^ART-(\d+)$'))[1]::BIGINT), 0) + 1 FROM articles
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'CATEGORIE_ARTICLE', COALESCE(MAX((regexp_match(code, '^CAT-(\d+)$'))[1]::BIGINT), 0) + 1 FROM categories_articles
ON CONFLICT (scope) DO NOTHING;

-- ── Year-scoped counters (documents) ─────────────────────────────────────
INSERT INTO code_sequences (scope, next_val)
SELECT 'CHANTIER:' || (regexp_match(code, '^CH-(\d{4})-\d+$'))[1],
       MAX((regexp_match(code, '^CH-\d{4}-(\d+)$'))[1]::BIGINT) + 1
FROM chantiers WHERE code ~ '^CH-\d{4}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'ACHAT:' || (regexp_match(ref, '^ACH-(\d{4})-\d+$'))[1],
       MAX((regexp_match(ref, '^ACH-\d{4}-(\d+)$'))[1]::BIGINT) + 1
FROM achats WHERE ref ~ '^ACH-\d{4}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'CONTRAT_SOUS_TRAITANT:' || (regexp_match(reference, '^CST-(\d{4})-\d+$'))[1],
       MAX((regexp_match(reference, '^CST-\d{4}-(\d+)$'))[1]::BIGINT) + 1
FROM contrats_sous_traitant WHERE reference ~ '^CST-\d{4}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'PAIEMENT_SOUS_TRAITANT:' || (regexp_match(reference, '^PAI-(\d{4})-\d+$'))[1],
       MAX((regexp_match(reference, '^PAI-\d{4}-(\d+)$'))[1]::BIGINT) + 1
FROM paiements_sous_traitant WHERE reference ~ '^PAI-\d{4}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

INSERT INTO code_sequences (scope, next_val)
SELECT 'ATTACHEMENT:' || (regexp_match(reference, '^ATT-(\d{4})-\d+$'))[1],
       MAX((regexp_match(reference, '^ATT-\d{4}-(\d+)$'))[1]::BIGINT) + 1
FROM attachements WHERE reference ~ '^ATT-\d{4}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

-- ── Period-scoped counter (payroll) ──────────────────────────────────────
INSERT INTO code_sequences (scope, next_val)
SELECT 'FICHE_PAIE:' || (regexp_match(reference, '^FDP-(\d{4}-\d{2})-\d+$'))[1],
       MAX((regexp_match(reference, '^FDP-\d{4}-\d{2}-(\d+)$'))[1]::BIGINT) + 1
FROM fiches_paie WHERE reference ~ '^FDP-\d{4}-\d{2}-\d+$' GROUP BY 1
ON CONFLICT (scope) DO NOTHING;

-- Note: bpu_lignes.ref has no counter on purpose. Those refs (1.1, 1.1.a, …)
-- are transcribed from the client's tender document and are how a line is
-- reconciled against it, so they stay hand-entered.

--rollback DELETE FROM code_sequences;
