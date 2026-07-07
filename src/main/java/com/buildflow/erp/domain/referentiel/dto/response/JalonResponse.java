package com.buildflow.erp.domain.referentiel.dto.response;
import com.buildflow.erp.domain.referentiel.entity.JalonStatut;
import java.time.LocalDate;
import java.util.UUID;

public record JalonResponse(
        UUID id,
        String libelle,
        LocalDate datePrevue,
        LocalDate dateReelle,
        JalonStatut statut
) {}