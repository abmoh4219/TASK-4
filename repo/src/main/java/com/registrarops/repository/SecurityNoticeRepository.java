package com.registrarops.repository;

import com.registrarops.entity.SecurityNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityNoticeRepository extends JpaRepository<SecurityNotice, Long> {
    List<SecurityNotice> findByUserIdOrderByCreatedAtDesc(Long userId);
}
