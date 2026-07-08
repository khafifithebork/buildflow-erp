package com.buildflow.erp.domain.achats.repository;

import com.buildflow.erp.domain.achats.entity.Achat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AchatRepository extends JpaRepository<Achat, UUID> {
    boolean existsByRef(String ref);
}