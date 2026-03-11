package com.engops.platform.workitem;

import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Work item kodi yaratuvchi.
 * Format: TYPE-RAQAM (masalan: BUG-1, TASK-42, INCIDENT-7)
 *
 * Hozirgi strategiya: tenant ichida shu turdagi mavjud itemlar soniga +1.
 * Bu oddiy va o'qilishi oson. Produkciyada ko'p concurrent yaratish bo'lsa,
 * DB sequence ga o'tish mumkin — lekin hozir bu yetarli.
 *
 * Unique constraint (tenant_id, work_item_code) bazada himoya qiladi.
 */
@Component
public class WorkItemCodeGenerator {

    private final WorkItemRepository workItemRepository;

    public WorkItemCodeGenerator(WorkItemRepository workItemRepository) {
        this.workItemRepository = workItemRepository;
    }

    /**
     * Yangi work item kodi generatsiya qiladi.
     *
     * @param tenantId tenant identifikatori
     * @param typeCode work item turi (BUG, TASK, INCIDENT)
     * @return masalan "BUG-1", "TASK-5"
     */
    public String generate(UUID tenantId, WorkItemType typeCode) {
        long count = workItemRepository.countByTenantIdAndTypeCode(tenantId, typeCode);
        return typeCode.name() + "-" + (count + 1);
    }
}
