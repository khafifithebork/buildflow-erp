package com.buildflow.erp.domain.tresorerie.service;

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
     * Cross-domain method: called by AchatServiceImpl when an Achat transitions to PAYE.
     * Debits the caisse associated with the achat's chantier.
     */
    void debiterPourAchat(UUID chantierId, BigDecimal montant, String achatRef);

    /**
     * Cross-domain method: called by SalaireServiceImpl when a FichePaie is paid
     * with modePaiement=CAISSE. Debits the caisse associated with the fiche's chantier.
     */
    void debiterPourSalaire(UUID chantierId, BigDecimal montant, String reference);
}
