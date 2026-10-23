package com.autoscientist;

import com.autoscientist.dto.dataset.WorkflowStatusDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import com.autoscientist.repository.*;
import com.autoscientist.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkflowServiceTest {

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DataQualityReportRepository dataQualityReportRepository;
    @Mock
    private AssociationRepository associationRepository;
    @Mock
    private ResearchQuestionRepository researchQuestionRepository;
    @Mock
    private StatisticalTestRepository statisticalTestRepository;
    @Mock
    private ExperimentRepository experimentRepository;
    @Mock
    private ExperimentVersionRepository experimentVersionRepository;
    @Mock
    private ModelRunRepository modelRunRepository;
    @Mock
    private FindingRepository findingRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AnalysisRunRepository analysisRunRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private User testUser;
    private Dataset testDataset;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).email("scientist@autoscientist.com").name("Lead Scientist").build();
        testDataset = Dataset.builder()
                .id(100L)
                .name("Cancer Biomarkers")
                .rowCount(500)
                .columnCount(12)
                .owner(testUser)
                .build();
        testDataset.setColumns(java.util.Arrays.asList(
                com.autoscientist.entity.DatasetColumn.builder()
                        .columnIndex(0).columnName("age").dataType("NUMERIC").uniqueCount(40).build(),
                com.autoscientist.entity.DatasetColumn.builder()
                        .columnIndex(1).columnName("stage").dataType("CATEGORICAL").uniqueCount(4).build(),
                com.autoscientist.entity.DatasetColumn.builder()
                        .columnIndex(2).columnName("patient_id").dataType("IDENTIFIER").uniqueCount(500).build()
        ));
    }

    @Test
    void testWorkflowStatusStagesAndPrerequisites() {
        when(datasetRepository.findByIdAndOwner(eq(100L), any(User.class))).thenReturn(Optional.of(testDataset));
        when(dataQualityReportRepository.findByDataset(any(Dataset.class))).thenReturn(Optional.empty());
        when(associationRepository.findByDatasetOrderByRankScoreDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(statisticalTestRepository.findByDatasetOrderByCreatedAtDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(experimentRepository.findByDatasetOrderByCreatedAtDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(findingRepository.findByDatasetOrderByCreatedAtDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(reportRepository.findByDatasetOrderByCreatedAtDesc(any(Dataset.class))).thenReturn(Collections.emptyList());
        when(analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(any())).thenReturn(Collections.emptyList());

        WorkflowStatusDto status = workflowService.getWorkflowStatus(100L, testUser);

        assertNotNull(status);
        assertEquals(100L, status.getDatasetId());
        assertEquals("Cancer Biomarkers", status.getDatasetName());
        assertEquals(13, status.getStages().size());

        // Upload and Overview should be complete
        assertTrue(status.getStages().get("upload").isCompleted());
        assertTrue(status.getStages().get("overview").isCompleted());

        // Quality, Associations, Hypotheses, etc. should be incomplete
        assertFalse(status.getStages().get("quality").isCompleted());
        assertFalse(status.getStages().get("associations").isCompleted());
        assertFalse(status.getStages().get("research").isCompleted());
        assertFalse(status.getStages().get("ml").isCompleted());

        // Verify prerequisite messages
        assertFalse(status.getStages().get("hypotheses").isPrerequisitesMet());
        assertEquals("Hypotheses require at least one formulated research question.",
                status.getStages().get("hypotheses").getPrerequisiteMessage());

        assertFalse(status.getStages().get("xai").isPrerequisitesMet());
        assertEquals("Model explanation is unavailable because no model has been trained.",
                status.getStages().get("xai").getPrerequisiteMessage());
    }
}
