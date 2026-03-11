package com.engops.platform.workitem;

import com.engops.platform.workitem.model.WorkItemCounter;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemCounterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Work item kodi yaratuvchi.
 * Format: TYPE-RAQAM (masalan: BUG-1, TASK-42, INCIDENT-7)
 *
 * Strategiya: work_item_counter jadvalida tenant+type bo'yicha counter saqlanadi.
 * PESSIMISTIC_WRITE lock bilan bir vaqtda faqat bitta tranzaksiya counterni o'qiydi.
 *
 * Birinchi yaratishda race condition:
 * Ikki so'rov bir vaqtda counter yo'qligini ko'rishi va ikkalasi ham INSERT qilishga
 * harakat qilishi mumkin. Buni hal qilish uchun: agar INSERT unique constraint
 * bilan muvaffaqiyatsiz bo'lsa, qayta o'qiymiz — boshqa tranzaksiya allaqachon
 * yaratgan counter'ni olamiz.
 *
 * Trade-off: bitta tenant+type uchun parallel yaratish serialized bo'ladi.
 * MVP uchun bu to'g'ri — chunki kod yagona bo'lishi SHARTdir.
 */
@Component
public class WorkItemCodeGenerator {

    private final WorkItemCounterRepository counterRepository;

    public WorkItemCodeGenerator(WorkItemCounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }

    /**
     * Yangi work item kodi generatsiya qiladi.
     * Counter topilmasa, yangi counter yaratadi.
     * Agar concurrent INSERT bo'lsa, qayta o'qiydi (race-safe).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String generate(UUID tenantId, WorkItemType typeCode) {
        WorkItemCounter counter = counterRepository
                .findByTenantIdAndTypeCode(tenantId, typeCode)
                .orElseGet(() -> createCounter(tenantId, typeCode));

        long value = counter.incrementAndGet();
        counterRepository.save(counter);

        return typeCode.name() + "-" + value;
    }

    /**
     * Yangi counter yaratadi. Agar boshqa tranzaksiya allaqachon yaratgan bo'lsa
     * (unique constraint violation), mavjud counterni qayta o'qiydi.
     */
    private WorkItemCounter createCounter(UUID tenantId, WorkItemType typeCode) {
        try {
            WorkItemCounter newCounter = new WorkItemCounter(tenantId, typeCode);
            counterRepository.saveAndFlush(newCounter);
            return newCounter;
        } catch (DataIntegrityViolationException e) {
            // Boshqa tranzaksiya allaqachon yaratgan — qayta o'qiymiz (lock bilan)
            return counterRepository.findByTenantIdAndTypeCode(tenantId, typeCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "Counter yaratish va o'qish ikkalasi ham muvaffaqiyatsiz bo'ldi"));
        }
    }
}
