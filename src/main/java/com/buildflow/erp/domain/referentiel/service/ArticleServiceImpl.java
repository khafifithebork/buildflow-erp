package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.dto.PageResponse;
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
import org.springframework.data.domain.Pageable;
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
    @Transactional
    public ArticleResponse update(UUID id, CreateArticleRequest request) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));

        if (articleRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("An article with code '" + request.code() + "' already exists");
        }

        CategorieArticle categorie = categorieArticleRepository.findById(request.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("CategorieArticle", request.categorieId()));

        articleMapper.updateEntityFromRequest(request, article);
        article.setCategorie(categorie);

        return articleMapper.toResponse(articleRepository.save(article));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!articleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Article", id);
        }
        articleRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse findById(UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        return articleMapper.toResponse(article);
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleResponse> findAll(Pageable pageable) {
        return PageResponse.from(
                articleRepository.findAll(pageable)
                        .map(articleMapper::toResponse)
        );
    }
}