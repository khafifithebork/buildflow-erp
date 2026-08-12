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
     * Corrects the price of a delivery already received — an order re-priced
     * after the goods arrived.
     *
     * <p>Only the units still held can be corrected, and there are never more
     * of them than the delivery brought. Material already consumed left at the
     * old price: that cost is spent and cannot be put back into stock. So the
     * value moves by {@code min(livrée, détenue) × Δ}, not by the whole
     * {@code livrée × Δ} — applying the full difference to a part-consumed line
     * inflates what is left. Ten units received and five consumed, re-priced
     * from 100 to 150, are worth 250 more, not 500.
     *
     * <p>Lots are not tracked, so which units are "the ones still held" is a
     * choice; taking as many as the line still carries is the reading that
     * keeps the total right in every case tested.
     *
     * @return how much of the delivery the correction reached — zero when none
     *         of it is held any more, less than {@code quantiteLivree} when
     *         only part is
     */
    public BigDecimal corrigerValeur(BigDecimal quantiteLivree, double deltaPrixUnitaire) {
        BigDecimal detenu = quantiteTotale();
        if (detenu.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal corrigee = detenu.min(quantiteLivree);
        coutUnitaire += corrigee.doubleValue() * deltaPrixUnitaire / detenu.doubleValue();
        // A floor, not a rounding: reachable only on a line whose average
        // predates this column, since anything priced through integrerArrivage
        // carries at least what this delivery contributed.
        if (coutUnitaire < 0) {
            coutUnitaire = 0d;
        }
        return corrigee;
    }
}