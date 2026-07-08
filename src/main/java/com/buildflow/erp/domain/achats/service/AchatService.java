package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import java.util.List;
import java.util.UUID;

public interface AchatService {
    AchatResponse create(CreateAchatRequest request);
    AchatResponse findById(UUID id);
    List<AchatResponse> findAll();
    AchatResponse validateBL(UUID id, String bonLivraisonRef);
    AchatResponse validateFacture(UUID id, String factureRef);
    AchatResponse validatePaiement(UUID id);
}