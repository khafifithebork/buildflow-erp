package com.buildflow.erp.domain.attachement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateAttachementRequest(
        @NotNull LocalDate dateAttachement,
        @NotEmpty @Valid List<CreateAttachementLigneRequest> lignes
) {}
