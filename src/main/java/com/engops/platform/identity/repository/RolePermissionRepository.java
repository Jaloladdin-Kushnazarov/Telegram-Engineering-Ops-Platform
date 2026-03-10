package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Rol-ruxsat bog'lanishi uchun repository.
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleId(UUID roleId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
