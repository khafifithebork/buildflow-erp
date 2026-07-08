package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.domain.referentiel.dto.request.CreateEmployeRequest;
import com.buildflow.erp.domain.referentiel.dto.response.EmployeResponse;
import java.util.List;
import java.util.UUID;

public interface EmployeService {
    EmployeResponse create(CreateEmployeRequest request);
    EmployeResponse findById(UUID id);
    List<EmployeResponse> findAll();
}