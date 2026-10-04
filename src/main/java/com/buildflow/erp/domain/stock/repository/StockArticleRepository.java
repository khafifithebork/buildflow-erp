package com.buildflow.erp.domain.stock.repository;

import com.buildflow.erp.domain.stock.entity.StockArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockArticleRepository extends JpaRepository<StockArticle, UUID> {
    Optional<StockArticle> findByArticleIdAndChantierId(UUID articleId, UUID chantierId);
    Page<StockArticle> findByChantierId(UUID chantierId, Pageable pageable);
    long countByChantierId(UUID chantierId);

    /** The central dépôt line for an article — the one with no chantier. */
    Optional<StockArticle> findByArticleIdAndChantierIsNull(UUID articleId);

    /** Everything sitting in the central dépôt. */
    Page<StockArticle> findByChantierIsNull(Pageable pageable);

    // ── Dépôts vs En Travaux, for the dashboard split ────────────────────
    // The split is by availability, not by location: what is still in stock
    // versus what has been incorporated into the works. Location is a separate
    // axis, queried through findByChantierIsNull / findByChantierId.
    //
    // Both return Double because coutUnitaire is DOUBLE PRECISION.
    //
    // Every valuation below multiplies by the line's own coutUnitaire — what
    // the material actually cost — rather than the article's reference price,
    // which never moves and left re-priced orders valued at the old figure.

    /** Still available anywhere — the dashboard's "Dépôts". */
    @Query("""
            SELECT COALESCE(SUM(s.quantiteTheorique * s.coutUnitaire), 0) FROM StockArticle s
            """)
    Double sumValeurStockDispoHt();

    /** Incorporated into the works — the dashboard's "En Travaux". */
    @Query("""
            SELECT COALESCE(SUM(s.quantiteTravaux * s.coutUnitaire), 0) FROM StockArticle s
            """)
    Double sumValeurStockTravauxHt();

    // ── By location, kept separate from the availability split ───────────

    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.coutUnitaire), 0)
            FROM StockArticle s WHERE s.chantier IS NULL
            """)
    Double sumValeurStockAuDepotHt();

    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.coutUnitaire), 0)
            FROM StockArticle s WHERE s.chantier IS NOT NULL
            """)
    Double sumValeurStockSurChantiersHt();

    // No Dépôts/En Travaux split yet — StockArticle isn't scoped beyond a
    // single chantier quantity (see doc gap 2.7), so this is one global total.
    //
    // Returns Double, not BigDecimal: coutUnitaire is DOUBLE PRECISION, so the
    // product and its SUM come back as a float from the database. Callers round
    // it to two decimals before presenting it as money.
    // Total stock value: available plus posé. Affecting to the works moves
    // quantity between the two, so this total is unchanged by it — only the
    // split moves.
    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.coutUnitaire), 0)
            FROM StockArticle s
            """)
    Double sumValeurStockHt();

    /**
     * La part « effet chantier » de chaque ligne de stock, une valeur par
     * ligne, déjà pondérée — le total est leur somme.
     *
     * <p>Le stock est fongible : il se valorise en moyenne pondérée par ligne,
     * donc aucune unité détenue ne porte d'origine. Impossible de dire laquelle
     * des 20 unités vient de quelle commande. La convention retenue est donc un
     * <b>prorata en valeur des entrées</b> : la ligne contribue à hauteur de la
     * part de sa valeur d'entrée venue de commandes à effet chantier.
     *
     * <pre>
     *   part = valeur de la ligne × (entrées effet chantier ÷ entrées totales)
     * </pre>
     *
     * <p>En valeur et non en quantité, parce que deux fournisseurs ne vendent
     * pas le même article au même prix : 10 unités à 100 et 10 à 300 font une
     * ligne de 4 000 dont le quart relève du premier achat, pas la moitié.
     *
     * <p>Trois conséquences à connaître, chacune volontaire :
     *
     * <ul>
     *   <li>Une entrée sans ligne de commande — saisie à la main, ajustement —
     *       compte au dénominateur, jamais au numérateur. Elle dilue donc la
     *       part effet chantier au lieu de s'y ajouter. Son prix est inconnu,
     *       et {@code coutUnitaire} est le seul dont on dispose : c'est aussi
     *       celui auquel {@code createMouvement} l'avait intégrée.
     *   <li>Une ligne sans aucune entrée enregistrée — stock antérieur au
     *       journal — ne contribue pas. {@code NULLIF} la sort de la somme
     *       plutôt que de diviser par zéro.
     *   <li>Un transfert est enregistré en {@code TRANSFERT}, pas en
     *       {@code ENTREE} : l'origine ne traverse pas un déplacement entre
     *       dépôt et chantier. Le chemin des achats, lui, est couvert —
     *       {@code approvisionnerDepuisAchat} livre directement sur le chantier
     *       de la commande. Suivre l'origine à travers un transfert demanderait
     *       un suivi par lot, qui est un autre chantier.
     * </ul>
     *
     * <p>La somme ne se fait pas ici : JPQL n'agrège pas par-dessus un
     * {@code GROUP BY}. {@link com.buildflow.erp.domain.stock.service.StockService#valeurStockEffetChantierHt()}
     * la fait, et c'est là que vit la convention.
     *
     * <p>Rend des {@code Double} — {@code coutUnitaire} et {@code prixUnitaire}
     * sont en {@code DOUBLE PRECISION}, le produit revient en flottant. Un
     * élément nul est une ligne sans entrée valorisée, à ignorer.
     */
    @Query("""
            SELECT (s.quantiteTheorique + s.quantiteTravaux) * s.coutUnitaire
                   * SUM(CASE WHEN a.impactAnalytiqueChantier = true
                               AND a.impactComptableFiscal = false
                              THEN m.quantite * l.prixUnitaire
                              ELSE 0 END)
                   / NULLIF(SUM(m.quantite * COALESCE(l.prixUnitaire, s.coutUnitaire)), 0)
            FROM MouvementStock m
                 JOIN m.stockArticle s
                 LEFT JOIN m.ligneAchat l
                 LEFT JOIN l.achat a
            WHERE m.typeMouvement = com.buildflow.erp.domain.stock.entity.TypeMouvement.ENTREE
            GROUP BY s.id, s.quantiteTheorique, s.quantiteTravaux, s.coutUnitaire
            """)
    List<Double> valeursEffetChantierParLigne();
}