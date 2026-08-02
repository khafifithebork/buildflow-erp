package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ArticleResponse;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ArticleService {
    ArticleResponse create(CreateArticleRequest request);
    ArticleResponse update(UUID id, CreateArticleRequest request);
    void delete(UUID id);
    ArticleResponse findById(UUID id);
    PageResponse<ArticleResponse> findAll(Pageable pageable);
}