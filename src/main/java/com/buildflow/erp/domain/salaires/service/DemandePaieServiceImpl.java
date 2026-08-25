package com.buildflow.erp.domain.salaires.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.common.paiement.ModePaiement;
import com.buildflow.erp.common.paiement.ModePaiementAudit;
import com.buildflow.erp.common.paiement.TypeDocumentPaiement;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.bpu.repository.BpuLigneRepository;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.salaires.dto.request.CreateDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.request.PayerDemandePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.DemandePaieResponse;
import com.buildflow.erp.domain.salaires.entity.DemandePaie;
import com.buildflow.erp.domain.salaires.entity.DemandePaieStatut;
import com.buildflow.erp.domain.salaires.mapper.DemandePaieMapper;
import com.buildflow.erp.domain.salaires.repository.DemandePaieRepository;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemandePaieServiceImpl implements DemandePaieService {

    private final DemandePaieRepository demandePaieRepository;
    private final ChantierRepository chantierRepository;
    private final BpuLigneRepository bpuLigneRepository;
    private final DemandePaieMapper demandePaieMapper;
    private final TresorerieService tresorerieService;
    private final CodeGenerator codeGenerator;
    private final ModePaiementAudit modePaiementAudit;

    @Override
    @Transactional
    public DemandePaieResponse create(CreateDemandePaieRequest request) {
        DemandePaie demande = new DemandePaie();
        // Numbering restarts each payroll period, like the payslips: DP-2026-07-001.
        demande.setReference(codeGenerator.next(CodeSequence.DEMANDE_PAIE, request.periode()));
        demande.setLibelle(request.libelle());
        demande.setPeriode(request.periode());
        demande.setMontantNet(request.montantNet());

        if (request.chantierId() != null) {
            Chantier chantier = chantierRepository.findById(request.chantierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));
            demande.setChantier(chantier);
        }

        if (request.bpuLigneId() != null) {
            BpuLigne bpuLigne = bpuLigneRepository.findById(request.bpuLigneId())
                    .orElseThrow(() -> new ResourceNotFoundException("BpuLigne", request.bpuLigneId()));
            demande.setBpuLigne(bpuLigne);
        }

        return demandePaieMapper.toResponse(demandePaieRepository.save(demande));
    }

    @Override
    @Transactional(readOnly = true)
    public DemandePaieResponse findById(UUID id) {
        return demandePaieMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemandePaieResponse> findAll() {
        return demandePaieRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(demandePaieMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DemandePaieResponse> findByPeriode(String periode) {
        return demandePaieRepository.findByPeriodeOrderByCreatedAtDesc(periode).stream()
                .map(demandePaieMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DemandePaieResponse valider(UUID id) {
        DemandePaie demande = findEntity(id);
        assertStatut(demande, DemandePaieStatut.SOUMISE, "VALIDER");

        demande.setStatut(DemandePaieStatut.VALIDEE);
        log.info("Demande de paie {} validated: {} ({})",
                demande.getReference(), demande.getLibelle(), demande.getPeriode());

        return demandePaieMapper.toResponse(demandePaieRepository.save(demande));
    }

    @Override
    @Transactional
    public DemandePaieResponse payer(UUID id, PayerDemandePaieRequest request) {
        DemandePaie demande = findEntity(id);
        assertStatut(demande, DemandePaieStatut.VALIDEE, "PAYER");

        // A caisse belongs to a chantier. Paying cash out of no chantier would
        // debit nothing, and the balance would silently overstate what is left.
        // Refusing is the only honest answer: impute the demande, then pay it.
        if (request.modePaiement() == ModePaiement.CAISSE && demande.getChantier() == null) {
            throw new BusinessRuleException(
                    "Cannot pay a demande de paie from the caisse without a chantier: "
                            + "assign one first, or settle it outside the caisse.");
        }

        ModePaiement ancien = demande.getModePaiement();

        demande.setStatut(DemandePaieStatut.PAYEE);
        demande.setModePaiement(request.modePaiement());
        modePaiementAudit.record(TypeDocumentPaiement.DEMANDE_PAIE, demande.getId(),
                demande.getReference(), ancien, request.modePaiement());

        // CROSS-DOMAIN SIDE EFFECT: same rule as a fiche de paie — only cash
        // actually leaves the caisse. A virement settles through the bank.
        if (request.modePaiement() == ModePaiement.CAISSE) {
            tresorerieService.debiterPourSalaire(
                    demande.getChantier().getId(),
                    demande.getMontantNet(),
                    "SAL-" + demande.getReference());
        }

        log.info("Demande de paie {} paid via {}: {} MAD ({})",
                demande.getReference(), request.modePaiement(),
                demande.getMontantNet(), demande.getPeriode());

        return demandePaieMapper.toResponse(demandePaieRepository.save(demande));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        DemandePaie demande = findEntity(id);

        // Once paid, the demande has moved money and is referenced by the
        // payment-mode audit trail. Deleting it would leave the ledger
        // pointing at a document that no longer exists.
        if (demande.getStatut() == DemandePaieStatut.PAYEE) {
            throw new BusinessRuleException(
                    "Cannot delete a demande de paie that has already been paid.");
        }

        demandePaieRepository.delete(demande);
        log.info("Demande de paie {} deleted while {}", demande.getReference(), demande.getStatut());
    }

    // ── Private ────────────────────────────────────────────────────

    private DemandePaie findEntity(UUID id) {
        return demandePaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DemandePaie", id));
    }

    private void assertStatut(DemandePaie demande, DemandePaieStatut expected, String action) {
        if (demande.getStatut() != expected) {
            throw new BusinessRuleException(
                    String.format("Cannot %s a demande de paie that is currently '%s'. Expected: '%s'",
                            action, demande.getStatut(), expected));
        }
    }
}
