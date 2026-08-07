package com.buildflow.erp.domain.referentiel.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.referentiel.dto.request.CreateEmployeRequest;
import com.buildflow.erp.domain.referentiel.dto.response.EmployeResponse;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.entity.Employe;
import com.buildflow.erp.domain.referentiel.mapper.EmployeMapper;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import com.buildflow.erp.domain.referentiel.repository.EmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeServiceImpl implements EmployeService {

    private final EmployeRepository employeRepository;
    private final ChantierRepository chantierRepository;
    private final EmployeMapper employeMapper;
    private final CodeGenerator codeGenerator;

    @Override
    @Transactional
    public EmployeResponse create(CreateEmployeRequest request) {
        Employe employe = employeMapper.toEntity(request);
        employe.setMatricule(codeGenerator.next(CodeSequence.EMPLOYE));

        if (request.chantierActuelId() != null) {
            Chantier chantier = chantierRepository.findById(request.chantierActuelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier", request.chantierActuelId()));
            employe.setChantierActuel(chantier);
        }

        Employe saved = employeRepository.save(employe);
        return employeMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeResponse findById(UUID id) {
        Employe employe = employeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employe", id));
        return employeMapper.toResponse(employe);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeResponse> findAll() {
        return employeRepository.findAll().stream()
                .map(employeMapper::toResponse)
                .toList();
    }
}