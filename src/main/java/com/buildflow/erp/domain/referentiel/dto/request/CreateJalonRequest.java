package com.buildflow.erp.domain.referentiel.dto.request;
import com.buildflow.erp.domain.referentiel.entity.JalonStatut;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateJalonRequest(
        @NotBlank String libelle,
        @NotNull LocalDate datePrevue,
        LocalDate dateReelle,
        JalonStatut statut
) {}