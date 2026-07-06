package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateArticleRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ArticleResponse;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.CategorieArticle;
import com.buildflow.erp.domain.referentiel.mapper.ArticleMapper;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.CategorieArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieArticleRepository;
    private final ArticleMapper articleMapper;

    @Override
    @Transactional
    public ArticleResponse create(CreateArticleRequest request) {
        if (articleRepository.existsByCode(request.code())) {
            throw new ConflictException("An article with code '" + request.code() + "' already exists");
        }

        CategorieArticle categorie = categorieArticleRepository.findById(request.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("CategorieArticle", request.categorieId()));

        Article article = articleMapper.toEntity(request);
        article.setCategorie(categorie);

        Article saved = articleRepository.save(article);
        return articleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse findById(UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        return articleMapper.toResponse(article);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleResponse> findAll() {
        return articleRepository.findAll().stream()
                .map(articleMapper::toResponse)
                .toList();
    }
}