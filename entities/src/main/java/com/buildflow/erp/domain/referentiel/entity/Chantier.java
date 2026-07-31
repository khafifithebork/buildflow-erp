package com.buildflow.erp.domain.referentiel.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chantiers")
@Getter
@Setter
@NoArgsConstructor
public class Chantier extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String client;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    @Column(length = 100)
    private String ville;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ChantierStatut statut = ChantierStatut.EN_PREPARATION;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "budget_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal budgetHt = BigDecimal.ZERO;

    @Column(name = "depenses_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal depensesHt = BigDecimal.ZERO;

    @Column(nullable = false)
    private int avancement = 0;

    @Column(name = "chef_projet_nom")
    private String chefProjetNom;

    @Column(name = "nombre_ouvriers", nullable = false)
    private int nombreOuvriers = 0;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "soustraitants_actifs", columnDefinition = "text[]")
    private List<String> soustraitantsActifs = new ArrayList<>();

    // One-to-Many relationship with Cascade for nested creation
    @OneToMany(mappedBy = "chantier", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Jalon> jalons = new ArrayList<>();
}