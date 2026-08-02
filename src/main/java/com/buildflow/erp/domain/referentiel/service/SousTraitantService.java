package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.domain.referentiel.dto.request.CreateSousTraitantRequest;
import com.buildflow.erp.domain.referentiel.dto.response.SousTraitantResponse;
import java.util.List;
import java.util.UUID;

public interface SousTraitantService {
    SousTraitantResponse create(CreateSousTraitantRequest request);
    SousTraitantResponse update(UUID id, CreateSousTraitantRequest request);
    void delete(UUID id);
    SousTraitantResponse findById(UUID id);
    List<SousTraitantResponse> findAll();
}