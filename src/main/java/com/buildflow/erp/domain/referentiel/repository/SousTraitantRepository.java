package com.buildflow.erp.domain.referentiel.repository;

import com.buildflow.erp.domain.referentiel.entity.SousTraitant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SousTraitantRepository extends JpaRepository<SousTraitant, UUID> {
    boolean existsByCode(String code);
    boolean existsByIce(String ice);
}