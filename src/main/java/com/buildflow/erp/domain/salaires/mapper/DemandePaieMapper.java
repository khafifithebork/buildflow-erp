package com.buildflow.erp.domain.salaires.mapper;

import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;
import com.buildflow.erp.domain.salaires.entity.DemandePaie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DemandePaieMapper {

    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    @Mapping(target = "bpuLigneId", source = "bpuLigne.id")
    @Mapping(target = "bpuLigneRef", source = "bpuLigne.ref")
    DemandePaieResponse toResponse(DemandePaie demandePaie);
}
