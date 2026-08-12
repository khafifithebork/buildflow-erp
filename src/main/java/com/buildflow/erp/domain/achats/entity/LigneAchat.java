package com.buildflow.erp.domain.achats.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.buildflow.erp.common.fiscal.Tva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "lignes_achat")
@Getter
@Setter
@NoArgsConstructor
public class LigneAchat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achat_id", nullable = false)
    @JsonIgnore
    private Achat achat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false)
    private String designation; // Snapshot

    @Column(nullable = false, length = 20)
    private String unite; // Snapshot

    // DOUBLE PRECISION, same as prixUnitaire below: both sides of the line
    // total hold the same kind of value. The total stays BigDecimal.
    @Column(nullable = false)
    private double quantite;

    // DOUBLE PRECISION: a unit price may carry more than two decimals. The
    // line total below stays BigDecimal — that is the invoiced figure.
    @Column(name = "prix_unitaire", nullable = false)
    private double prixUnitaire;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /**
     * Le taux de TVA de l'article, figé au moment de la commande — instantané
     * au même titre que la désignation et l'unité.
     *
     * <p>Figé et non relu chez l'article : une commande passée à 14 % reste à
     * 14 % même si le référentiel change ensuite. Le taux qui a servi à
     * facturer ne se réécrit pas rétroactivement.
     */
    @Column(name = "tva_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal tvaRate = Tva.TAUX_PAR_DEFAUT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpu_ligne_id")
    private BpuLigne bpuLigne;
}