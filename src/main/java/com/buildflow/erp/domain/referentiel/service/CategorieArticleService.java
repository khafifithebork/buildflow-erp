package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.referentiel.dto.request.CreateCategorieArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface CategorieArticleService {
    CategorieArticleResponse create(CreateCategorieArticleRequest request);
    CategorieArticleResponse findById(UUID id);
    PageResponse<CategorieArticleResponse> findAll(Pageable pageable);
}