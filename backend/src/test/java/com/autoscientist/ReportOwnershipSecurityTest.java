package com.autoscientist;

import com.autoscientist.analysis.report.PdfExportService;
import com.autoscientist.analysis.report.ReportExportService;
import com.autoscientist.analysis.report.ReportGeneratorService;
import com.autoscientist.dto.report.GenerateReportRequest;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.ResearchQuestion;
import com.autoscientist.entity.User;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.*;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportOwnershipSecurityTest {

    @Mock private DatasetService datasetService;
    @Mock private ReportRepository reportRepository;
    @Mock private DataQualityReportRepository dataQualityReportRepository;
    @Mock private AssociationRepository associationRepository;
    @Mock private ResearchQuestionRepository researchQuestionRepository;
    @Mock private HypothesisRepository hypothesisRepository;
    @Mock private StatisticalTestRepository statisticalTestRepository;
    @Mock private ExperimentRepository experimentRepository;
    @Mock private ModelRunRepository modelRunRepository;
    @Mock private FindingRepository findingRepository;
    @Mock private DriftSnapshotRepository driftSnapshotRepository;
    @Mock private ReportGeneratorService reportGeneratorService;
    @Mock private PdfExportService pdfExportService;
    @Mock private ReportExportService reportExportService;

    @InjectMocks private ReportService reportService;

    private User requester;
    private User otherUser;
    private Dataset dataset;
    private Dataset otherDataset;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).email("requester@autoscientist.com").name("Requester").build();
        otherUser = User.builder().id(2L).email("other@autoscientist.com").name("Other").build();
        dataset = Dataset.builder().id(100L).name("Requester Dataset").owner(requester).build();
        otherDataset = Dataset.builder().id(200L).name("Other Dataset").owner(otherUser).build();
    }

    @Test
    @DisplayName("Report generation rejects another user's research question")
    void rejectsResearchQuestionFromAnotherUser() {
        when(datasetService.getDatasetEntityWithOwnership(100L, requester)).thenReturn(dataset);
        ResearchQuestion foreign = ResearchQuestion.builder()
                .id(500L).dataset(otherDataset).user(otherUser).build();
        when(researchQuestionRepository.findById(500L)).thenReturn(Optional.of(foreign));

        GenerateReportRequest request = GenerateReportRequest.builder().researchQuestionId(500L).build();

        assertThatThrownBy(() -> reportService.generateReport(100L, request, requester))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Research question not found for this dataset");
    }

    @Test
    @DisplayName("Report generation rejects a research question from a different dataset")
    void rejectsResearchQuestionFromAnotherDataset() {
        when(datasetService.getDatasetEntityWithOwnership(100L, requester)).thenReturn(dataset);
        ResearchQuestion mismatched = ResearchQuestion.builder()
                .id(501L).dataset(otherDataset).user(requester).build();
        when(researchQuestionRepository.findById(501L)).thenReturn(Optional.of(mismatched));

        GenerateReportRequest request = GenerateReportRequest.builder().researchQuestionId(501L).build();

        assertThatThrownBy(() -> reportService.generateReport(100L, request, requester))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Research question not found for this dataset");
    }
}
