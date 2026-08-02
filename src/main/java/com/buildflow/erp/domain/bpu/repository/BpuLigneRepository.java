package com.buildflow.erp.domain.bpu.repository;

import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BpuLigneRepository extends JpaRepository<BpuLigne, UUID> {

    List<BpuLigne> findByChantierId(UUID chantierId);

    boolean existsByChantierIdAndRef(UUID chantierId, String ref);

    boolean existsByChantierIdAndRefAndIdNot(UUID chantierId, String ref, UUID id);
}
