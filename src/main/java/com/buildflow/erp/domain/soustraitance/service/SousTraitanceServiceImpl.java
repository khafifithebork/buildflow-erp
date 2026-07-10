package com.buildflow.erp.domain.soustraitance.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.SousTraitant;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.SousTraitantRepository;
import com.buildflow.erp.domain.soustraitance.dto.request.CreateContratRequest;
import com.buildflow.erp.domain.soustraitance.dto.request.CreatePaiementRequest;
import com.buildflow.erp.domain.soustraitance.dto.response.ContratSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.dto.response.PaiementSousTraitantResponse;
import com.buildflow.erp.domain.soustraitance.entity.ContratSousTraitant;
import com.buildflow.erp.domain.soustraitance.entity.ContratStatut;
import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import com.buildflow.erp.domain.soustraitance.entity.PaiementStatut;
import com.buildflow.erp.domain.soustraitance.mapper.SousTraitanceMapper;
import com.buildflow.erp.domain.soustraitance.repository.ContratSousTraitantRepository;
import com.buildflow.erp.domain.soustraitance.repository.PaiementSousTraitantRepository;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SousTraitanceServiceImpl implements SousTraitanceService {

    private final ContratSousTraitantRepository contratRepository;
    private final PaiementSousTraitantRepository paiementRepository;
    private final SousTraitantRepository sousTraitantRepository;
    private final ChantierRepository chantierRepository;
    private final SousTraitanceMapper mapper;
    private final TresorerieService tresorerieService;

    private static final BigDecimal TVA_RATE = new BigDecimal("0.20");

    // ── Contrats ───────────────────────────────────────────────────

    @Override
    @Transactional
    public ContratSousTraitantResponse createContrat(CreateContratRequest request) {
        if (contratRepository.existsByReference(request.reference())) {
            throw new ConflictException("A contrat with reference '" + request.reference() + "' already exists");
        }

        SousTraitant st = sousTraitantRepository.findById(request.sousTraitantId())
                .orElseThrow(() -> new ResourceNotFoundException("SousTraitant", request.sousTraitantId()));

        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        ContratSousTraitant contrat = new ContratSousTraitant();
        contrat.setReference(request.reference());
        contrat.setSousTraitant(st);
        contrat.setChantier(chantier);
        contrat.setObjet(request.objet());
        contrat.setMontantHt(request.montantHt());

        BigDecimal tva = request.montantHt().multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        contrat.setTva(tva);
        contrat.setMontantTtc(request.montantHt().add(tva));

        contrat.setDateDebut(request.dateDebut());
        contrat.setDateFin(request.dateFin());

        // Update SousTraitant counter
        st.setNombreContratsActifs(st.getNombreContratsActifs() + 1);
        sousTraitantRepository.save(st);

        return mapper.toContratResponse(contratRepository.save(contrat));
    }

    @Override
    @Transactional(readOnly = true)
    public ContratSousTraitantResponse findContratById(UUID id) {
        return mapper.toContratResponse(findContratEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratSousTraitantResponse> findAllContrats() {
        return contratRepository.findAll().stream()
                .map(mapper::toContratResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContratSousTraitantResponse> findContratsByChantier(UUID chantierId) {
        return contratRepository.findByChantierId(chantierId).stream()
                .map(mapper::toContratResponse)
                .toList();
    }

    @Override
    @Transactional
    public ContratSousTraitantResponse terminerContrat(UUID id) {
        ContratSousTraitant contrat = findContratEntity(id);
        if (contrat.getStatut() != ContratStatut.EN_COURS) {
            throw new BusinessRuleException("Cannot terminate a contract that is '" + contrat.getStatut() + "'");
        }

        contrat.setStatut(ContratStatut.TERMINE);

        // Decrement SousTraitant active contract counter
        SousTraitant st = contrat.getSousTraitant();
        st.setNombreContratsActifs(Math.max(0, st.getNombreContratsActifs() - 1));
        sousTraitantRepository.save(st);

        log.info("Contrat {} terminated for sous-traitant {}", contrat.getReference(), st.getRaisonSociale());
        return mapper.toContratResponse(contratRepository.save(contrat));
    }

    // ── Paiements ──────────────────────────────────────────────────

    @Override
    @Transactional
    public PaiementSousTraitantResponse createPaiement(UUID contratId, CreatePaiementRequest request) {
        ContratSousTraitant contrat = findContratEntity(contratId);

        if (contrat.getStatut() != ContratStatut.EN_COURS) {
            throw new BusinessRuleException("Cannot add payment to a contract that is '" + contrat.getStatut() + "'");
        }

        if (paiementRepository.existsByReference(request.reference())) {
            throw new ConflictException("A paiement with reference '" + request.reference() + "' already exists");
        }

        // Validate payment doesn't exceed remaining amount
        BigDecimal resteAPayer = contrat.getMontantTtc().subtract(contrat.getMontantPaye());
        if (request.montant().compareTo(resteAPayer) > 0) {
            throw new BusinessRuleException(
                    String.format("Payment of %s exceeds remaining amount of %s for contrat %s",
                            request.montant(), resteAPayer, contrat.getReference()));
        }

        PaiementSousTraitant paiement = new PaiementSousTraitant();
        paiement.setReference(request.reference());
        paiement.setContrat(contrat);
        paiement.setMontant(request.montant());
        paiement.setMotif(request.motif());

        return mapper.toPaiementResponse(paiementRepository.save(paiement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaiementSousTraitantResponse> getPaiements(UUID contratId) {
        if (!contratRepository.existsById(contratId)) {
            throw new ResourceNotFoundException("ContratSousTraitant", contratId);
        }
        return paiementRepository.findByContratIdOrderByCreatedAtDesc(contratId).stream()
                .map(mapper::toPaiementResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaiementSousTraitantResponse validerPaiement(UUID paiementId) {
        PaiementSousTraitant paiement = findPaiementEntity(paiementId);
        assertPaiementStatut(paiement, PaiementStatut.EN_ATTENTE, "VALIDER");

        paiement.setStatut(PaiementStatut.VALIDE);
        return mapper.toPaiementResponse(paiementRepository.save(paiement));
    }

    @Override
    @Transactional
    public PaiementSousTraitantResponse payerPaiement(UUID paiementId) {
        PaiementSousTraitant paiement = findPaiementEntity(paiementId);
        assertPaiementStatut(paiement, PaiementStatut.VALIDE, "PAYER");

        paiement.setStatut(PaiementStatut.PAYE);
        paiement.setDatePaiement(LocalDate.now());

        ContratSousTraitant contrat = paiement.getContrat();

        // Update contract paid amount
        contrat.setMontantPaye(contrat.getMontantPaye().add(paiement.getMontant()));
        contratRepository.save(contrat);

        // Update SousTraitant total paid
        SousTraitant st = contrat.getSousTraitant();
        st.setMontantTotalPaye(st.getMontantTotalPaye().add(paiement.getMontant()));
        sousTraitantRepository.save(st);

        // CROSS-DOMAIN SIDE EFFECT: Debit the chantier's caisse
        tresorerieService.debiterPourAchat(
                contrat.getChantier().getId(),
                paiement.getMontant(),
                "ST-" + paiement.getReference());

        log.info("Paiement {} paid: {} MAD for contrat {} (sous-traitant: {})",
                paiement.getReference(), paiement.getMontant(),
                contrat.getReference(), st.getRaisonSociale());

        return mapper.toPaiementResponse(paiementRepository.save(paiement));
    }

    // ── Private ────────────────────────────────────────────────────

    private ContratSousTraitant findContratEntity(UUID id) {
        return contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContratSousTraitant", id));
    }

    private PaiementSousTraitant findPaiementEntity(UUID id) {
        return paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaiementSousTraitant", id));
    }

    private void assertPaiementStatut(PaiementSousTraitant p, PaiementStatut expected, String action) {
        if (p.getStatut() != expected) {
            throw new BusinessRuleException(
                    String.format("Cannot %s a paiement that is currently '%s'. Expected: '%s'",
                            action, p.getStatut(), expected));
        }
    }
}
