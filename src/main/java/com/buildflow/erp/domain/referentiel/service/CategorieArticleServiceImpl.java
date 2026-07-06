package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateCategorieArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.CategorieArticleResponse;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.mapper.CategorieArticleMapper;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategorieArticleServiceImpl implements CategorieArticleService {

    private final CategorieArticleRepository categorieArticleRepository;
    private final CategorieArticleMapper categorieArticleMapper;

    @Override
    @Transactional
    public CategorieArticleResponse create(CreateCategorieArticleRequest request) {
        if (categorieArticleRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("A category with code '" + request.code() + "' already exists");
        }

        CategorieArticle categorie = categorieArticleMapper.toEntity(request);

        if (request.parentId() != null) {
            CategorieArticle parent = categorieArticleRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("CategorieArticle", request.parentId()));
            categorie.setParent(parent);
        }

        CategorieArticle saved = categorieArticleRepository.save(categorie);
        return categorieArticleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CategorieArticleResponse findById(UUID id) {
        CategorieArticle categorie = categorieArticleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CategorieArticle", id));
        return categorieArticleMapper.toResponse(categorie);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorieArticleResponse> findAll() {
        return categorieArticleRepository.findAll().stream()
                .map(categorieArticleMapper::toResponse)
                .toList();
    }
}