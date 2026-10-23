package com.autoscientist.service;

import com.autoscientist.analysis.findings.EvidenceEngine;
import com.autoscientist.analysis.findings.QuestionAnalysisEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.dto.research.*;
import com.autoscientist.entity.*;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.*;
import com.autoscientist.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResearchService {

    private final ResearchQuestionRepository researchQuestionRepository;
    private final HypothesisRepository hypothesisRepository;
    private final StatisticalTestRepository statisticalTestRepository;
    private final FindingRepository findingRepository;
    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final QuestionAnalysisEngine questionAnalysisEngine;
    private final StatisticalTestEngine statisticalTestEngine;
    private final EvidenceEngine evidenceEngine;
    private final AnalysisRunService analysisRunService;

    @Transactional
    public ResearchQuestionDto createResearchQuestion(Long datasetId, ResearchQuestionRequest request, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);

        ResearchQuestion rq = ResearchQuestion.builder()
                .dataset(dataset)
                .user(user)
                .questionText(request.getQuestionText().trim())
                .status("ACTIVE")
                .build();

        rq = researchQuestionRepository.save(rq);

        // Active columns (user exclusions honoured). Explicit predictor/target values from
        // the request are used verbatim; auto-extraction never selects an identifier.
        QuestionAnalysisEngine.QuestionAnalysisResult analysis =
                questionAnalysisEngine.analyzeQuestion(
                        request.getQuestionText(),
                        request.getPredictorVariable(),
                        request.getTargetVariable(),
                        com.autoscientist.util.ColumnSelection.active(dataset),
                        rq
                );

        rq.setPredictorVariable(analysis.getPredictorVariable());
        rq.setTargetVariable(analysis.getTargetVariable());
        rq.setQuestionType(analysis.getQuestionType());
        rq.setSuggestedTest(analysis.getSuggestedTest());

        List<Hypothesis> savedHypotheses = hypothesisRepository.saveAll(analysis.getHypotheses());
        rq.setHypotheses(savedHypotheses);

        rq = researchQuestionRepository.save(rq);

        analysisRunService.record(dataset, com.autoscientist.analysis.core.AnalysisOperations.RESEARCH_QUESTION,
                Map.of("test", analysis.getSuggestedTest()),
                "Question formulated: " + abbreviate(request.getQuestionText(), 200),
                Map.of("predictor", analysis.getPredictorVariable(), "target", analysis.getTargetVariable()),
                null, user);

        return mapToRqDto(rq);
    }

    @Transactional
    public FindingDto executeHypothesisTest(Long rqId, Long hypothesisId, User user) {
        ResearchQuestion rq = researchQuestionRepository.findById(rqId)
                .orElseThrow(() -> new ResourceNotFoundException("Research question not found with id: " + rqId));

        if (!rq.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Unauthorized access to research question.");
        }

        Hypothesis hypothesis = hypothesisRepository.findById(hypothesisId)
                .orElseThrow(() -> new ResourceNotFoundException("Hypothesis not found with id: " + hypothesisId));

        Dataset dataset = rq.getDataset();

        // Resolve the hypothesis variables case-insensitively against the stored columns so
        // a case-only mismatch fails with a clear message instead of silently testing nothing.
        String var1 = resolveColumnName(dataset, hypothesis.getVar1());
        String var2 = hypothesis.getVar2() != null ? resolveColumnName(dataset, hypothesis.getVar2()) : null;

        // Read columns data from dataset CSV
        Map<String, List<String>> colData = parseColumns(dataset.getStoredFilename(),
                Arrays.asList(var1, var2 != null ? var2 : var1));
        List<String> raw1 = colData.get(var1);
        List<String> raw2 = var2 != null ? colData.get(var2) : null;

        if (raw1 == null || raw1.isEmpty()) {
            throw new BadRequestException("Variable '" + var1 + "' has no data in this dataset.");
        }
        if (var2 != null && (raw2 == null || raw2.isEmpty())) {
            throw new BadRequestException("Variable '" + var2 + "' has no data in this dataset.");
        }

        StatisticalTestEngine.TestResult result;
        String testType = hypothesis.getSuggestedTest();

        long started = System.currentTimeMillis();
        if (testType.equals("PEARSON")) {
            result = statisticalTestEngine.computePearsonCorrelation(var1, var2, raw1, raw2);
        } else if (testType.equals("WELCH_T_TEST")) {
            result = statisticalTestEngine.computeWelchTTest(var1, var2, raw1, raw2);
        } else if (testType.equals("ONE_WAY_ANOVA")) {
            result = statisticalTestEngine.computeOneWayAnova(var1, var2, raw1, raw2);
        } else {
            result = statisticalTestEngine.computeChiSquare(var1, var2, raw1, raw2);
        }

        StatisticalTest test = StatisticalTest.builder()
                .hypothesis(hypothesis)
                .dataset(dataset)
                .testType(result.getTestType())
                .var1(var1)
                .var2(var2)
                .statistic(result.getStatistic())
                .pValue(result.getPValue())
                .alpha(0.05)
                .degreesOfFreedom(result.getDegreesOfFreedom())
                .effectSize(result.getEffectSize())
                .effectSizeType(result.getEffectSizeType())
                .sampleSize(result.getSampleSize())
                .isStatisticallySignificant(result.isSignificant())
                .status("COMPLETED")
                .interpretation(result.getInterpretation())
                .ciLower(result.getCiLower())
                .ciUpper(result.getCiUpper())
                .ciLevel(result.getCiLevel())
                .ciMethod(result.getCiMethod())
                .ciTarget(result.getCiTarget())
                .ciUnavailableReason(result.getCiUnavailableReason())
                .build();

        test = statisticalTestRepository.save(test);

        // Generate Finding and Provenance Graph Nodes
        Finding finding = evidenceEngine.createFinding(dataset, rq, hypothesis, test);
        finding = findingRepository.save(finding);

        analysisRunService.record(dataset, com.autoscientist.analysis.core.AnalysisOperations.STAT_TEST,
                Map.of("testType", result.getTestType(), "var1", var1, "var2", var2 == null ? "" : var2),
                String.format("%s on %s: statistic=%.4f, p=%.4g, n=%d, %s",
                        result.getTestType(), var1 + (var2 != null ? " vs " + var2 : ""),
                        result.getStatistic(), result.getPValue(), result.getSampleSize(),
                        result.isSignificant() ? "significant" : "not significant"),
                Map.of("pValue", result.getPValue(), "effectSize", result.getEffectSize()),
                System.currentTimeMillis() - started, user);
        analysisRunService.record(dataset, com.autoscientist.analysis.core.AnalysisOperations.FINDING,
                Map.of("testId", String.valueOf(test.getId())),
                "Finding recorded: " + finding.getTitle(), null, null, user);

        return mapToFindingDto(finding);
    }

    private String resolveColumnName(Dataset dataset, String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("A variable name is required to run a statistical test.");
        }
        return dataset.getColumns().stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(name.trim()))
                .map(DatasetColumn::getColumnName)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Variable '" + name + "' does not exist in this dataset."));
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    @Transactional(readOnly = true)
    public List<ResearchQuestionDto> getDatasetResearchQuestions(Long datasetId, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        return researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(dataset)
                .stream()
                .map(this::mapToRqDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ResearchQuestionDto getResearchQuestion(Long id, User user) {
        ResearchQuestion rq = researchQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Research question not found with id: " + id));

        if (!rq.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Unauthorized access to research question.");
        }

        return mapToRqDto(rq);
    }

    private Map<String, List<String>> parseColumns(String storedFilename, List<String> targets) {
        Map<String, List<String>> map = new HashMap<>();
        targets.forEach(t -> map.put(t, new ArrayList<>()));

        try (InputStream in = fileStorageService.getInputStream(storedFilename);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                for (String t : targets) {
                    if (record.isMapped(t)) {
                        map.get(t).add(record.get(t));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed reading columns from dataset: " + e.getMessage(), e);
        }

        return map;
    }

    public ResearchQuestionDto mapToRqDto(ResearchQuestion rq) {
        List<HypothesisDto> hypDtos = rq.getHypotheses().stream()
                .map(this::mapToHypDto)
                .collect(Collectors.toList());

        return ResearchQuestionDto.builder()
                .id(rq.getId())
                .datasetId(rq.getDataset().getId())
                .questionText(rq.getQuestionText())
                .predictorVariable(rq.getPredictorVariable())
                .targetVariable(rq.getTargetVariable())
                .questionType(rq.getQuestionType())
                .suggestedTest(rq.getSuggestedTest())
                .status(rq.getStatus())
                .hypotheses(hypDtos)
                .createdAt(rq.getCreatedAt())
                .build();
    }

    public HypothesisDto mapToHypDto(Hypothesis h) {
        List<StatisticalTestDto> testDtos = h.getStatisticalTests().stream()
                .map(this::mapToTestDto)
                .collect(Collectors.toList());

        return HypothesisDto.builder()
                .id(h.getId())
                .researchQuestionId(h.getResearchQuestion().getId())
                .nullHypothesis(h.getNullHypothesis())
                .altHypothesis(h.getAltHypothesis())
                .rationale(h.getRationale())
                .suggestedTest(h.getSuggestedTest())
                .var1(h.getVar1())
                .var2(h.getVar2())
                .confidence(h.getConfidence())
                .rankOrder(h.getRankOrder())
                .statisticalTests(testDtos)
                .createdAt(h.getCreatedAt())
                .build();
    }

    public StatisticalTestDto mapToTestDto(StatisticalTest t) {
        return StatisticalTestDto.builder()
                .id(t.getId())
                .hypothesisId(t.getHypothesis().getId())
                .datasetId(t.getDataset().getId())
                .testType(t.getTestType())
                .var1(t.getVar1())
                .var2(t.getVar2())
                .statistic(t.getStatistic())
                .pValue(t.getPValue())
                .alpha(t.getAlpha())
                .degreesOfFreedom(t.getDegreesOfFreedom())
                .effectSize(t.getEffectSize())
                .effectSizeType(t.getEffectSizeType())
                .sampleSize(t.getSampleSize())
                .isStatisticallySignificant(t.getIsStatisticallySignificant())
                .status(t.getStatus())
                .interpretation(t.getInterpretation())
                .ciLower(t.getCiLower())
                .ciUpper(t.getCiUpper())
                .ciLevel(t.getCiLevel())
                .ciMethod(t.getCiMethod())
                .ciTarget(t.getCiTarget())
                .ciUnavailableReason(t.getCiUnavailableReason())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public FindingDto mapToFindingDto(Finding f) {
        List<EvidenceNodeDto> nodeDtos = f.getEvidenceNodes().stream()
                .map(n -> EvidenceNodeDto.builder()
                        .id(n.getId())
                        .nodeType(n.getNodeType())
                        .referenceId(n.getReferenceId())
                        .description(n.getDescription())
                        .dataPayloadJson(n.getDataPayloadJson())
                        .createdAt(n.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return FindingDto.builder()
                .id(f.getId())
                .datasetId(f.getDataset().getId())
                .researchQuestionId(f.getResearchQuestion() != null ? f.getResearchQuestion().getId() : null)
                .hypothesisId(f.getHypothesis() != null ? f.getHypothesis().getId() : null)
                .statisticalTestId(f.getStatisticalTest() != null ? f.getStatisticalTest().getId() : null)
                .title(f.getTitle())
                .summary(f.getSummary())
                .evidenceText(f.getEvidenceText())
                .confidenceLevel(f.getConfidenceLevel())
                .causalityDisclaimer(f.getCausalityDisclaimer())
                .evidenceNodes(nodeDtos)
                .createdAt(f.getCreatedAt())
                .build();
    }
}
