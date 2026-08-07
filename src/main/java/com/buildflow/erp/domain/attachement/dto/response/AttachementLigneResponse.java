package com.buildflow.erp.domain.attachement.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AttachementLigneResponse(
        UUID id,
        UUID bpuLigneId,
        String bpuLigneRef,
        String bpuLigneDesignation,
        BigDecimal ancienCumul,
        BigDecimal nouveauCumul,
        double puHt,
        BigDecimal montantHt
) {}
