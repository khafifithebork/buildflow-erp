package com.buildflow.erp.domain.bpu.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateBpuLigneRequest(
        // Deliberately NOT auto-generated. A BPU ref is transcribed from the
        // client's tender document (1.1, 1.1.a, …) and is how a line is
        // reconciled against it — see BpuExcelParser, which exists to preserve
        // exactly these refs on import.
        @NotBlank String ref,
        @NotBlank String designation,
        @NotBlank String unite,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal qtePrevue,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal puHt
) {}
