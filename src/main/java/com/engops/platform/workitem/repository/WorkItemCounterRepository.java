package com.engops.platform.workitem.repository;

import com.engops.platform.workitem.model.WorkItemCounter;
import com.engops.platform.workitem.model.WorkItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Work item counter repository.
 * PESSIMISTIC_WRITE lock bilan concurrent-safe increment ta'minlaydi.
 */
@Repository
public interface WorkItemCounterRepository extends JpaRepository<WorkItemCounter, UUID> {

    /**
     * Counter qatorini PESSIMISTIC_WRITE lock bilan oladi.
     * Bu boshqa tranzaksiyalar shu qatorni o'qiy olmasligini ta'minlaydi.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkItemCounter> findByTenantIdAndTypeCode(UUID tenantId, WorkItemType typeCode);
}
