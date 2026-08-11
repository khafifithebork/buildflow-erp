package com.buildflow.erp.domain.stock.repository;

import com.buildflow.erp.domain.stock.entity.StockArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
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
    // Both return Double because prixAchatRef is DOUBLE PRECISION.

    /** Still available anywhere — the dashboard's "Dépôts". */
    @Query("""
            SELECT COALESCE(SUM(s.quantiteTheorique * s.article.prixAchatRef), 0) FROM StockArticle s
            """)
    Double sumValeurStockDispoHt();

    /** Incorporated into the works — the dashboard's "En Travaux". */
    @Query("""
            SELECT COALESCE(SUM(s.quantiteTravaux * s.article.prixAchatRef), 0) FROM StockArticle s
            """)
    Double sumValeurStockTravauxHt();

    // ── By location, kept separate from the availability split ───────────

    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.article.prixAchatRef), 0)
            FROM StockArticle s WHERE s.chantier IS NULL
            """)
    Double sumValeurStockAuDepotHt();

    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.article.prixAchatRef), 0)
            FROM StockArticle s WHERE s.chantier IS NOT NULL
            """)
    Double sumValeurStockSurChantiersHt();

    // No Dépôts/En Travaux split yet — StockArticle isn't scoped beyond a
    // single chantier quantity (see doc gap 2.7), so this is one global total.
    //
    // Returns Double, not BigDecimal: prixAchatRef is DOUBLE PRECISION, so the
    // product and its SUM come back as a float from the database. Callers round
    // it to two decimals before presenting it as money.
    // Total stock value: available plus posé. Affecting to the works moves
    // quantity between the two, so this total is unchanged by it — only the
    // split moves.
    @Query("""
            SELECT COALESCE(SUM((s.quantiteTheorique + s.quantiteTravaux) * s.article.prixAchatRef), 0)
            FROM StockArticle s
            """)
    Double sumValeurStockHt();
}