package com.autoscientist;

import com.autoscientist.dto.dashboard.ActivityBucketDto;
import com.autoscientist.dto.dashboard.ActivitySeriesDto;
import com.autoscientist.dto.dashboard.DashboardStatsDto;
import com.autoscientist.entity.*;
import com.autoscientist.repository.*;
import com.autoscientist.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardRegressionTest {

    @Mock
    private DatasetRepository datasetRepository;
    @Mock
    private DataQualityReportRepository dataQualityReportRepository;
    @Mock
    private AssociationRepository associationRepository;
    @Mock
    private ResearchQuestionRepository researchQuestionRepository;
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

    @InjectMocks
    private DashboardService dashboardService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(10L).name("Dr. Ada Lovelace").email("ada@science.org").build();
    }

    @Test
    @DisplayName("DashboardStats: Computes DB-derived aggregates and builds activity series")
    void testDashboardStatsAndActivitySeries() {
        Dataset ds1 = Dataset.builder()
                .id(1L).name("Cohort 1").rowCount(500).columnCount(10)
                .datasetType("TABULAR").createdAt(LocalDateTime.now().minusDays(3))
                .owner(user).build();
        Dataset ds2 = Dataset.builder()
                .id(2L).name("Cohort 2").rowCount(250).columnCount(8)
                .datasetType("TABULAR").createdAt(LocalDateTime.now().minusDays(1))
                .owner(user).build();

        when(datasetRepository.findByOwnerOrderByCreatedAtDesc(user)).thenReturn(List.of(ds2, ds1));

        DataQualityReport qr1 = DataQualityReport.builder().dataset(ds1).overallScore(94.0).build();
        when(dataQualityReportRepository.findByOwner(user)).thenReturn(List.of(qr1));

        when(associationRepository.countByOwner(user)).thenReturn(12L);
        when(findingRepository.countByOwner(user)).thenReturn(4L);
        when(modelRunRepository.countByOwner(user)).thenReturn(6L);
        when(experimentRepository.countByOwner(user)).thenReturn(2L);

        ResearchQuestion rq = ResearchQuestion.builder()
                .id(1L).dataset(ds1).questionText("Does X correlate with Y?")
                .createdAt(LocalDateTime.now().minusDays(2)).build();
        when(researchQuestionRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(rq));

        Experiment exp = Experiment.builder()
                .id(1L).dataset(ds1).name("AutoML Exp 1")
                .targetColumn("Y").problemType("CLASSIFICATION")
                .createdAt(LocalDateTime.now().minusDays(1)).build();
        when(experimentRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(exp));

        Finding finding = Finding.builder()
                .id(1L).dataset(ds1).title("Significant association")
                .confidenceLevel("HIGH").createdAt(LocalDateTime.now().minusDays(1)).build();
        when(findingRepository.findAllByOwner(user)).thenReturn(List.of(finding));

        Report rep = Report.builder()
                .id(1L).title("Final Research Report")
                .dataset(ds1)
                .createdAt(LocalDateTime.now()).build();
        when(reportRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(rep));

        DashboardStatsDto stats = dashboardService.getDashboardStats(user);

        assertNotNull(stats);
        assertEquals(2, stats.getTotalDatasets());
        assertEquals(750, stats.getTotalObservations());
        assertEquals(12, stats.getTotalAssociations());
        assertEquals(4, stats.getTotalFindings());
        assertEquals(6, stats.getTotalModelsTrained());
        assertEquals(2, stats.getTotalExperiments());
        assertEquals(1, stats.getTotalReports());
        assertEquals(94.0, stats.getAverageQualityScore(), 1e-4);

        // Verify Activity Series
        ActivitySeriesDto activity = stats.getActivity();
        assertNotNull(activity, "Activity series must be generated");
        assertEquals(LocalDate.now(), activity.getEndDate());
        assertFalse(activity.isTruncated(), "Should not be truncated within 4 days");
        assertTrue(activity.getTotalEvents() >= 6, "At least 6 events should be in the window");

        List<ActivityBucketDto> buckets = activity.getBuckets();
        assertNotNull(buckets);
        assertTrue(buckets.size() >= 4, "Should span at least minusDays(3) to today");

        // Verify buckets have contiguous dates
        for (int i = 0; i < buckets.size() - 1; i++) {
            assertEquals(buckets.get(i).getDate().plusDays(1), buckets.get(i + 1).getDate(),
                    "Activity dates should be sequential without gaps");
        }

        // Today bucket should have the report exported
        ActivityBucketDto todayBucket = buckets.get(buckets.size() - 1);
        assertEquals(LocalDate.now(), todayBucket.getDate());
        assertTrue(todayBucket.getReports() >= 1);
    }
}
