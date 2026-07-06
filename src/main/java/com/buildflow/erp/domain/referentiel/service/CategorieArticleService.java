package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.domain.referentiel.dto.request.CreateCategorieArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;

import java.util.List;
import java.util.UUID;

public interface CategorieArticleService {
    CategorieArticleResponse create(CreateCategorieArticleRequest request);
    CategorieArticleResponse findById(UUID id);
    List<CategorieArticleResponse> findAll();
}