package com.buildflow.erp.domain.referentiel.dto.request;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateChantierRequest(
        @NotBlank String code,
        @NotBlank String nom,
        @NotBlank String client,
        String adresse,
        String ville,
        ChantierStatut statut,
        @NotNull LocalDate dateDebut,
        @NotNull LocalDate dateFin,
        @NotNull BigDecimal budgetHt,
        String chefProjetNom,
        List<String> soustraitantsActifs,
        List<CreateJalonRequest> jalons
) {}