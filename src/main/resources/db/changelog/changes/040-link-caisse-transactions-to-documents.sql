--liquibase formatted sql

-- ============================================================================
-- Une écriture de caisse qui règle un document sait désormais lequel.
--
-- Le lien n'existait que sous forme de texte libre (reference_document =
-- "ACH-2026-021"), qu'aucun code ne relisait. L'annulation d'une écriture
-- traitait donc le règlement d'une commande comme une opération autonome :
-- l'argent revenait en caisse, la commande restait PAYE. La dette ne
-- réapparaissait pas et le décaissement disparaissait — la commande devenait
-- gratuite au bilan.
--
-- Le couple (type_document, document_id) reprend la forme déjà utilisée par
-- mode_paiement_historique. Pas de clé étrangère : le lien est polymorphe, il
-- vaudra aussi pour les fiches de paie et les paiements de sous-traitance.
--
-- reference_document reste, et reste lisible même si le document disparaît.
-- ============================================================================

--changeset khafifi:040-add-document-link-to-caisse-transactions
ALTER TABLE caisse_transactions
    ADD COLUMN type_document VARCHAR(30),
    ADD COLUMN document_id UUID;

-- Les écritures déjà passées portent la référence de la commande en clair :
-- de quoi rattacher l'historique sans perte.
UPDATE caisse_transactions t
SET type_document = 'ACHAT',
    document_id = a.id
FROM achats a
WHERE a.ref = t.reference_document;

CREATE INDEX idx_caisse_transactions_document
    ON caisse_transactions (document_id)
    WHERE document_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS idx_caisse_transactions_document;
--rollback ALTER TABLE caisse_transactions DROP COLUMN document_id, DROP COLUMN type_document;

--changeset khafifi:040-allow-null-nouveau-mode-in-historique
-- L'historique des modes de paiement enregistre des transitions. Il admettait
-- déjà un ancien mode nul — « il n'y en avait pas avant ». L'annulation d'un
-- règlement produit la transition symétrique : « il n'y en a plus après ».
-- Sans cette symétrie, la seule façon de tracer une annulation serait de mentir
-- sur le mode qui la suit.
ALTER TABLE mode_paiement_historique
    ALTER COLUMN nouveau_mode DROP NOT NULL;

--rollback ALTER TABLE mode_paiement_historique ALTER COLUMN nouveau_mode SET NOT NULL;
