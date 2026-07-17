package com.buildflow.erp.domain.referentiel.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employes")
@Getter
@Setter
@NoArgsConstructor
public class Employe extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String matricule;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 100)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EmployeRole role;

    @Column(nullable = false)
    private String poste;

    @Column(nullable = false, length = 100)
    private String departement;

    @Column(length = 20)
    private String telephone;

    @Column
    private String email;

    @Column(name = "date_embauche", nullable = false)
    private LocalDate dateEmbauche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chantier_actuel_id")
    private Chantier chantierActuel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeStatut statut = EmployeStatut.ACTIF;

    @Column(name = "salaire_brut", nullable = false, precision = 15, scale = 2)
    private BigDecimal salaireBrut = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_contrat", nullable = false, length = 20)
    private TypeContrat typeContrat;
}