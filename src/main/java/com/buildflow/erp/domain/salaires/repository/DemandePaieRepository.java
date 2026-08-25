package com.buildflow.erp.domain.salaires.repository;

import com.buildflow.erp.domain.salaires.entity.DemandePaie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DemandePaieRepository extends JpaRepository<DemandePaie, UUID> {

    List<DemandePaie> findByPeriodeOrderByCreatedAtDesc(String periode);

    List<DemandePaie> findAllByOrderByCreatedAtDesc();

    long countByChantierId(UUID chantierId);
}
