package com.buildflow.erp.domain.salaires.dto.request;

import com.buildflow.erp.domain.salaires.entity.ModePaiement;
import jakarta.validation.constraints.NotNull;

public record PayerFichePaieRequest(
        @NotNull ModePaiement modePaiement
) {}
