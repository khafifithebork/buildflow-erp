package com.buildflow.erp.domain.salaires.repository;

import com.buildflow.erp.domain.salaires.entity.DemandePaie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface DemandePaieRepository extends JpaRepository<DemandePaie, UUID> {

    List<DemandePaie> findByPeriodeOrderByCreatedAtDesc(String periode);

    List<DemandePaie> findAllByOrderByCreatedAtDesc();

    long countByChantierId(UUID chantierId);

    /**
     * Ce qu'une ligne du bordereau a consomme en demandes de paie.
     *
     * <p>Le formulaire laissait choisir une ligne BPU depuis la creation de la
     * fonctionnalite, et personne ne la lisait : le champ etait decoratif et le
     * bordereau sous-estimait d'autant la paie imputee.
     *
     * <p>Meme seuil que {@code FichePaieRepository.sumMontantEngageByBpuLigneId}
     * — seules les demandes payees comptent. Les deux documents de paie se
     * lisent donc pareil ; si ce seuil change un jour, il changera pour les
     * deux ensemble.
     */
    @Query("""
            SELECT COALESCE(SUM(d.montantNet), 0) FROM DemandePaie d
            WHERE d.bpuLigne.id = :bpuLigneId
            AND d.statut = com.buildflow.erp.domain.salaires.entity.DemandePaieStatut.PAYEE
            """)
    BigDecimal sumMontantEngageByBpuLigneId(@Param("bpuLigneId") UUID bpuLigneId);
}
