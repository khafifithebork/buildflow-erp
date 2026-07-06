package com.buildflow.erp.domain.referentiel.dto.request;

import com.buildflow.erp.domain.referentiel.entity.FournisseurStatut;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateFournisseurRequest(
        @NotBlank String code,
        @NotBlank String raisonSociale,
        String ice,
        String contact,
        String telephone,
        @Email String email,
        String ville,
        String adresse,
        String rib,
        String banque,
        FournisseurStatut statut,
        List<String> categorieArticles
) {}