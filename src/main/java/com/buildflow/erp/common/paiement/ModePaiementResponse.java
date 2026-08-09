package com.buildflow.erp.common.paiement;

import java.util.UUID;

/** Outcome of a payment-mode change. */
public record ModePaiementResponse(
        TypeDocumentPaiement typeDocument,
        UUID documentId,
        String documentRef,
        ModePaiement ancienMode,
        ModePaiement nouveauMode,
        /** Set when the change leaves the caisse out of step; null otherwise. */
        String avertissement
) {}
