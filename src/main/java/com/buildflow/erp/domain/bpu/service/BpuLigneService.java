package com.buildflow.erp.domain.bpu.service;

import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import com.buildflow.erp.domain.bpu.dto.request.ImportBpuLignesRequest;
import com.buildflow.erp.domain.bpu.dto.response.BpuLigneResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BpuLigneService {

    BpuLigneResponse create(UUID chantierId, CreateBpuLigneRequest request);

    BpuLigneResponse update(UUID chantierId, UUID id, CreateBpuLigneRequest request);

    void delete(UUID chantierId, UUID id);

    List<BpuLigneResponse> batchReplace(UUID chantierId, ImportBpuLignesRequest request);

    List<BpuLigneResponse> importExcel(UUID chantierId, MultipartFile file);

    List<BpuLigneResponse> findByChantier(UUID chantierId);
}
