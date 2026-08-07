package com.buildflow.erp.domain.referentiel.dto.request;

import com.buildflow.erp.domain.referentiel.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEmployeRequest(
        @NotBlank String nom,
        @NotBlank String prenom,
        @NotNull EmployeRole role,
        @NotBlank String poste,
        @NotBlank String departement,
        String telephone,
        @Email String email,
        @NotNull LocalDate dateEmbauche,
        UUID chantierActuelId,
        EmployeStatut statut,
        @NotNull @DecimalMin("0.0") BigDecimal salaireBrut,
        @NotNull TypeContrat typeContrat
) {}