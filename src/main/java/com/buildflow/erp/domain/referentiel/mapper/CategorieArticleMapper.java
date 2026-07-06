package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategorieArticleMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategorieArticleResponse toResponse(CategorieArticle categorieArticle);
}