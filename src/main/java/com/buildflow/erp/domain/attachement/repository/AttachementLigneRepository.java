package com.buildflow.erp.domain.attachement.repository;

import com.buildflow.erp.domain.attachement.entity.AttachementLigne;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttachementLigneRepository extends JpaRepository<AttachementLigne, UUID> {

    // Most recently created attachement line for a given BPU line — its
    // nouveauCumul is the "ancien cumul" (starting point) for the next décompte.
    Optional<AttachementLigne> findFirstByBpuLigne_IdOrderByCreatedAtDesc(UUID bpuLigneId);
}
