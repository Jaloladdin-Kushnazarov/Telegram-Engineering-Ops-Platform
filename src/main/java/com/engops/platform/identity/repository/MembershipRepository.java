package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A'zolik (Membership) uchun repository.
 * Barcha so'rovlar tenant-scoped.
 */
@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Membership> findByTenantIdAndStatus(UUID tenantId, com.engops.platform.identity.model.MembershipStatus status);

    List<Membership> findByTenantId(UUID tenantId);

    List<Membership> findByUserId(UUID userId);
}
