package com.buildflow.erp.common.paiement;

/**
 * Which kind of document a payment-mode change refers to.
 *
 * <p>The audit trail spans three unrelated tables, so it identifies its subject
 * by (type, id) rather than by a foreign key.
 */
public enum TypeDocumentPaiement {

    /** A purchase order — {@code achats}. */
    ACHAT,

    /** A payslip — {@code fiches_paie}. */
    FICHE_PAIE,

    /** A subcontractor payment — {@code paiements_sous_traitant}. */
    PAIEMENT_SOUS_TRAITANT
}
