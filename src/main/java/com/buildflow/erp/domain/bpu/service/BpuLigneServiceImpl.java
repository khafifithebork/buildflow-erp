package com.buildflow.erp.domain.bpu.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.bpu.dto.request.CreateBpuLigneRequest;
import com.buildflow.erp.domain.bpu.dto.request.ImportBpuLignesRequest;
import com.buildflow.erp.domain.bpu.dto.response.BpuLigneResponse;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.bpu.mapper.BpuLigneMapper;
import com.buildflow.erp.domain.bpu.repository.BpuLigneRepository;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.soustraitance.repository.ContratSousTraitantRepository;
import com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BpuLigneServiceImpl implements BpuLigneService {

    private final BpuLigneRepository bpuLigneRepository;
    private final ChantierRepository chantierRepository;
    private final AchatRepository achatRepository;
    private final CaisseTransactionRepository caisseTransactionRepository;
    private final FichePaieRepository fichePaieRepository;
    private final ContratSousTraitantRepository contratSousTraitantRepository;
    private final BpuLigneMapper bpuLigneMapper;
    private final BpuExcelParser bpuExcelParser;

    private static final BigDecimal TVA_MULTIPLIER = new BigDecimal("1.20");

    @Override
    @Transactional
    public BpuLigneResponse create(UUID chantierId, CreateBpuLigneRequest request) {
        Chantier chantier = findChantier(chantierId);

        if (bpuLigneRepository.existsByChantierIdAndRef(chantierId, request.ref())) {
            throw new BusinessRuleException(
                    "A BPU ligne with ref '" + request.ref() + "' already exists for this chantier");
        }

        BpuLigne ligne = new BpuLigne();
        ligne.setChantier(chantier);
        applyRequest(ligne, request);

        return withConsommation(bpuLigneRepository.save(ligne));
    }

    @Override
    @Transactional
    public BpuLigneResponse update(UUID chantierId, UUID id, CreateBpuLigneRequest request) {
        BpuLigne ligne = findLigne(chantierId, id);

        if (bpuLigneRepository.existsByChantierIdAndRefAndIdNot(chantierId, request.ref(), id)) {
            throw new BusinessRuleException(
                    "A BPU ligne with ref '" + request.ref() + "' already exists for this chantier");
        }

        applyRequest(ligne, request);

        return withConsommation(bpuLigneRepository.save(ligne));
    }

    @Override
    @Transactional
    public void delete(UUID chantierId, UUID id) {
        BpuLigne ligne = findLigne(chantierId, id);
        bpuLigneRepository.delete(ligne);
    }

    @Override
    @Transactional
    public List<BpuLigneResponse> batchReplace(UUID chantierId, ImportBpuLignesRequest request) {
        Chantier chantier = findChantier(chantierId);

        bpuLigneRepository.deleteAll(bpuLigneRepository.findByChantierId(chantierId));

        List<BpuLigne> lignes = request.lignes().stream()
                .map(req -> {
                    BpuLigne ligne = new BpuLigne();
                    ligne.setChantier(chantier);
                    applyRequest(ligne, req);
                    return ligne;
                })
                .toList();

        return bpuLigneRepository.saveAll(lignes).stream()
                .map(this::withConsommation)
                .toList();
    }

    @Override
    @Transactional
    public List<BpuLigneResponse> importExcel(UUID chantierId, MultipartFile file) {
        List<CreateBpuLigneRequest> parsed = bpuExcelParser.parse(file);
        return batchReplace(chantierId, new ImportBpuLignesRequest(parsed));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BpuLigneResponse> findByChantier(UUID chantierId) {
        if (!chantierRepository.existsById(chantierId)) {
            throw new ResourceNotFoundException("Chantier", chantierId);
        }
        return bpuLigneRepository.findByChantierId(chantierId).stream()
                .map(this::withConsommation)
                .toList();
    }

    // ── Private ────────────────────────────────────────────────────

    private void applyRequest(BpuLigne ligne, CreateBpuLigneRequest request) {
        ligne.setRef(request.ref());
        ligne.setDesignation(request.designation());
        ligne.setUnite(request.unite());
        ligne.setQtePrevue(request.qtePrevue());
        ligne.setPuHt(request.puHt());
        ligne.setBudgetPrevuHt(
                request.qtePrevue().multiply(request.puHt()).setScale(2, RoundingMode.HALF_UP));
    }

    private BpuLigneResponse withConsommation(BpuLigne ligne) {
        BpuLigneResponse base = bpuLigneMapper.toResponse(ligne);

        BigDecimal achatsHt = achatRepository.sumMontantEngageByBpuLigneId(ligne.getId());
        BigDecimal caisseTtc = caisseTransactionRepository.sumMontantTtcByBpuLigneId(ligne.getId());
        BigDecimal caisseHt = caisseTtc.divide(TVA_MULTIPLIER, 2, RoundingMode.HALF_UP);
        BigDecimal paieHt = fichePaieRepository.sumMontantEngageByBpuLigneId(ligne.getId());
        BigDecimal sousTraitanceHt = contratSousTraitantRepository.sumMontantEngageByBpuLigneId(ligne.getId());

        BigDecimal montantEngageHt = achatsHt.add(caisseHt).add(paieHt).add(sousTraitanceHt);

        BigDecimal tauxConsommation = ligne.getBudgetPrevuHt().compareTo(BigDecimal.ZERO) > 0
                ? montantEngageHt.divide(ligne.getBudgetPrevuHt(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        boolean alerteDepassement = montantEngageHt.compareTo(ligne.getBudgetPrevuHt()) > 0;

        return new BpuLigneResponse(
                base.id(), base.ref(), base.designation(), base.unite(),
                base.qtePrevue(), base.puHt(), base.budgetPrevuHt(),
                montantEngageHt, tauxConsommation, alerteDepassement);
    }

    private Chantier findChantier(UUID chantierId) {
        return chantierRepository.findById(chantierId)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", chantierId));
    }

    private BpuLigne findLigne(UUID chantierId, UUID id) {
        BpuLigne ligne = bpuLigneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BpuLigne", id));
        if (!ligne.getChantier().getId().equals(chantierId)) {
            throw new ResourceNotFoundException("BpuLigne", id);
        }
        return ligne;
    }
}
