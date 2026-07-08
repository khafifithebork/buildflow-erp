package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateSousTraitantRequest;
import com.buildflow.erp.domain.referentiel.dto.response.SousTraitantResponse;
import com.buildflow.erp.domain.referentiel.entity.SousTraitant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SousTraitantMapper {
    SousTraitantResponse toResponse(SousTraitant sousTraitant);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "nombreContratsActifs", ignore = true)
    @Mapping(target = "montantTotalPaye", ignore = true)
    SousTraitant toEntity(CreateSousTraitantRequest request);
}