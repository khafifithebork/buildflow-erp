package com.buildflow.erp.domain.referentiel.repository;

import com.buildflow.erp.domain.referentiel.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Optional<Article> findByCode(String code);
    boolean existsByCode(String code);
}