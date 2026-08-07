package com.buildflow.erp.domain.referentiel.dto.request;

import com.buildflow.erp.domain.referentiel.entity.SousTraitantStatut;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateSousTraitantRequest(
        @NotBlank String raisonSociale,
        @NotBlank String ice,
        @NotBlank String specialite,
        String contact,
        String telephone,
        @Email String email,
        String ville,
        String adresse,
        SousTraitantStatut statut
) {}