package com.buildflow.erp.domain.salaires.service;

import com.buildflow.erp.domain.salaires.dto.request.CreateDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;

import java.util.List;
import java.util.UUID;

public interface DemandePaieService {

    DemandePaieResponse create(CreateDemandePaieRequest request);

    DemandePaieResponse findById(UUID id);

    List<DemandePaieResponse> findAll();

    List<DemandePaieResponse> findByPeriode(String periode);

    /** HR/Manager validates: SOUMISE → VALIDEE */
    DemandePaieResponse valider(UUID id);

    /** Finance pays: VALIDEE → PAYEE (debits the caisse only when modePaiement=CAISSE) */
    DemandePaieResponse payer(UUID id, PayerDemandePaieRequest request);

    /** Only while unpaid: a PAYEE demande is an accounting record, not a draft. */
    void delete(UUID id);
}
