package com.engops.platform.tenantconfig;

import com.engops.platform.tenantconfig.model.WorkflowTemplate;
import com.engops.platform.tenantconfig.model.WorkflowTemplateStatus;
import com.engops.platform.tenantconfig.model.WorkflowTemplateTransition;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateStatusRepository;
import com.engops.platform.tenantconfig.repository.WorkflowTemplateTransitionRepository;
import com.engops.platform.workitem.model.WorkItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * WorkflowTemplateQueryService unit testlari. Repositoriyalar mock'lanadi.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTemplateQueryServiceTest {

    @Mock private WorkflowTemplateRepository workflowTemplateRepository;
    @Mock private WorkflowTemplateStatusRepository workflowTemplateStatusRepository;
    @Mock private WorkflowTemplateTransitionRepository workflowTemplateTransitionRepository;

    @InjectMocks
    private WorkflowTemplateQueryService queryService;

    @Test
    void listAll_repositoriyAlfavitTartibdaQaytaradi() {
        WorkflowTemplate bugMin = new WorkflowTemplate("BUG_MINIMAL", "Bug Min", WorkItemType.BUG, null);
        WorkflowTemplate taskBasic = new WorkflowTemplate("TASK_BASIC", "Task Basic", WorkItemType.TASK, null);
        when(workflowTemplateRepository.findAllByOrderByCodeAsc())
                .thenReturn(List.of(bugMin, taskBasic));

        List<WorkflowTemplate> result = queryService.listAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCode()).isEqualTo("BUG_MINIMAL");
        assertThat(result.get(1).getCode()).isEqualTo("TASK_BASIC");
    }

    @Test
    void findByCode_mavjudCodeUchunShablonniQaytaradi() {
        WorkflowTemplate incident = new WorkflowTemplate(
                "INCIDENT_BASIC", "Incident Basic", WorkItemType.INCIDENT, "desc");
        when(workflowTemplateRepository.findByCode("INCIDENT_BASIC"))
                .thenReturn(Optional.of(incident));

        Optional<WorkflowTemplate> result = queryService.findByCode("INCIDENT_BASIC");

        assertThat(result).isPresent();
        assertThat(result.get().getWorkItemType()).isEqualTo(WorkItemType.INCIDENT);
        assertThat(result.get().getDescription()).isEqualTo("desc");
    }

    @Test
    void findByCode_nomalumCodeUchunEmptyQaytaradi() {
        when(workflowTemplateRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        Optional<WorkflowTemplate> result = queryService.findByCode("UNKNOWN");

        assertThat(result).isEmpty();
    }

    @Test
    void listStatuses_statusOrderBoYichaTartiblangan() {
        UUID templateId = UUID.randomUUID();
        WorkflowTemplate template = new WorkflowTemplate(
                "BUG_MINIMAL", "Bug Min", WorkItemType.BUG, null);
        WorkflowTemplateStatus s1 = new WorkflowTemplateStatus(template, "BUGS", "Bugs", true, 1);
        WorkflowTemplateStatus s2 = new WorkflowTemplateStatus(template, "PROCESSING", "Processing", false, 2);
        when(workflowTemplateStatusRepository
                .findAllByTemplate_IdOrderByStatusOrderAsc(templateId))
                .thenReturn(List.of(s1, s2));

        List<WorkflowTemplateStatus> result = queryService.listStatuses(templateId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatusCode()).isEqualTo("BUGS");
        assertThat(result.get(0).isInitial()).isTrue();
        assertThat(result.get(1).getStatusCode()).isEqualTo("PROCESSING");
    }

    @Test
    void listTransitions_fromToBoYichaTartiblangan() {
        UUID templateId = UUID.randomUUID();
        WorkflowTemplate template = new WorkflowTemplate(
                "BUG_MINIMAL", "Bug Min", WorkItemType.BUG, null);
        WorkflowTemplateTransition t1 = new WorkflowTemplateTransition(
                template, "BUGS", "PROCESSING", "Start");
        WorkflowTemplateTransition t2 = new WorkflowTemplateTransition(
                template, "TESTING", "FIXED", "Mark Fixed");
        when(workflowTemplateTransitionRepository
                .findAllByTemplate_IdOrderByFromStatusCodeAscToStatusCodeAsc(templateId))
                .thenReturn(List.of(t1, t2));

        List<WorkflowTemplateTransition> result = queryService.listTransitions(templateId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFromStatusCode()).isEqualTo("BUGS");
        assertThat(result.get(0).getActionLabel()).isEqualTo("Start");
        assertThat(result.get(1).getFromStatusCode()).isEqualTo("TESTING");
    }
}
