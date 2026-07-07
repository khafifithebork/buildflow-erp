package com.buildflow.erp.domain.referentiel.repository;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ChantierRepository extends JpaRepository<Chantier, UUID> {
    boolean existsByCode(String code);
}