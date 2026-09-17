package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface FournisseurMapper {
    FournisseurResponse toResponse(Fournisseur fournisseur);

    /**
     * Même chose, mais la dette vient du calcul plutôt que de la colonne.
     *
     * <p>{@code fournisseurs.solde_impaye} et {@code total_achats_annee}
     * existent depuis la migration 004 et n'ont jamais eu d'écrivain : la page
     * Fournisseurs et l'export Excel affichaient donc zéro. Les deux se
     * dérivent des achats, ils n'ont pas à être stockés.
     */
    @Mapping(target = "soldeImpaye", source = "soldeImpaye")
    @Mapping(target = "totalAchatsAnnee", source = "totalAchatsAnnee")
    FournisseurResponse toResponse(Fournisseur fournisseur,
                                   BigDecimal soldeImpaye,
                                   BigDecimal totalAchatsAnnee);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "totalAchatsAnnee", ignore = true)
    @Mapping(target = "soldeImpaye", ignore = true)
    Fournisseur toEntity(CreateFournisseurRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "totalAchatsAnnee", ignore = true)
    @Mapping(target = "soldeImpaye", ignore = true)
    void updateEntityFromRequest(CreateFournisseurRequest request, @MappingTarget Fournisseur fournisseur);
}