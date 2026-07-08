package com.buildflow.erp.domain.stock.repository;

import com.buildflow.erp.domain.stock.entity.StockArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StockArticleRepository extends JpaRepository<StockArticle, UUID> {
    Optional<StockArticle> findByArticleIdAndChantierId(UUID articleId, UUID chantierId);
    Page<StockArticle> findByChantierId(UUID chantierId, Pageable pageable);
}