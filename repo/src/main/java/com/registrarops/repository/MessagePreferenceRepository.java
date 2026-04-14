package com.registrarops.repository;

import com.registrarops.entity.MessagePreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessagePreferenceRepository extends JpaRepository<MessagePreference, Long> {
    Optional<MessagePreference> findByUserId(Long userId);
}
