package com.buildflow.erp.domain.soustraitance.repository;

import com.buildflow.erp.domain.soustraitance.entity.PaiementSousTraitant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaiementSousTraitantRepository extends JpaRepository<PaiementSousTraitant, UUID> {
    boolean existsByReference(String reference);
    List<PaiementSousTraitant> findByContratIdOrderByCreatedAtDesc(UUID contratId);
}
