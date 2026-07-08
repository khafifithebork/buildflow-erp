package com.buildflow.erp.domain.stock.repository;

import com.buildflow.erp.domain.stock.entity.MouvementStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, UUID> {
}