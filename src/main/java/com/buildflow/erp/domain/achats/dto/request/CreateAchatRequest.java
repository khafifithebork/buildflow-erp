package com.buildflow.erp.domain.achats.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateAchatRequest(
        @NotBlank String ref,
        @NotNull UUID fournisseurId,
        @NotNull UUID chantierId,
        @NotNull LocalDate dateCommande,
        @NotNull LocalDate dateLivraisonPrevue,
        @NotEmpty @Valid List<CreateLigneAchatRequest> lignes
) {}