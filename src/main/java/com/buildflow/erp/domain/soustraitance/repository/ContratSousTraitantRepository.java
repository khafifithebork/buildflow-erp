package com.buildflow.erp.domain.soustraitance.repository;

import com.buildflow.erp.domain.soustraitance.entity.ContratSousTraitant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContratSousTraitantRepository extends JpaRepository<ContratSousTraitant, UUID> {
    boolean existsByReference(String reference);
    List<ContratSousTraitant> findByChantierId(UUID chantierId);
    List<ContratSousTraitant> findBySousTraitantId(UUID sousTraitantId);
}
