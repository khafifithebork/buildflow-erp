package com.buildflow.erp.domain.stock.mapper;

import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import com.buildflow.erp.domain.stock.entity.StockArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMapper {

    @Mapping(target = "articleId", source = "article.id")
    @Mapping(target = "articleCode", source = "article.code")
    @Mapping(target = "designation", source = "article.designation")
    @Mapping(target = "unite", source = "article.unite")
    // chantier is null for stock held in the central dépôt; MapStruct leaves
    // chantierId null on its own, and the two expressions below spell out what
    // that means rather than showing an empty cell.
    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom",
            expression = "java(stockArticle.getChantier() == null ? \"Dépôt central\" : stockArticle.getChantier().getNom())")
    @Mapping(target = "emplacement",
            expression = "java(stockArticle.getChantier() == null ? \"DEPOT\" : \"CHANTIER\")")
    @Mapping(target = "enAlerte", expression = "java(stockArticle.getQuantiteTheorique().compareTo(stockArticle.getSeuilAlerte()) <= 0 && stockArticle.getSeuilAlerte().compareTo(java.math.BigDecimal.ZERO) > 0)")
    StockArticleResponse toResponse(StockArticle stockArticle);
}