package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FournisseurMapper {
    FournisseurResponse toResponse(Fournisseur fournisseur);

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