package com.autoscientist.service;

import com.autoscientist.analysis.report.PdfExportService;
import com.autoscientist.analysis.report.ReportGeneratorService;
import com.autoscientist.dto.report.GenerateReportRequest;
import com.autoscientist.dto.report.ReportDto;
import com.autoscientist.entity.*;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DatasetService datasetService;
    private final ReportRepository reportRepository;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final AssociationRepository associationRepository;
    private final ResearchQuestionRepository researchQuestionRepository;
    private final HypothesisRepository hypothesisRepository;
    private final StatisticalTestRepository statisticalTestRepository;
    private final ExperimentRepository experimentRepository;
    private final ModelRunRepository modelRunRepository;
    private final FindingRepository findingRepository;
    private final DriftSnapshotRepository driftSnapshotRepository;
    private final ReportGeneratorService reportGeneratorService;
    private final PdfExportService pdfExportService;
    private final com.autoscientist.analysis.report.ReportExportService reportExportService;

    @Transactional
    public ReportDto generateReport(Long datasetId, GenerateReportRequest req, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        DataQualityReport qualityReport = dataQualityReportRepository.findByDataset(dataset).orElse(null);
        List<Association> associations = associationRepository.findByDatasetOrderByRankScoreDesc(dataset);

        ResearchQuestion question = null;
        if (req != null && req.getResearchQuestionId() != null) {
            question = researchQuestionRepository.findById(req.getResearchQuestionId()).orElse(null);
        } else {
            List<ResearchQuestion> questions = researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(dataset);
            if (!questions.isEmpty()) question = questions.get(0);
        }

        List<Hypothesis> hypotheses = question != null
                ? hypothesisRepository.findByResearchQuestionOrderByRankOrderAsc(question)
                : Collections.emptyList();

        List<StatisticalTest> tests = statisticalTestRepository.findByDatasetOrderByCreatedAtDesc(dataset);

        List<Experiment> experiments = experimentRepository.findByUserOrderByCreatedAtDesc(user);
        List<ModelRun> modelRuns = experiments.stream()
                .filter(e -> e.getDataset().getId().equals(datasetId))
                .findFirst()
                .map(e -> e.getVersions().isEmpty() ? Collections.<ModelRun>emptyList()
                        : modelRunRepository.findByExperimentVersion(e.getVersions().get(e.getVersions().size() - 1)))
                .orElse(Collections.emptyList());

        List<Finding> findings = findingRepository.findByDatasetOrderByCreatedAtDesc(dataset);

        // Real drift evidence, if a comparison has been run for this dataset.
        String driftReportJson = driftSnapshotRepository
                .findFirstByDatasetOrderByCreatedAtDesc(dataset)
                .map(DriftSnapshot::getReportJson)
                .orElse(null);

        String title = (req != null && req.getCustomTitle() != null && !req.getCustomTitle().isBlank())
                ? req.getCustomTitle()
                : (question != null ? "Scientific Discovery Report: " + question.getQuestionText()
                : "Empirical Discovery Report: " + dataset.getName());

        String markdown = reportGeneratorService.generateScientificReportMarkdown(
                dataset, qualityReport, associations, question, hypotheses, tests, modelRuns, findings, driftReportJson);

        String summary = String.format("Scientific empirical report (dataset v%d) synthesizing %d variables, %d associations, %d statistical tests, %d predictive models, and drift status %s.",
                dataset.getVersion() == null ? 1 : dataset.getVersion(),
                dataset.getColumnCount(), associations.size(), tests.size(), modelRuns.size(),
                driftReportJson == null ? "not run" : "reported");

        Report report = Report.builder()
                .dataset(dataset)
                .user(user)
                .researchQuestion(question)
                .title(title)
                .summary(summary)
                .markdownContent(markdown)
                .pdfFilename(dataset.getId() + "_report.pdf")
                .build();

        report = reportRepository.save(report);
        return mapToDto(report);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listUserReports(User user) {
        return reportRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listDatasetReports(Long datasetId, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        return reportRepository.findByDatasetOrderByCreatedAtDesc(dataset)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportDto getReport(Long id, User user) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));

        if (!report.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Unauthorized access to report.");
        }

        return mapToDto(report);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(Long id, User user) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));

        if (!report.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Unauthorized access to report.");
        }

        return pdfExportService.generatePdfReport(report.getTitle(), report.getMarkdownContent());
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long id, User user) {
        Report report = ownedReport(id, user);
        return reportExportService.toCsv(report.getMarkdownContent());
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(Long id, User user) {
        Report report = ownedReport(id, user);
        return reportExportService.toExcel(report.getTitle(), report.getMarkdownContent());
    }

    @Transactional(readOnly = true)
    public String exportMarkdown(Long id, User user) {
        Report report = ownedReport(id, user);
        return report.getMarkdownContent() != null ? report.getMarkdownContent() : "";
    }

    private Report ownedReport(Long id, User user) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        if (!report.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Unauthorized access to report.");
        }
        return report;
    }

    private ReportDto mapToDto(Report r) {
        return ReportDto.builder()
                .id(r.getId())
                .datasetId(r.getDataset().getId())
                .datasetName(r.getDataset().getName())
                .researchQuestionId(r.getResearchQuestion() != null ? r.getResearchQuestion().getId() : null)
                .title(r.getTitle())
                .summary(r.getSummary())
                .markdownContent(r.getMarkdownContent())
                .pdfDownloadUrl("/api/reports/" + r.getId() + "/pdf")
                .createdAt(r.getCreatedAt())
                .build();
    }
}
