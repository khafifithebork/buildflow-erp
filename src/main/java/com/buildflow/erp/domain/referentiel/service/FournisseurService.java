package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import java.util.List;
import java.util.UUID;

public interface FournisseurService {
    FournisseurResponse create(CreateFournisseurRequest request);
    FournisseurResponse findById(UUID id);
    List<FournisseurResponse> findAll();
}