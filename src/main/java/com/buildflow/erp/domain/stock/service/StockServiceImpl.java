package com.buildflow.erp.domain.stock.service;

import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.stock.dto.request.CreateMouvementStockRequest;
import com.buildflow.erp.domain.stock.dto.response.StockArticleResponse;
import com.buildflow.erp.domain.stock.entity.MouvementStock;
import com.buildflow.erp.domain.stock.entity.StockArticle;
import com.buildflow.erp.domain.stock.entity.TypeMouvement;
import com.buildflow.erp.domain.stock.mapper.StockMapper;
import com.buildflow.erp.domain.stock.repository.MouvementStockRepository;
import com.buildflow.erp.domain.stock.repository.StockArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final StockArticleRepository stockArticleRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final ArticleRepository articleRepository;
    private final ChantierRepository chantierRepository;
    private final StockMapper stockMapper;

    @Override
    @Transactional
    public void approvisionnerDepuisAchat(Achat achat) {
        log.info("Approvisioning stock for Achat: {}", achat.getRef());

        for (LigneAchat ligne : achat.getLignes()) {
            // 1. Find or Create the StockArticle record for this (Article, Chantier) pair
            StockArticle stock = stockArticleRepository
                    .findByArticleIdAndChantierId(ligne.getArticle().getId(), achat.getChantier().getId())
                    .orElseGet(() -> {
                        StockArticle newStock = new StockArticle();
                        newStock.setArticle(ligne.getArticle());
                        newStock.setChantier(achat.getChantier());
                        return newStock;
                    });

            // 2. Update the theoretical quantity
            stock.setQuantiteTheorique(stock.getQuantiteTheorique().add(ligne.getQuantite()));
            stockArticleRepository.save(stock);

            // 3. Record the immutable ledger entry
            MouvementStock mouvement = new MouvementStock();
            mouvement.setStockArticle(stock);
            mouvement.setTypeMouvement(TypeMouvement.ENTREE);
            mouvement.setQuantite(ligne.getQuantite());
            mouvement.setDocumentRef(achat.getRef()); // Traceability!
            mouvementStockRepository.save(mouvement);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockArticleResponse> getStockByChantier(UUID chantierId, Pageable pageable) {
        return PageResponse.from(
                stockArticleRepository.findByChantierId(chantierId, pageable)
                        .map(stockMapper::toResponse)
        );
    }

    @Override
    @Transactional
    public StockArticleResponse createMouvement(CreateMouvementStockRequest request) {
        Article article = articleRepository.findById(request.articleId())
                .orElseThrow(() -> new ResourceNotFoundException("Article", request.articleId()));
        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        if (request.typeMouvement() == TypeMouvement.TRANSFERT) {
            throw new BusinessRuleException(
                    "TRANSFERT n'est pas supporté par ce mouvement manuel (nécessite un chantier destination)");
        }

        StockArticle stock = stockArticleRepository
                .findByArticleIdAndChantierId(article.getId(), chantier.getId())
                .orElseGet(() -> {
                    StockArticle newStock = new StockArticle();
                    newStock.setArticle(article);
                    newStock.setChantier(chantier);
                    return newStock;
                });

        switch (request.typeMouvement()) {
            case ENTREE, AJUSTEMENT -> stock.setQuantiteTheorique(stock.getQuantiteTheorique().add(request.quantite()));
            case SORTIE -> {
                if (stock.getQuantiteTheorique().compareTo(request.quantite()) < 0) {
                    throw new BusinessRuleException(
                            "Quantité insuffisante en stock (disponible: " + stock.getQuantiteTheorique() + ")");
                }
                stock.setQuantiteTheorique(stock.getQuantiteTheorique().subtract(request.quantite()));
            }
            case TRANSFERT -> throw new BusinessRuleException("unreachable");
        }

        StockArticle savedStock = stockArticleRepository.save(stock);

        MouvementStock mouvement = new MouvementStock();
        mouvement.setStockArticle(savedStock);
        mouvement.setTypeMouvement(request.typeMouvement());
        mouvement.setQuantite(request.quantite());
        mouvement.setDocumentRef(request.documentRef());
        mouvementStockRepository.save(mouvement);

        return stockMapper.toResponse(savedStock);
    }

    @Transactional(readOnly = true)
    public List<StockArticleResponse> getStockByChantier(UUID chantierId) {
        // Note: In a real app, we'd add a custom query to StockArticleRepository for this.
        // For now, returning all to keep the build moving, but filtering by chantier in a custom query is the next optimization.
        return stockArticleRepository.findAll().stream()
                .filter(s -> s.getChantier().getId().equals(chantierId))
                .map(stockMapper::toResponse)
                .toList();
    }
}