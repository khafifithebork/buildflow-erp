package com.buildflow.erp.domain.stock.service;

import com.buildflow.erp.common.dto.PageResponse;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
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