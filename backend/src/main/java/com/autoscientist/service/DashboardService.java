package com.autoscientist.service;

import com.autoscientist.dto.dashboard.ActivityBucketDto;
import com.autoscientist.dto.dashboard.ActivitySeriesDto;
import com.autoscientist.dto.dashboard.DashboardStatsDto;
import com.autoscientist.dto.dashboard.TimelineEventDto;
import com.autoscientist.entity.*;
import com.autoscientist.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard aggregates.
 *
 * Every number reported here comes from a persisted record — model counts are
 * real ModelRun rows, association/finding counts are real repository counts.
 * No estimate, placeholder or "+N models per experiment" style fabrication.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Maximum number of days rendered by the dashboard activity histogram. */
    private static final int MAX_ACTIVITY_DAYS = 60;

    private final DatasetRepository datasetRepository;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final AssociationRepository associationRepository;
    private final ResearchQuestionRepository researchQuestionRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentVersionRepository experimentVersionRepository;
    private final ModelRunRepository modelRunRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(User user) {
        List<Dataset> datasets = datasetRepository.findByOwnerOrderByCreatedAtDesc(user);
        long totalDatasets = datasets.size();
        long totalObservations = datasets.stream()
                .mapToLong(d -> d.getRowCount() != null ? d.getRowCount() : 0).sum();

        // Single owner-scoped queries instead of per-dataset loops (N+1 removal).
        List<DataQualityReport> qualityReports = dataQualityReportRepository.findByOwner(user);
        double totalQualityScore = qualityReports.stream()
                .mapToDouble(q -> q.getOverallScore() != null ? q.getOverallScore() : 0.0).sum();
        long totalAssoc = associationRepository.countByOwner(user);
        long totalFindings = findingRepository.countByOwner(user);
        long totalModelsTrained = modelRunRepository.countByOwner(user);
        long totalExperiments = experimentRepository.countByOwner(user);

        List<TimelineEventDto> timeline = new ArrayList<>();

        for (Dataset d : datasets) {
            timeline.add(TimelineEventDto.builder()
                    .eventType("DATASET_UPLOAD")
                    .title("Uploaded Dataset: " + d.getName())
                    .description(String.format("Profiled %d rows and %d columns (%s)",
                            d.getRowCount() != null ? d.getRowCount() : 0,
                            d.getColumnCount() != null ? d.getColumnCount() : 0,
                            d.getDatasetType()))
                    .datasetId(d.getId())
                    .datasetName(d.getName())
                    .timestamp(d.getCreatedAt())
                    .build());
        }

        for (Finding f : findingRepository.findAllByOwner(user)) {
            timeline.add(TimelineEventDto.builder()
                    .eventType("FINDING_PRODUCED")
                    .title("Established Finding: " + f.getTitle())
                    .description(abbreviate(f.getSummary(), 100))
                    .datasetId(f.getDataset().getId())
                    .datasetName(f.getDataset().getName())
                    .timestamp(f.getCreatedAt())
                    .build());
        }

        List<ResearchQuestion> questions = researchQuestionRepository.findByUserOrderByCreatedAtDesc(user);
        for (ResearchQuestion q : questions) {
            timeline.add(TimelineEventDto.builder()
                    .eventType("QUESTION_ASKED")
                    .title("Research Question: " + abbreviate(q.getQuestionText(), 60))
                    .description("Target: " + q.getTargetVariable() + " | Test: " + q.getSuggestedTest())
                    .datasetId(q.getDataset().getId())
                    .datasetName(q.getDataset().getName())
                    .timestamp(q.getCreatedAt())
                    .build());
        }

        for (Experiment e : experimentRepository.findByUserOrderByCreatedAtDesc(user)) {
            int versionCount = e.getVersions() != null ? e.getVersions().size() : 0;
            timeline.add(TimelineEventDto.builder()
                    .eventType("MODEL_TRAINED")
                    .title("AutoML Experiment: " + e.getName())
                    .description(String.format("Target: %s (%s) — %d version(s) recorded",
                            e.getTargetColumn(), e.getProblemType(), versionCount))
                    .datasetId(e.getDataset().getId())
                    .datasetName(e.getDataset().getName())
                    .timestamp(e.getCreatedAt())
                    .build());
        }

        for (Report r : reportRepository.findByUserOrderByCreatedAtDesc(user)) {
            timeline.add(TimelineEventDto.builder()
                    .eventType("REPORT_EXPORTED")
                    .title("Generated Scientific Report: " + r.getTitle())
                    .description(r.getSummary())
                    .datasetId(r.getDataset().getId())
                    .datasetName(r.getDataset().getName())
                    .timestamp(r.getCreatedAt())
                    .build());
        }

        timeline.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

        ActivitySeriesDto activity = buildActivitySeries(timeline);

        int qualityCount = qualityReports.size();
        double avgQuality = qualityCount > 0 ? totalQualityScore / qualityCount : 0.0;
        avgQuality = Math.round(avgQuality * 10.0) / 10.0;

        return DashboardStatsDto.builder()
                .totalDatasets(totalDatasets)
                .totalObservations(totalObservations)
                .totalAssociations(totalAssoc)
                .totalResearchQuestions(questions.size())
                .totalExperiments(totalExperiments)
                .totalModelsTrained(totalModelsTrained)
                .totalFindings(totalFindings)
                .totalReports(reportRepository.findByUserOrderByCreatedAtDesc(user).size())
                .averageQualityScore(avgQuality)
                .recentActivity(timeline.stream().limit(12).collect(Collectors.toList()))
                .activity(activity)
                .build();
    }

    /**
     * Builds a per-day histogram of the complete research activity timeline.
     *
     * The window spans the first recorded event up to today, capped to the most
     * recent {@code MAX_ACTIVITY_DAYS} days so the chart stays readable. Days
     * without recorded activity are emitted with zero counts — they are real
     * days, not missing data.
     */
    private ActivitySeriesDto buildActivitySeries(List<TimelineEventDto> timeline) {
        LocalDate today = LocalDate.now();
        LocalDate start = timeline.stream()
                .map(e -> e.getTimestamp())
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(today);
        if (start.isAfter(today)) {
            start = today;
        }

        boolean truncated = false;
        LocalDate earliestAllowed = today.minusDays(MAX_ACTIVITY_DAYS - 1L);
        if (start.isBefore(earliestAllowed)) {
            start = earliestAllowed;
            truncated = true;
        }

        Map<LocalDate, ActivityBucketDto> byDate = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            byDate.put(d, ActivityBucketDto.builder().date(d).build());
        }

        int inWindow = 0;
        for (TimelineEventDto e : timeline) {
            if (e.getTimestamp() == null) {
                continue;
            }
            LocalDate date = e.getTimestamp().toLocalDate();
            ActivityBucketDto bucket = byDate.get(date);
            if (bucket == null) {
                continue;
            }
            inWindow++;
            bucket.setTotal(bucket.getTotal() + 1);
            switch (e.getEventType() == null ? "" : e.getEventType()) {
                case "DATASET_UPLOAD" -> bucket.setDatasetUploads(bucket.getDatasetUploads() + 1);
                case "FINDING_PRODUCED" -> bucket.setFindings(bucket.getFindings() + 1);
                case "QUESTION_ASKED" -> bucket.setQuestions(bucket.getQuestions() + 1);
                case "MODEL_TRAINED" -> bucket.setExperiments(bucket.getExperiments() + 1);
                case "REPORT_EXPORTED" -> bucket.setReports(bucket.getReports() + 1);
                default -> { /* unknown event types still count towards the daily total */ }
            }
        }

        return ActivitySeriesDto.builder()
                .startDate(start)
                .endDate(today)
                .totalEvents(inWindow)
                .lifetimeEvents(timeline.size())
                .truncated(truncated)
                .buckets(new ArrayList<>(byDate.values()))
                .build();
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, Math.max(0, max - 3)) + "..." : s;
    }
}
