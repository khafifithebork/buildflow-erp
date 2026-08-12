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

    /**
     * Cancels a cash movement that should not have happened — a mistyped
     * amount, a duplicate, a payment that never cleared.
     *
     * <p>The balance is put back, but the original movement is not deleted: a
     * reversing entry is posted against it instead, so the ledger still shows
     * what was recorded and what corrected it. Cancelling twice is refused.
     */
    CaisseTransactionResponse annulerTransaction(UUID caisseId, UUID transactionId, String motif);

    /** Toggles the two operational billing indicators on an existing cash operation. */
    CaisseTransactionResponse updateTransactionIndicateurs(
            UUID caisseId, UUID transactionId, UpdateIndicateursRequest request);

    /**
     * Débite la caisse d'un chantier pour régler un document — une commande,
     * un paiement de sous-traitant.
     *
     * <p>L'écriture produite EST la face trésorerie de ce document : elle en
     * hérite les deux indicateurs, et elle en porte l'identifiant, ce qui la
     * rend dérivée. Une écriture dérivée ne s'annule pas seule, seulement avec
     * son document.
     *
     * <p>S'appelait {@code debiterPourAchat}, ce que la sous-traitance
     * appelait déjà pour ses propres règlements — le nom mentait sur ce que la
     * méthode faisait, et le typage du document rendait l'ambiguïté nuisible.
     */
    void debiterPourDocument(UUID chantierId, com.buildflow.erp.common.paiement.TypeDocumentPaiement typeDocument,
                             UUID documentId, BigDecimal montant, String reference,
                             boolean impactAnalytiqueChantier, boolean impactComptableFiscal);

    /**
     * Corrects the caisse after a settled achat's amount changed.
     *
     * <p>A positive delta means the order now costs more and the caisse owes
     * the difference; a negative delta refunds it. Without this, re-pricing a
     * paid order leaves the ledger showing the amount that was actually paid
     * while every derived figure reads the new one.
     */
    void ajusterPourAchat(UUID chantierId, UUID achatId, BigDecimal delta, String achatRef,
                          boolean impactAnalytiqueChantier, boolean impactComptableFiscal);

    /**
     * Cross-domain method: called by SalaireServiceImpl when a FichePaie is paid
     * with modePaiement=CAISSE. Debits the caisse associated with the fiche's chantier.
     */
    void debiterPourSalaire(UUID chantierId, BigDecimal montant, String reference);

    /**
     * Contre-passe toutes les écritures encore vivantes d'un document — son
     * règlement et les ajustements qui ont suivi.
     *
     * <p>Appelé par le domaine propriétaire du document, jamais par la caisse
     * elle-même : c'est le document qui décide qu'il n'est plus payé, la caisse
     * ne fait qu'en tirer les conséquences.
     *
     * @return le montant net rendu à la caisse
     */
    BigDecimal annulerEcrituresDuDocument(UUID documentId, String motif);
}
