package com.buildflow.erp.domain.achats.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.dto.request.CreateAchatRequest;
import com.buildflow.erp.domain.achats.dto.request.CreateLigneAchatRequest;
import com.buildflow.erp.domain.achats.dto.response.AchatResponse;
import com.buildflow.erp.domain.achats.entity.Achat;
import com.buildflow.erp.domain.achats.entity.AchatStatut;
import com.buildflow.erp.domain.achats.entity.LigneAchat;
import com.buildflow.erp.domain.achats.mapper.AchatMapper;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.referentiel.entity.Article;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.repository.ArticleRepository;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import com.buildflow.erp.domain.stock.service.StockService;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AchatServiceImpl implements AchatService {

    private final AchatRepository achatRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ChantierRepository chantierRepository;
    private final ArticleRepository articleRepository;
    private final AchatMapper achatMapper;
    private final StockService stockService;
    private final TresorerieService tresorerieService;

    private static final BigDecimal TVA_RATE = new BigDecimal("0.20"); // 20% Moroccan TVA

    @Override
    @Transactional
    public AchatResponse create(CreateAchatRequest request) {
        if (achatRepository.existsByRef(request.ref())) {
            throw new BusinessRuleException("An Achat with ref '" + request.ref() + "' already exists");
        }

        Fournisseur fournisseur = fournisseurRepository.findById(request.fournisseurId())
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", request.fournisseurId()));

        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        Achat achat = new Achat();
        achat.setRef(request.ref());
        achat.setFournisseur(fournisseur);
        achat.setChantier(chantier);
        achat.setDateCommande(request.dateCommande());
        achat.setDateLivraisonPrevue(request.dateLivraisonPrevue());

        BigDecimal totalHt = BigDecimal.ZERO;

        for (CreateLigneAchatRequest ligneReq : request.lignes()) {
            Article article = articleRepository.findById(ligneReq.articleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Article", ligneReq.articleId()));

            LigneAchat ligne = new LigneAchat();
            ligne.setAchat(achat);
            ligne.setArticle(article);
            ligne.setDesignation(article.getDesignation()); // Snapshot
            ligne.setUnite(article.getUnite());             // Snapshot
            ligne.setQuantite(ligneReq.quantite());
            ligne.setPrixUnitaire(ligneReq.prixUnitaire());

            BigDecimal ligneTotal = ligneReq.quantite().multiply(ligneReq.prixUnitaire()).setScale(2, RoundingMode.HALF_UP);
            ligne.setTotal(ligneTotal);

            totalHt = totalHt.add(ligneTotal);
            achat.getLignes().add(ligne);
        }

        BigDecimal tva = totalHt.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ttc = totalHt.add(tva);

        achat.setHt(totalHt);
        achat.setTva(tva);
        achat.setTtc(ttc);

        Achat saved = achatRepository.save(achat);
        return achatMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AchatResponse findById(UUID id) {
        Achat achat = achatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat", id));
        return achatMapper.toResponse(achat);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchatResponse> findAll() {
        return achatRepository.findAll().stream()
                .map(achatMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AchatResponse validateBL(UUID id, String bonLivraisonRef) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.EN_COURS, "LIVRE");

        achat.setStatut(AchatStatut.LIVRE);
        achat.setBonLivraisonRef(bonLivraisonRef);

        Achat savedAchat = achatRepository.save(achat);

        // CROSS-DOMAIN SIDE EFFECT: Increment Stock
        stockService.approvisionnerDepuisAchat(savedAchat);

        return achatMapper.toResponse(savedAchat);
    }

    @Override
    @Transactional
    public AchatResponse validateFacture(UUID id, String factureRef) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.LIVRE, "FACTURE");

        achat.setStatut(AchatStatut.FACTURE);
        achat.setFactureRef(factureRef);

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    @Override
    @Transactional
    public AchatResponse validatePaiement(UUID id) {
        Achat achat = findEntityById(id);
        assertStatus(achat, AchatStatut.FACTURE, "PAYE");

        achat.setStatut(AchatStatut.PAYE);

        // CROSS-DOMAIN SIDE EFFECT: Debit the chantier's caisse
        tresorerieService.debiterPourAchat(
                achat.getChantier().getId(), achat.getTtc(), achat.getRef());

        return achatMapper.toResponse(achatRepository.save(achat));
    }

    private Achat findEntityById(UUID id) {
        return achatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat", id));
    }

    private void assertStatus(Achat achat, AchatStatut expectedCurrent, String nextAction) {
        if (achat.getStatut() != expectedCurrent) {
            throw new BusinessRuleException(
                    String.format("Cannot %s an Achat that is currently '%s'. Expected status: '%s'",
                            nextAction, achat.getStatut(), expectedCurrent)
            );
        }
    }
}