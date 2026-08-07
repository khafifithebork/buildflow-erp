package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateChantierRequest;
import com.buildflow.erp.domain.referentiel.dto.response.ChantierResponse;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.ChantierStatut;
import com.buildflow.erp.domain.referentiel.entity.Jalon;
import com.buildflow.erp.domain.referentiel.mapper.ChantierMapper;
import com.buildflow.erp.domain.referentiel.mapper.JalonMapper;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.attachement.repository.AttachementRepository;
import com.buildflow.erp.domain.salaires.repository.FichePaieRepository;
import com.buildflow.erp.domain.soustraitance.repository.ContratSousTraitantRepository;
import com.buildflow.erp.domain.stock.repository.StockArticleRepository;
import com.buildflow.erp.domain.tresorerie.entity.Caisse;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
import com.buildflow.erp.domain.tresorerie.repository.CaisseTransactionRepository;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChantierServiceImpl implements ChantierService {

    private final ChantierRepository chantierRepository;
    private final ChantierMapper chantierMapper;
    private final JalonMapper jalonMapper;
    private final CaisseRepository caisseRepository;
    private final CodeGenerator codeGenerator;

    // Read-only, used by delete() to explain exactly what still references the
    // chantier instead of letting the DB raise an opaque FK violation.
    private final CaisseTransactionRepository caisseTransactionRepository;
    private final AchatRepository achatRepository;
    private final FichePaieRepository fichePaieRepository;
    private final ContratSousTraitantRepository contratSousTraitantRepository;
    private final AttachementRepository attachementRepository;
    private final StockArticleRepository stockArticleRepository;

    @Override
    @Transactional
    public ChantierResponse create(CreateChantierRequest request) {
        Chantier chantier = chantierMapper.toEntity(request);
        chantier.setCode(codeGenerator.next(CodeSequence.CHANTIER));

        // Manually map nested Jalons to ensure the bidirectional relationship is set correctly
        if (request.jalons() != null) {
            for (var jalonReq : request.jalons()) {
                Jalon jalon = jalonMapper.toEntity(jalonReq);
                jalon.setChantier(chantier); // Set parent reference
                chantier.getJalons().add(jalon);
            }
        }

        Chantier saved = chantierRepository.save(chantier);

        Caisse caisse = new Caisse();
        caisse.setCode("CAISSE-" + saved.getCode());
        caisse.setLibelle("Caisse " + saved.getNom());
        caisse.setChantier(saved);
        caisse.setSolde(BigDecimal.ZERO);
        caisse.setSeuilMinimum(BigDecimal.ZERO);

        caisseRepository.save(caisse);

        return chantierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ChantierResponse update(UUID id, CreateChantierRequest request) {
        Chantier chantier = chantierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", id));

        // The code is assigned once at creation and never changes.
        chantierMapper.updateEntityFromRequest(request, chantier);

        return chantierMapper.toResponse(chantierRepository.save(chantier));
    }

    @Override
    @Transactional
    public ChantierResponse demarrer(UUID id) {
        Chantier chantier = chantierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", id));

        if (chantier.getStatut() != ChantierStatut.EN_PREPARATION) {
            throw new BusinessRuleException(
                    "Seul un chantier en préparation peut être démarré (statut actuel: " + chantier.getStatut() + ")");
        }

        chantier.setStatut(ChantierStatut.EN_COURS);

        return chantierMapper.toResponse(chantierRepository.save(chantier));
    }

    /**
     * Deletes a chantier.
     *
     * <p>Historically this was a bare {@code deleteById}, which could never
     * succeed: {@link #create} auto-provisions a Caisse for every chantier, and
     * several tables point at {@code chantiers} with a restrictive foreign key.
     * The database therefore rejected the DELETE and the user saw nothing but a
     * silent failure.
     *
     * <p>The rules now applied:
     * <ul>
     *   <li><b>Blocked</b> when real business documents exist (achats, fiches de
     *       paie, contrats de sous-traitance, attachements, lignes de stock,
     *       opérations de caisse). A {@link ConflictException} names exactly what
     *       has to be removed first — accounting history is never destroyed by a
     *       referential-cleanup side effect.</li>
     *   <li><b>Cascaded</b> for artifacts that only exist because the chantier
     *       does: jalons, lignes BPU, and the auto-provisioned empty caisse(s).</li>
     *   <li><b>Unlinked</b> for employees, whose {@code chantier_actuel_id} is
     *       already {@code ON DELETE SET NULL}.</li>
     * </ul>
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        if (!chantierRepository.existsById(id)) {
            throw new ResourceNotFoundException("Chantier", id);
        }

        List<String> blockers = new ArrayList<>();
        addBlocker(blockers, achatRepository.countByChantierId(id), "commande d'achat", "commandes d'achat");
        addBlocker(blockers, caisseTransactionRepository.countByCaisse_ChantierId(id),
                "opération de caisse", "opérations de caisse");
        addBlocker(blockers, fichePaieRepository.countByChantierId(id), "fiche de paie", "fiches de paie");
        addBlocker(blockers, contratSousTraitantRepository.countByChantierId(id),
                "contrat de sous-traitance", "contrats de sous-traitance");
        addBlocker(blockers, attachementRepository.countByChantierId(id), "attachement", "attachements");
        addBlocker(blockers, stockArticleRepository.countByChantierId(id), "ligne de stock", "lignes de stock");

        if (!blockers.isEmpty()) {
            throw new ConflictException(
                    "Ce chantier ne peut pas être supprimé : il est encore référencé par "
                            + String.join(", ", blockers)
                            + ". Supprimez ou réaffectez ces éléments avant de réessayer.");
        }

        // Only the auto-provisioned, never-used caisse can be left at this point
        // (any caisse holding transactions was caught by the check above).
        caisseRepository.deleteAll(caisseRepository.findByChantierId(id));

        chantierRepository.deleteById(id);
    }

    private static void addBlocker(List<String> blockers, long count, String singular, String plural) {
        if (count > 0) {
            blockers.add(count + " " + (count == 1 ? singular : plural));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChantierResponse findById(UUID id) {
        Chantier chantier = chantierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", id));
        return chantierMapper.toResponse(chantier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChantierResponse> findAll() {
        return chantierRepository.findAll().stream()
                .map(chantierMapper::toResponse)
                .toList();
    }
}