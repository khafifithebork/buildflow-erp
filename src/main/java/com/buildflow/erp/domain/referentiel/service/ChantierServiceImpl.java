package com.buildflow.erp.domain.referentiel.service;

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
import com.buildflow.erp.domain.tresorerie.entity.Caisse;
import com.buildflow.erp.domain.tresorerie.repository.CaisseRepository;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChantierServiceImpl implements ChantierService {

    private final ChantierRepository chantierRepository;
    private final ChantierMapper chantierMapper;
    private final JalonMapper jalonMapper;
    private final CaisseRepository caisseRepository;

    @Override
    @Transactional
    public ChantierResponse create(CreateChantierRequest request) {
        if (chantierRepository.existsByCode(request.code())) {
            throw new ConflictException("A chantier with code '" + request.code() + "' already exists");
        }

        Chantier chantier = chantierMapper.toEntity(request);

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

        if (chantierRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("A chantier with code '" + request.code() + "' already exists");
        }

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

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!chantierRepository.existsById(id)) {
            throw new ResourceNotFoundException("Chantier", id);
        }
        chantierRepository.deleteById(id);
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