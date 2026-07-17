package com.buildflow.erp.domain.achats.entity;

import com.buildflow.erp.common.entity.BaseEntity;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "achats")
@Getter
@Setter
@NoArgsConstructor
public class Achat extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String ref;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chantier_id", nullable = false)
    private Chantier chantier;

    @Column(name = "date_commande", nullable = false)
    private LocalDate dateCommande;

    @Column(name = "date_livraison_prevue", nullable = false)
    private LocalDate dateLivraisonPrevue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AchatStatut statut = AchatStatut.EN_COURS;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal ht = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal ttc = BigDecimal.ZERO;

    @Column(name = "bon_livraison_ref", length = 50)
    private String bonLivraisonRef;

    @Column(name = "facture_ref", length = 50)
    private String factureRef;

    @OneToMany(mappedBy = "achat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneAchat> lignes = new ArrayList<>();
}