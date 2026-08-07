package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateSousTraitantRequest;
import com.buildflow.erp.domain.referentiel.dto.response.SousTraitantResponse;
import com.buildflow.erp.domain.referentiel.entity.SousTraitant;
import com.buildflow.erp.domain.referentiel.mapper.SousTraitantMapper;
import com.buildflow.erp.domain.referentiel.repository.SousTraitantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SousTraitantServiceImpl implements SousTraitantService {

    private final SousTraitantRepository sousTraitantRepository;
    private final SousTraitantMapper sousTraitantMapper;
    private final CodeGenerator codeGenerator;

    @Override
    @Transactional
    public SousTraitantResponse create(CreateSousTraitantRequest request) {
        if (sousTraitantRepository.existsByIce(request.ice())) {
            throw new ConflictException("A sous-traitant with ICE '" + request.ice() + "' already exists");
        }

        SousTraitant sousTraitant = sousTraitantMapper.toEntity(request);
        sousTraitant.setCode(codeGenerator.next(CodeSequence.SOUS_TRAITANT));
        SousTraitant saved = sousTraitantRepository.save(sousTraitant);
        return sousTraitantMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SousTraitantResponse update(UUID id, CreateSousTraitantRequest request) {
        SousTraitant sousTraitant = sousTraitantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SousTraitant", id));

        // The code is assigned once at creation and never changes.
        if (sousTraitantRepository.existsByIceAndIdNot(request.ice(), id)) {
            throw new ConflictException("A sous-traitant with ICE '" + request.ice() + "' already exists");
        }

        sousTraitantMapper.updateEntityFromRequest(request, sousTraitant);

        return sousTraitantMapper.toResponse(sousTraitantRepository.save(sousTraitant));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!sousTraitantRepository.existsById(id)) {
            throw new ResourceNotFoundException("SousTraitant", id);
        }
        sousTraitantRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public SousTraitantResponse findById(UUID id) {
        SousTraitant sousTraitant = sousTraitantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SousTraitant", id));
        return sousTraitantMapper.toResponse(sousTraitant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SousTraitantResponse> findAll() {
        return sousTraitantRepository.findAll().stream()
                .map(sousTraitantMapper::toResponse)
                .toList();
    }
}