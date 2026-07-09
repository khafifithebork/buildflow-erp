package com.buildflow.erp.domain.salaires.service;

import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Employe;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.EmployeRepository;
import com.buildflow.erp.domain.salaires.dto.request.CreateFichePaieRequest;
import com.buildflow.erp.domain.salaires.dto.response.FichePaieResponse;
import com.buildflow.erp.domain.salaires.entity.FichePaie;
import com.buildflow.erp.domain.salaires.entity.FichePaieStatut;
import com.buildflow.erp.domain.salaires.mapper.FichePaieMapper;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.tresorerie.service.TresorerieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaireServiceImpl implements SalaireService {

    private final FichePaieRepository fichePaieRepository;
    private final EmployeRepository employeRepository;
    private final ChantierRepository chantierRepository;
    private final FichePaieMapper fichePaieMapper;
    private final TresorerieService tresorerieService;

    @Override
    @Transactional
    public FichePaieResponse create(CreateFichePaieRequest request) {
        if (fichePaieRepository.existsByReference(request.reference())) {
            throw new ConflictException("A fiche de paie with reference '" + request.reference() + "' already exists");
        }

        if (fichePaieRepository.existsByEmployeIdAndPeriode(request.employeId(), request.periode())) {
            throw new ConflictException("A fiche de paie already exists for this employee for period " + request.periode());
        }

        Employe employe = employeRepository.findById(request.employeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employe", request.employeId()));

        Chantier chantier = chantierRepository.findById(request.chantierId())
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierId()));

        FichePaie fiche = new FichePaie();
        fiche.setReference(request.reference());
        fiche.setEmploye(employe);
        fiche.setChantier(chantier);
        fiche.setPeriode(request.periode());
        fiche.setJoursTravailles(request.joursTravailles());
        fiche.setSalaireBase(request.salaireBase());
        fiche.setHeuresSupplementaires(orZero(request.heuresSupplementaires()));
        fiche.setMontantHeuresSupp(orZero(request.montantHeuresSupp()));
        fiche.setPrimeTransport(orZero(request.primeTransport()));
        fiche.setPrimePanier(orZero(request.primePanier()));
        fiche.setAutresPrimes(orZero(request.autresPrimes()));
        fiche.setAvance(orZero(request.avance()));
        fiche.setDeductionsCnss(orZero(request.deductionsCnss()));
        fiche.setDeductionsIr(orZero(request.deductionsIr()));

        // Compute net à payer server-side
        fiche.setNetAPayer(computeNet(fiche));

        return fichePaieMapper.toResponse(fichePaieRepository.save(fiche));
    }

    @Override
    @Transactional(readOnly = true)
    public FichePaieResponse findById(UUID id) {
        FichePaie fiche = fichePaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FichePaie", id));
        return fichePaieMapper.toResponse(fiche);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichePaieResponse> findAll() {
        return fichePaieRepository.findAll().stream()
                .map(fichePaieMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FichePaieResponse> findByPeriode(String periode) {
        return fichePaieRepository.findByPeriode(periode).stream()
                .map(fichePaieMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FichePaieResponse valider(UUID id) {
        FichePaie fiche = findEntity(id);
        assertStatut(fiche, FichePaieStatut.BROUILLON, "VALIDER");

        fiche.setStatut(FichePaieStatut.VALIDEE);
        log.info("Fiche de paie {} validated for {} ({})",
                fiche.getReference(), fiche.getEmploye().getMatricule(), fiche.getPeriode());

        return fichePaieMapper.toResponse(fichePaieRepository.save(fiche));
    }

    @Override
    @Transactional
    public FichePaieResponse payer(UUID id) {
        FichePaie fiche = findEntity(id);
        assertStatut(fiche, FichePaieStatut.VALIDEE, "PAYER");

        fiche.setStatut(FichePaieStatut.PAYEE);

        // CROSS-DOMAIN SIDE EFFECT: Debit the chantier's caisse
        tresorerieService.debiterPourAchat(
                fiche.getChantier().getId(),
                fiche.getNetAPayer(),
                "SAL-" + fiche.getReference());

        log.info("Fiche de paie {} paid: {} MAD for {} ({})",
                fiche.getReference(), fiche.getNetAPayer(),
                fiche.getEmploye().getMatricule(), fiche.getPeriode());

        return fichePaieMapper.toResponse(fichePaieRepository.save(fiche));
    }

    // ── Private ────────────────────────────────────────────────────

    private FichePaie findEntity(UUID id) {
        return fichePaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FichePaie", id));
    }

    private void assertStatut(FichePaie fiche, FichePaieStatut expected, String action) {
        if (fiche.getStatut() != expected) {
            throw new BusinessRuleException(
                    String.format("Cannot %s a fiche de paie that is currently '%s'. Expected: '%s'",
                            action, fiche.getStatut(), expected));
        }
    }

    /**
     * Net = salaireBase + montantHeuresSupp + primeTransport + primePanier + autresPrimes
     *       - avance - deductionsCnss - deductionsIr
     */
    private BigDecimal computeNet(FichePaie f) {
        return f.getSalaireBase()
                .add(f.getMontantHeuresSupp())
                .add(f.getPrimeTransport())
                .add(f.getPrimePanier())
                .add(f.getAutresPrimes())
                .subtract(f.getAvance())
                .subtract(f.getDeductionsCnss())
                .subtract(f.getDeductionsIr());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
