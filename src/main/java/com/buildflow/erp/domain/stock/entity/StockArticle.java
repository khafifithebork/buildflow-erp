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

    /**
     * Where this stock sits. Null means the central dépôt; a chantier means the
     * quantity is allocated to that site ("en travaux").
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chantier_id")
    private Chantier chantier;

    /** True when this line is held in the central dépôt rather than on a site. */
    public boolean estAuDepot() {
        return chantier == null;
    }

    @Column(name = "quantite_theorique", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantiteTheorique = BigDecimal.ZERO;

    /**
     * Quantity incorporated into the works at this location — "posé". Distinct
     * from where the stock sits: a chantier holds both material still waiting
     * ({@code quantiteTheorique}) and material already laid. Affecting to the
     * works moves quantity between the two without changing location.
     */
    @Column(name = "quantite_travaux", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantiteTravaux = BigDecimal.ZERO;

    @Column(name = "seuil_alerte", nullable = false, precision = 15, scale = 3)
    private BigDecimal seuilAlerte = BigDecimal.ZERO;

    /**
     * Weighted average of what the material on this line actually cost, and the
     * price every valuation reads. Receiving goods folds the purchase price into
     * the average; re-pricing an order already received corrects it.
     *
     * <p>Kept as a double to match {@code prix_achat_ref} — the reference price
     * it is seeded from, and the one it replaces in the valuation queries.
     */
    @Column(name = "cout_unitaire", nullable = false)
    private double coutUnitaire = 0d;

    /** Everything held on this line: still available plus already posé. */
    public BigDecimal quantiteTotale() {
        return quantiteTheorique.add(quantiteTravaux);
    }

    /**
     * Folds an arrival into the weighted average. A line receiving its first
     * goods simply takes the arrival price.
     */
    public void integrerArrivage(BigDecimal quantite, double prixUnitaire) {
        BigDecimal avant = quantiteTotale();
        BigDecimal apres = avant.add(quantite);
        if (apres.signum() <= 0) {
            coutUnitaire = prixUnitaire;
            return;
        }
        double valeur = avant.doubleValue() * coutUnitaire + quantite.doubleValue() * prixUnitaire;
        coutUnitaire = valeur / apres.doubleValue();
    }

    /**
     * Spreads a value correction over what is still held — used when an order
     * that has already been received is re-priced. With nothing left to
     * revalue the correction has nowhere to go, which the caller reports.
     *
     * @return true when the correction was applied
     */
    public boolean corrigerValeur(BigDecimal deltaValeur) {
        BigDecimal detenu = quantiteTotale();
        if (detenu.signum() <= 0) {
            return false;
        }
        coutUnitaire += deltaValeur.doubleValue() / detenu.doubleValue();
        if (coutUnitaire < 0) {
            coutUnitaire = 0d;
        }
        return true;
    }
}