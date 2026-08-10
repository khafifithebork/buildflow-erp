package com.buildflow.erp.domain.tresorerie.service;

import com.buildflow.erp.common.dto.UpdateIndicateursRequest;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateCaisseRequest;
import com.buildflow.erp.domain.tresorerie.dto.request.CreateTransactionRequest;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseResponse;
import com.buildflow.erp.domain.tresorerie.dto.response.CaisseTransactionResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TresorerieService {

    CaisseResponse createCaisse(CreateCaisseRequest request);

    CaisseResponse findById(UUID id);

    List<CaisseResponse> findAll();

    CaisseTransactionResponse enregistrerTransaction(UUID caisseId, CreateTransactionRequest request);

    List<CaisseTransactionResponse> getTransactions(UUID caisseId);

    /** Toggles the two operational billing indicators on an existing cash operation. */
    CaisseTransactionResponse updateTransactionIndicateurs(
            UUID caisseId, UUID transactionId, UpdateIndicateursRequest request);

    /**
     * Cross-domain method: called by AchatServiceImpl when an Achat transitions to PAYE.
     * Debits the caisse associated with the achat's chantier.
     *
     * <p>The generated debit IS the cash side of that achat, so it inherits the
     * achat's two billing indicators rather than defaulting them to false.
     */
    void debiterPourAchat(UUID chantierId, BigDecimal montant, String achatRef,
                          boolean impactAnalytiqueChantier, boolean impactComptableFiscal);

    /**
     * Corrects the caisse after a settled achat's amount changed.
     *
     * <p>A positive delta means the order now costs more and the caisse owes
     * the difference; a negative delta refunds it. Without this, re-pricing a
     * paid order leaves the ledger showing the amount that was actually paid
     * while every derived figure reads the new one.
     */
    void ajusterPourAchat(UUID chantierId, BigDecimal delta, String achatRef,
                          boolean impactAnalytiqueChantier, boolean impactComptableFiscal);

    /**
     * Cross-domain method: called by SalaireServiceImpl when a FichePaie is paid
     * with modePaiement=CAISSE. Debits the caisse associated with the fiche's chantier.
     */
    void debiterPourSalaire(UUID chantierId, BigDecimal montant, String reference);
}
