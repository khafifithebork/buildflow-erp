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

    // No Dépôts/En Travaux split yet — StockArticle isn't scoped beyond a
    // single chantier quantity (see doc gap 2.7), so this is one global total.
    //
    // Returns Double, not BigDecimal: prixAchatRef is DOUBLE PRECISION, so the
    // product and its SUM come back as a float from the database. Callers round
    // it to two decimals before presenting it as money.
    @Query("SELECT COALESCE(SUM(s.quantiteTheorique * s.article.prixAchatRef), 0) FROM StockArticle s")
    Double sumValeurStockHt();
}