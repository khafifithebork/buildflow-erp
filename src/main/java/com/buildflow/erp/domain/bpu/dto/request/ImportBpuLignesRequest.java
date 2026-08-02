package com.buildflow.erp.domain.bpu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImportBpuLignesRequest(
        @NotEmpty @Valid List<CreateBpuLigneRequest> lignes
) {}
