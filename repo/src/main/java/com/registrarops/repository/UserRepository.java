package com.registrarops.repository;

import com.registrarops.entity.Role;
import com.registrarops.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRole(Role role);

    List<User> findByIsActiveTrueAndDeletedAtIsNull();
}
