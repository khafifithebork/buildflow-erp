package com.buildflow.erp.domain.referentiel.mapper;

import com.buildflow.erp.domain.referentiel.dto.request.CreateArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ArticleResponse;
import com.buildflow.erp.domain.referentiel.entity.Article;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(target = "categorieId", source = "categorie.id")
    @Mapping(target = "categorieLibelle", source = "categorie.libelle")
    ArticleResponse toResponse(Article article);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "categorie", ignore = true)
    @Mapping(target = "actif", constant = "true")
    Article toEntity(CreateArticleRequest request);
}