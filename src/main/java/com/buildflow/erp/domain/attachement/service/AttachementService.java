package com.buildflow.erp.domain.attachement.service;

import com.buildflow.erp.domain.attachement.dto.request.CreateAttachementRequest;
import com.buildflow.erp.domain.attachement.dto.response.AttachementResponse;

import java.util.List;
import java.util.UUID;

public interface AttachementService {

    AttachementResponse create(UUID chantierId, CreateAttachementRequest request);

    AttachementResponse encaisser(UUID id);

    List<AttachementResponse> findByChantier(UUID chantierId);
}
