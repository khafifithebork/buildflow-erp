package com.buildflow.erp.domain.tresorerie.repository;

import com.buildflow.erp.domain.tresorerie.entity.Caisse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaisseRepository extends JpaRepository<Caisse, UUID> {
    Optional<Caisse> findByCode(String code);
    Optional<Caisse> findByChantierId(UUID chantierId);
    boolean existsByCode(String code);
}
