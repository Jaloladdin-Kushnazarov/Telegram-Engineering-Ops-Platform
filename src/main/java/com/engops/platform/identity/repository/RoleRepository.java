package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Rol uchun repository.
 * Rollar global katalog — tenant'ga bog'liq emas.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}
