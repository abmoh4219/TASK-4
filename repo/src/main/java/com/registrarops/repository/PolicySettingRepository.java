package com.registrarops.repository;

import com.registrarops.entity.PolicySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicySettingRepository extends JpaRepository<PolicySetting, String> {
}
