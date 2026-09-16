package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.achats.repository.AchatRepository;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.mapper.FournisseurMapper;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FournisseurServiceImpl implements FournisseurService {

    private final FournisseurRepository fournisseurRepository;
    private final FournisseurMapper fournisseurMapper;
    private final CodeGenerator codeGenerator;
    private final AchatRepository achatRepository;

    @Override
    @Transactional
    public FournisseurResponse create(CreateFournisseurRequest request) {
        Fournisseur fournisseur = fournisseurMapper.toEntity(request);
        fournisseur.setCode(codeGenerator.next(CodeSequence.FOURNISSEUR));
        Fournisseur saved = fournisseurRepository.save(fournisseur);
        return fournisseurMapper.toResponse(saved, soldeDe(saved.getId()));
    }

    @Override
    @Transactional
    public FournisseurResponse update(UUID id, CreateFournisseurRequest request) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));

        // The code is assigned once at creation and never changes.
        fournisseurMapper.updateEntityFromRequest(request, fournisseur);

        Fournisseur saved = fournisseurRepository.save(fournisseur);
        return fournisseurMapper.toResponse(saved, soldeDe(saved.getId()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!fournisseurRepository.existsById(id)) {
            throw new ResourceNotFoundException("Fournisseur", id);
        }
        fournisseurRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public FournisseurResponse findById(UUID id) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
        return fournisseurMapper.toResponse(fournisseur, soldeDe(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FournisseurResponse> findAll() {
        Map<UUID, BigDecimal> soldes = achatRepository.sumTtcNonPayeesParFournisseur().stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1]));

        return fournisseurRepository.findAll().stream()
                .map(f -> fournisseurMapper.toResponse(
                        f, soldes.getOrDefault(f.getId(), BigDecimal.ZERO)))
                .toList();
    }

    /**
     * Ce que ce fournisseur reste dû : ses achats non soldés, TTC.
     *
     * <p>Même définition que {@code sumTtcNonPayees}, dont dépend la carte
     * Dettes Fournisseurs du tableau de bord — les soldes par fournisseur
     * somment donc au total affiché là-bas.
     */
    private BigDecimal soldeDe(UUID fournisseurId) {
        return achatRepository.sumTtcNonPayeesByFournisseurId(fournisseurId);
    }
}