package com.buildflow.erp.domain.achats.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantite;

    @Column(name = "prix_unitaire", nullable = false, precision = 15, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;
}