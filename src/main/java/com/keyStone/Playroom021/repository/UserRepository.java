package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.keyStone.Playroom021.entity.Role;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCustomerId(Long customerId);
    List<User> findByRole(Role role);
}
