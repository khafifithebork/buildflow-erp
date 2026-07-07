package com.buildflow.erp.domain.referentiel.service;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ChantierResponse;
import java.util.List;
import java.util.UUID;

public interface ChantierService {
    ChantierResponse create(CreateChantierRequest request);
    ChantierResponse findById(UUID id);
    List<ChantierResponse> findAll();
}