package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateCategorieArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategorieArticleMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategorieArticleResponse toResponse(CategorieArticle categorieArticle);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "parent", ignore = true)
    CategorieArticle toEntity(CreateCategorieArticleRequest request);
}