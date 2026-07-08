package com.buildflow.erp.domain.referentiel.dto.response;

import com.buildflow.erp.domain.referentiel.entity.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmployeResponse(
        UUID id,
        String matricule,
        String nom,
        String prenom,
        EmployeRole role,
        String poste,
        String departement,
        String telephone,
        String email,
        LocalDate dateEmbauche,
        UUID chantierActuelId,
        String chantierActuelNom,
        EmployeStatut statut,
        BigDecimal salaireBrut,
        TypeContrat typeContrat
) {}