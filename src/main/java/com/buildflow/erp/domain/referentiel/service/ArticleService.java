package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.domain.referentiel.dto.request.CreateArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ArticleResponse;

import java.util.List;
import java.util.UUID;

public interface ArticleService {
    ArticleResponse create(CreateArticleRequest request);
    ArticleResponse findById(UUID id);
    List<ArticleResponse> findAll();
}