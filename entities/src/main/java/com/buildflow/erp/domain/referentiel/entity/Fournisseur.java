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
@Table(name = "fournisseurs")
@Getter
@Setter
@NoArgsConstructor
public class Fournisseur extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "raison_sociale", nullable = false)
    private String raisonSociale;

    @Column(length = 50)
    private String ice;

    @Column
    private String contact;

    @Column(length = 20)
    private String telephone;

    @Column
    private String email;

    @Column(length = 100)
    private String ville;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    @Column(length = 50)
    private String rib;

    @Column(length = 100)
    private String banque;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FournisseurStatut statut = FournisseurStatut.ACTIF;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categorie_articles", columnDefinition = "text[]")
    private List<String> categorieArticles = new ArrayList<>();

    @Column(name = "total_achats_annee", precision = 15, scale = 2)
    private BigDecimal totalAchatsAnnee = BigDecimal.ZERO;

    @Column(name = "solde_impaye", precision = 15, scale = 2)
    private BigDecimal soldeImpaye = BigDecimal.ZERO;
}