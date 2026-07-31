package com.buildflow.erp.domain.stock.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_articles")
@Getter
@Setter
@NoArgsConstructor
public class StockArticle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(name = "quantite_theorique", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantiteTheorique = BigDecimal.ZERO;

    @Column(name = "seuil_alerte", nullable = false, precision = 15, scale = 3)
    private BigDecimal seuilAlerte = BigDecimal.ZERO;
}