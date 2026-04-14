package com.registrarops.repository;

import com.registrarops.entity.EvidenceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceAttachmentRepository extends JpaRepository<EvidenceAttachment, Long> {
    List<EvidenceAttachment> findByCycleId(Long cycleId);
}
