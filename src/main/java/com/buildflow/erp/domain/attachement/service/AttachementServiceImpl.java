package com.buildflow.erp.domain.attachement.service;

import com.buildflow.erp.common.code.CodeGenerator;
import com.buildflow.erp.common.code.CodeSequence;
import com.buildflow.erp.common.exception.BusinessRuleException;
import com.buildflow.erp.common.exception.ResourceNotFoundException;
import com.buildflow.erp.domain.attachement.dto.request.CreateAttachementLigneRequest;
import com.buildflow.erp.domain.attachement.dto.request.CreateAttachementRequest;
import com.buildflow.erp.domain.attachement.dto.response.AttachementLigneResponse;
import com.buildflow.erp.domain.attachement.dto.response.AttachementResponse;
import com.buildflow.erp.domain.attachement.entity.Attachement;
import com.buildflow.erp.domain.attachement.entity.AttachementLigne;
import com.buildflow.erp.domain.attachement.entity.AttachementStatut;
import com.buildflow.erp.domain.attachement.repository.AttachementLigneRepository;
import com.buildflow.erp.domain.attachement.repository.AttachementRepository;
import com.buildflow.erp.domain.bpu.entity.BpuLigne;
import com.buildflow.erp.domain.bpu.repository.BpuLigneRepository;
import com.buildflow.erp.domain.referentiel.entity.Chantier;
import com.buildflow.erp.domain.referentiel.repository.ChantierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachementServiceImpl implements AttachementService {

    private final AttachementRepository attachementRepository;
    private final AttachementLigneRepository attachementLigneRepository;
    private final BpuLigneRepository bpuLigneRepository;
    private final ChantierRepository chantierRepository;
    private final CodeGenerator codeGenerator;

    private static final BigDecimal TVA_RATE = new BigDecimal("0.20");

    @Override
    @Transactional
    public AttachementResponse create(UUID chantierId, CreateAttachementRequest request) {
        Chantier chantier = chantierRepository.findById(chantierId)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier", chantierId));

        Attachement attachement = new Attachement();
        attachement.setChantier(chantier);
        attachement.setReference(codeGenerator.next(CodeSequence.ATTACHEMENT));
        attachement.setDateAttachement(request.dateAttachement());
        attachement.setStatut(AttachementStatut.SOUMIS);

        BigDecimal montantHt = BigDecimal.ZERO;

        for (CreateAttachementLigneRequest ligneRequest : request.lignes()) {
            BpuLigne bpuLigne = bpuLigneRepository.findById(ligneRequest.bpuLigneId())
                    .orElseThrow(() -> new ResourceNotFoundException("BpuLigne", ligneRequest.bpuLigneId()));

            if (!bpuLigne.getChantier().getId().equals(chantierId)) {
                throw new ResourceNotFoundException("BpuLigne", ligneRequest.bpuLigneId());
            }

            BigDecimal ancienCumul = attachementLigneRepository
                    .findFirstByBpuLigne_IdOrderByCreatedAtDesc(bpuLigne.getId())
                    .map(AttachementLigne::getNouveauCumul)
                    .orElse(BigDecimal.ZERO);

            BigDecimal nouveauCumul = ligneRequest.nouveauCumul();

            if (nouveauCumul.compareTo(ancienCumul) < 0) {
                throw new BusinessRuleException(
                        "Le nouveau cumul de la ligne BPU '" + bpuLigne.getRef()
                                + "' ne peut pas être inférieur à l'ancien cumul (" + ancienCumul + ")");
            }
            if (nouveauCumul.compareTo(bpuLigne.getQtePrevue()) > 0) {
                throw new BusinessRuleException(
                        "Le nouveau cumul de la ligne BPU '" + bpuLigne.getRef()
                                + "' dépasse la quantité prévue (" + bpuLigne.getQtePrevue() + ")");
            }

            BigDecimal ligneMontantHt = nouveauCumul.subtract(ancienCumul)
                    .multiply(BigDecimal.valueOf(bpuLigne.getPuHt()))
                    .setScale(2, RoundingMode.HALF_UP);

            AttachementLigne ligne = new AttachementLigne();
            ligne.setAttachement(attachement);
            ligne.setBpuLigne(bpuLigne);
            ligne.setAncienCumul(ancienCumul);
            ligne.setNouveauCumul(nouveauCumul);
            ligne.setMontantHt(ligneMontantHt);
            attachement.getLignes().add(ligne);

            montantHt = montantHt.add(ligneMontantHt);
        }

        BigDecimal tva = montantHt.multiply(TVA_RATE).setScale(2, RoundingMode.HALF_UP);
        attachement.setMontantHt(montantHt);
        attachement.setTva(tva);
        attachement.setMontantTtc(montantHt.add(tva));

        return toResponse(attachementRepository.save(attachement));
    }

    @Override
    @Transactional
    public AttachementResponse encaisser(UUID id) {
        Attachement attachement = attachementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachement", id));

        if (attachement.getStatut() == AttachementStatut.ENCAISSE) {
            throw new BusinessRuleException("Cet attachement est déjà encaissé");
        }

        attachement.setStatut(AttachementStatut.ENCAISSE);
        attachement.setDateEncaissement(LocalDateTime.now());

        return toResponse(attachementRepository.save(attachement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachementResponse> findByChantier(UUID chantierId) {
        if (!chantierRepository.existsById(chantierId)) {
            throw new ResourceNotFoundException("Chantier", chantierId);
        }
        return attachementRepository.findByChantierIdOrderByDateAttachementDesc(chantierId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AttachementResponse toResponse(Attachement attachement) {
        List<AttachementLigneResponse> lignes = attachement.getLignes().stream()
                .map(l -> new AttachementLigneResponse(
                        l.getId(),
                        l.getBpuLigne().getId(),
                        l.getBpuLigne().getRef(),
                        l.getBpuLigne().getDesignation(),
                        l.getAncienCumul(),
                        l.getNouveauCumul(),
                        l.getBpuLigne().getPuHt(),
                        l.getMontantHt()))
                .toList();

        return new AttachementResponse(
                attachement.getId(),
                attachement.getChantier().getId(),
                attachement.getChantier().getNom(),
                attachement.getReference(),
                attachement.getDateAttachement(),
                attachement.getMontantHt(),
                attachement.getTva(),
                attachement.getMontantTtc(),
                attachement.getStatut(),
                attachement.getDateEncaissement(),
                lignes);
    }
}
