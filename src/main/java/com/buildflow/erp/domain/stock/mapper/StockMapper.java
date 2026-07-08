package com.buildflow.erp.domain.stock.mapper;

import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import com.buildflow.erp.domain.stock.entity.StockArticle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMapper {

    @Mapping(target = "articleCode", source = "article.code")
    @Mapping(target = "designation", source = "article.designation")
    @Mapping(target = "unite", source = "article.unite")
    @Mapping(target = "chantierId", source = "chantier.id")
    @Mapping(target = "chantierNom", source = "chantier.nom")
    @Mapping(target = "enAlerte", expression = "java(stockArticle.getQuantiteTheorique().compareTo(stockArticle.getSeuilAlerte()) <= 0 && stockArticle.getSeuilAlerte().compareTo(java.math.BigDecimal.ZERO) > 0)")
    StockArticleResponse toResponse(StockArticle stockArticle);
}