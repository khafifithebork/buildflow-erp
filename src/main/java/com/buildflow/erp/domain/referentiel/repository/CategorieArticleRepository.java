package com.buildflow.erp.domain.referentiel.repository;

import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategorieArticleRepository extends JpaRepository<CategorieArticle, UUID> {
    Optional<CategorieArticle> findByCode(String code);
}