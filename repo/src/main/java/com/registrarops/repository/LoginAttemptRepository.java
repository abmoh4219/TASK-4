package com.registrarops.repository;

import com.registrarops.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("SELECT COUNT(la) FROM LoginAttempt la WHERE la.username = :username AND la.attemptedAt >= :since")
    long countRecentByUsername(@Param("username") String username, @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM LoginAttempt la WHERE la.username = :username")
    void deleteByUsername(@Param("username") String username);
}
