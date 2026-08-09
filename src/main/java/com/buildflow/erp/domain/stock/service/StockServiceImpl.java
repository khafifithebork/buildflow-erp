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

import java.math.BigDecimal;
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
    @Transactional(readOnly = true)
    public PageResponse<StockArticleResponse> getStockDepot(Pageable pageable) {
        return PageResponse.from(
                stockArticleRepository.findByChantierIsNull(pageable)
                        .map(stockMapper::toResponse)
        );
    }

    @Override
    @Transactional
    public StockArticleResponse createMouvement(CreateMouvementStockRequest request) {
        Article article = articleRepository.findById(request.articleId())
                .orElseThrow(() -> new ResourceNotFoundException("Article", request.articleId()));

        // Null chantier = the central dépôt, on both sides of a movement.
        Chantier source = resolveChantier(request.chantierId());

        if (request.typeMouvement() == TypeMouvement.TRANSFERT) {
            return transferer(article, source, request);
        }

        StockArticle stock = findOrCreateStock(article, source);

        switch (request.typeMouvement()) {
            case ENTREE, AJUSTEMENT -> stock.setQuantiteTheorique(stock.getQuantiteTheorique().add(request.quantite()));
            case SORTIE -> retirer(stock, request.quantite());
            case TRANSFERT -> throw new IllegalStateException("handled above");
        }

        StockArticle savedStock = stockArticleRepository.save(stock);
        enregistrerMouvement(savedStock, request.typeMouvement(), request.quantite(), request.documentRef());

        return stockMapper.toResponse(savedStock);
    }

    /**
     * Moves quantity between two locations — dépôt to chantier, chantier to
     * dépôt, or between two chantiers. Previously rejected outright, which is
     * why nothing could ever sit in a warehouse.
     *
     * <p>Recorded as two TRANSFERT lines sharing a reference, one on each side,
     * because a MouvementStock belongs to exactly one stock line. The response
     * describes the destination, since that is where the stock ended up.
     */
    private StockArticleResponse transferer(Article article, Chantier source, CreateMouvementStockRequest request) {
        Chantier destination = resolveChantier(request.chantierDestinationId());

        if (java.util.Objects.equals(
                source == null ? null : source.getId(),
                destination == null ? null : destination.getId())) {
            throw new BusinessRuleException(
                    "La source et la destination du transfert sont identiques ("
                            + emplacementLabel(source) + ").");
        }

        StockArticle from = stockArticleRepository
                .findByArticleIdAndChantierId(article.getId(), source == null ? null : source.getId())
                .or(() -> source == null
                        ? stockArticleRepository.findByArticleIdAndChantierIsNull(article.getId())
                        : java.util.Optional.empty())
                .orElseThrow(() -> new BusinessRuleException(
                        "Aucun stock de '" + article.getDesignation() + "' à " + emplacementLabel(source) + "."));

        retirer(from, request.quantite());
        StockArticle savedFrom = stockArticleRepository.save(from);

        StockArticle to = findOrCreateStock(article, destination);
        to.setQuantiteTheorique(to.getQuantiteTheorique().add(request.quantite()));
        StockArticle savedTo = stockArticleRepository.save(to);

        String ref = request.documentRef() != null && !request.documentRef().isBlank()
                ? request.documentRef()
                : "TRF-" + emplacementLabel(source) + " → " + emplacementLabel(destination);

        enregistrerMouvement(savedFrom, TypeMouvement.TRANSFERT, request.quantite(), ref);
        enregistrerMouvement(savedTo, TypeMouvement.TRANSFERT, request.quantite(), ref);

        log.info("Transfert de {} {} : {} -> {}", request.quantite(), article.getDesignation(),
                emplacementLabel(source), emplacementLabel(destination));

        return stockMapper.toResponse(savedTo);
    }

    private Chantier resolveChantier(UUID chantierId) {
        if (chantierId == null) {
            return null; // central dépôt
        }
        return chantierRepository.findById(chantierId)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", chantierId));
    }

    private StockArticle findOrCreateStock(Article article, Chantier chantier) {
        java.util.Optional<StockArticle> existing = chantier == null
                ? stockArticleRepository.findByArticleIdAndChantierIsNull(article.getId())
                : stockArticleRepository.findByArticleIdAndChantierId(article.getId(), chantier.getId());

        return existing.orElseGet(() -> {
            StockArticle created = new StockArticle();
            created.setArticle(article);
            created.setChantier(chantier);
            return created;
        });
    }

    private static void retirer(StockArticle stock, BigDecimal quantite) {
        if (stock.getQuantiteTheorique().compareTo(quantite) < 0) {
            throw new BusinessRuleException(
                    "Quantité insuffisante en stock (disponible: " + stock.getQuantiteTheorique() + ")");
        }
        stock.setQuantiteTheorique(stock.getQuantiteTheorique().subtract(quantite));
    }

    private void enregistrerMouvement(StockArticle stock, TypeMouvement type, BigDecimal quantite, String documentRef) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setStockArticle(stock);
        mouvement.setTypeMouvement(type);
        mouvement.setQuantite(quantite);
        mouvement.setDocumentRef(documentRef);
        mouvementStockRepository.save(mouvement);
    }

    private static String emplacementLabel(Chantier chantier) {
        return chantier == null ? "Dépôt central" : chantier.getNom();
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