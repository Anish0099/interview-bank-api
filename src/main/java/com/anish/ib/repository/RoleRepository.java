package com.anish.ib.repository;

import com.anish.ib.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findBySlug(String slug);
}
