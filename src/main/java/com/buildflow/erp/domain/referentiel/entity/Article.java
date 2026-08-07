package com.buildflow.erp.domain.referentiel.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor
public class Article extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String designation;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieArticle categorie;

    @Column(nullable = false, length = 20)
    private String unite;

    // DOUBLE PRECISION: a reference rate may carry more than two decimals.
    @Column(name = "prix_achat_ref", nullable = false)
    private double prixAchatRef;

    @Column(name = "tva_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal tvaRate;

    @Column(nullable = false)
    private boolean actif = true;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "fournisseurs_preferentiels", columnDefinition = "text[]")
    private List<String> fournisseursPreferentiels = new ArrayList<>();
}