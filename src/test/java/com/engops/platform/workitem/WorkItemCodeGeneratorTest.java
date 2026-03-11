package com.engops.platform.workitem;

import com.engops.platform.workitem.model.WorkItemCounter;
import com.engops.platform.workitem.model.WorkItemType;
import com.engops.platform.workitem.repository.WorkItemCounterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkItemCodeGenerator unit testlari.
 * Counter asosida kod generatsiyasini tekshiradi.
 */
@ExtendWith(MockitoExtension.class)
class WorkItemCodeGeneratorTest {

    @Mock private WorkItemCounterRepository counterRepository;

    @InjectMocks
    private WorkItemCodeGenerator codeGenerator;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void birinchiMartaCounterYaratiladi() {
        when(counterRepository.findByTenantIdAndTypeCode(tenantId, WorkItemType.BUG))
                .thenReturn(Optional.empty());
        when(counterRepository.saveAndFlush(any(WorkItemCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(counterRepository.save(any(WorkItemCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String code = codeGenerator.generate(tenantId, WorkItemType.BUG);

        assertThat(code).isEqualTo("BUG-1");
        verify(counterRepository).saveAndFlush(any(WorkItemCounter.class));
    }

    @Test
    void concurrentYaratishdaRecovery() {
        // Birinchi o'qish — counter yo'q
        when(counterRepository.findByTenantIdAndTypeCode(tenantId, WorkItemType.BUG))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new WorkItemCounter(tenantId, WorkItemType.BUG)));

        // INSERT unique constraint bilan muvaffaqiyatsiz
        when(counterRepository.saveAndFlush(any(WorkItemCounter.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));
        when(counterRepository.save(any(WorkItemCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String code = codeGenerator.generate(tenantId, WorkItemType.BUG);

        // Recovery — mavjud counterni qayta o'qidi va BUG-1 generatsiya qildi
        assertThat(code).isEqualTo("BUG-1");
    }

    @Test
    void mavjudCounterdanKeyingiQiymat() {
        WorkItemCounter counter = new WorkItemCounter(tenantId, WorkItemType.TASK);
        for (int i = 0; i < 4; i++) {
            counter.incrementAndGet();
        }

        when(counterRepository.findByTenantIdAndTypeCode(tenantId, WorkItemType.TASK))
                .thenReturn(Optional.of(counter));
        when(counterRepository.save(any(WorkItemCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String code = codeGenerator.generate(tenantId, WorkItemType.TASK);

        assertThat(code).isEqualTo("TASK-5");
    }

    @Test
    void ketmaKetChaqiruvlarKetmaKetRaqam() {
        WorkItemCounter counter = new WorkItemCounter(tenantId, WorkItemType.INCIDENT);

        when(counterRepository.findByTenantIdAndTypeCode(tenantId, WorkItemType.INCIDENT))
                .thenReturn(Optional.of(counter));
        when(counterRepository.save(any(WorkItemCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String code1 = codeGenerator.generate(tenantId, WorkItemType.INCIDENT);
        String code2 = codeGenerator.generate(tenantId, WorkItemType.INCIDENT);
        String code3 = codeGenerator.generate(tenantId, WorkItemType.INCIDENT);

        assertThat(code1).isEqualTo("INCIDENT-1");
        assertThat(code2).isEqualTo("INCIDENT-2");
        assertThat(code3).isEqualTo("INCIDENT-3");
    }
}
