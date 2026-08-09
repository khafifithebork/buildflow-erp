package com.buildflow.erp.common.paiement;

/**
 * How a payment is settled. Shared by every document that can be paid:
 * {@code Achat}, {@code FichePaie} and {@code PaiementSousTraitant}.
 *
 * <p>Only {@link #CAISSE} moves the chantier's cash balance. The other three
 * settle outside the caisse, so they must not debit it.
 */
public enum ModePaiement {

    /** Bank transfer. */
    VIREMENT,

    /** Cheque. */
    CHEQUE,

    /** Bill of exchange / lettre de change ("effet de commerce"). */
    EFFET,

    /**
     * Cash, out of the chantier's caisse — the only mode with a treasury side
     * effect. Kept selectable so cash payouts remain possible, but it is no
     * longer the default: the payer must now choose a mode explicitly.
     */
    CAISSE
}
