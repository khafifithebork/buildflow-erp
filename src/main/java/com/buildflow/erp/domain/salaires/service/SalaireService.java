package com.buildflow.erp.domain.salaires.service;

import com.buildflow.erp.domain.salaires.dto.request.CreateFichePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.FichePaieResponse;

import java.util.List;
import java.util.UUID;

public interface SalaireService {

    FichePaieResponse create(CreateFichePaieRequest request);

    FichePaieResponse findById(UUID id);

    List<FichePaieResponse> findAll();

    List<FichePaieResponse> findByPeriode(String periode);

    /** HR/Manager validates: BROUILLON → VALIDEE */
    FichePaieResponse valider(UUID id);

    /** Finance pays: VALIDEE → PAYEE (triggers caisse debit) */
    FichePaieResponse payer(UUID id);
}
