package com.buildflow.erp.domain.achats.mapper;

import com.buildflow.erp.domain.achats.dto.response.LigneAchatResponse;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LigneAchatMapper {

    @Mapping(target = "articleCode", source = "article.code")
    @Mapping(target = "bpuLigneRef", source = "bpuLigne.ref")
    LigneAchatResponse toResponse(LigneAchat ligne);
}