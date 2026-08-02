package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateFournisseurRequest;
import com.buildflow.erp.domain.referentiel.dto.response.FournisseurResponse;
import com.buildflow.erp.domain.referentiel.entity.Fournisseur;
import com.buildflow.erp.domain.referentiel.mapper.FournisseurMapper;
import com.buildflow.erp.domain.referentiel.repository.FournisseurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FournisseurServiceImpl implements FournisseurService {

    private final FournisseurRepository fournisseurRepository;
    private final FournisseurMapper fournisseurMapper;

    @Override
    @Transactional
    public FournisseurResponse create(CreateFournisseurRequest request) {
        if (fournisseurRepository.existsByCode(request.code())) {
            throw new ConflictException("A fournisseur with code '" + request.code() + "' already exists");
        }
        Fournisseur fournisseur = fournisseurMapper.toEntity(request);
        Fournisseur saved = fournisseurRepository.save(fournisseur);
        return fournisseurMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public FournisseurResponse update(UUID id, CreateFournisseurRequest request) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));

        if (fournisseurRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ConflictException("A fournisseur with code '" + request.code() + "' already exists");
        }

        fournisseurMapper.updateEntityFromRequest(request, fournisseur);

        return fournisseurMapper.toResponse(fournisseurRepository.save(fournisseur));
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
        return fournisseurMapper.toResponse(fournisseur);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FournisseurResponse> findAll() {
        return fournisseurRepository.findAll().stream()
                .map(fournisseurMapper::toResponse)
                .toList();
    }
}