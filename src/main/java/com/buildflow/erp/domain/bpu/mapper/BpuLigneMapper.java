package com.buildflow.erp.domain.bpu.mapper;

import com.buildflow.erp.domain.bpu.dto.response.BpuLigneResponse;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BpuLigneMapper {

    @Mapping(target = "montantEngageHt", ignore = true)
    @Mapping(target = "tauxConsommation", ignore = true)
    @Mapping(target = "alerteDepassement", ignore = true)
    BpuLigneResponse toResponse(BpuLigne bpuLigne);
}
