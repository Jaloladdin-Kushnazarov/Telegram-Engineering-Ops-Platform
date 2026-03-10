package com.engops.platform.identity.repository;

import com.engops.platform.identity.model.MembershipRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * A'zolik-rol bog'lanishi uchun repository.
 */
@Repository
public interface MembershipRoleBindingRepository extends JpaRepository<MembershipRoleBinding, UUID> {

    List<MembershipRoleBinding> findByMembershipId(UUID membershipId);

    boolean existsByMembershipIdAndRoleId(UUID membershipId, UUID roleId);
}
