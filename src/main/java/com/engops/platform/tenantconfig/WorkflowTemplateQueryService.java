package com.engops.platform.tenantconfig;

import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateStatusRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateTransitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 198 — global workflow template katalogi uchun read-only query servisi.
 *
 * Tenant-agnostic: katalog tizim darajasidagi shablonlar to'plami,
 * tenant filter shart emas. Phase 199 onboarding endpoint shu servis orqali
 * shablonni o'qib har bir yangi tenant uchun workflow_definition +
 * workflow_status + workflow_transition_rule qatorlarini yaratadi.
 */
@Service
@Transactional(readOnly = true)
public class WorkflowTemplateQueryService {

    private final WorkflowTemplateRepository workflowTemplateRepository;
    private final WorkflowTemplateStatusRepository workflowTemplateStatusRepository;
    private final WorkflowTemplateTransitionRepository workflowTemplateTransitionRepository;

    public WorkflowTemplateQueryService(
            WorkflowTemplateRepository workflowTemplateRepository,
            WorkflowTemplateStatusRepository workflowTemplateStatusRepository,
            WorkflowTemplateTransitionRepository workflowTemplateTransitionRepository) {
        this.workflowTemplateRepository = workflowTemplateRepository;
        this.workflowTemplateStatusRepository = workflowTemplateStatusRepository;
        this.workflowTemplateTransitionRepository = workflowTemplateTransitionRepository;
    }

    /**
     * Barcha shablonlarni code bo'yicha alfavit tartibda qaytaradi.
     */
    public List<WorkflowTemplate> listAll() {
        return workflowTemplateRepository.findAllByOrderByCodeAsc();
    }

    /**
     * Code (masalan: 'BUG_MINIMAL') bo'yicha shablonni topadi.
     * Mavjud bo'lmasa — {@link Optional#empty()}.
     */
    public Optional<WorkflowTemplate> findByCode(String code) {
        return workflowTemplateRepository.findByCode(code);
    }

    /**
     * Shablon ichidagi statuslarni status_order bo'yicha tartiblangan ro'yxat.
     */
    public List<WorkflowTemplateStatus> listStatuses(UUID templateId) {
        return workflowTemplateStatusRepository.findAllByTemplate_IdOrderByStatusOrderAsc(templateId);
    }

    /**
     * Shablon ichidagi tranziyalarni from → to bo'yicha barqaror tartibda qaytaradi.
     */
    public List<WorkflowTemplateTransition> listTransitions(UUID templateId) {
        return workflowTemplateTransitionRepository
                .findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(templateId);
    }
}
