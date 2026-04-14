package com.registrarops.repository;

import com.registrarops.entity.DeviceBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, Long> {
    List<DeviceBinding> findByUserId(Long userId);
    boolean existsByUserIdAndDeviceHash(Long userId, String deviceHash);
}
