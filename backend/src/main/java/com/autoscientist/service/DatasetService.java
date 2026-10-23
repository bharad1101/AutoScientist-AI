package com.autoscientist.service;

import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.analysis.quality.DataQualityEngine;
import com.autoscientist.dto.dataset.*;
import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.*;
import com.autoscientist.util.CsvValidator;
import com.autoscientist.util.ColumnSelection;
import com.autoscientist.util.ExcelImportService;
import com.autoscientist.util.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final FileStorageService fileStorageService;
    private final ExcelImportService excelImportService;
    private final DatasetProfiler datasetProfiler;
    private final DataQualityEngine dataQualityEngine;
    private final AnalysisRunService analysisRunService;
    private final AssociationRepository associationRepository;
    private final ResearchQuestionRepository researchQuestionRepository;
    private final HypothesisRepository hypothesisRepository;
    private final StatisticalTestRepository statisticalTestRepository;
    private final FindingRepository findingRepository;
    private final ReportRepository reportRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentVersionRepository experimentVersionRepository;
    private final ModelRunRepository modelRunRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final com.autoscientist.repository.DriftSnapshotRepository driftSnapshotRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DatasetDetailDto uploadDataset(MultipartFile file, String name, String description,
                                          com.autoscientist.dto.importing.ImportOptionsDto importOptions,
                                          User owner) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty.");
        }
        String originalFilename = file.getOriginalFilename();
        if (!com.autoscientist.util.ExcelImportService.isSupportedFilename(originalFilename)) {
            throw new BadRequestException("Unsupported file type. Upload a .csv, .xls or .xlsx file.");
        }
        com.autoscientist.dto.importing.ImportOptionsDto opts =
                importOptions != null ? importOptions
                        : com.autoscientist.dto.importing.ImportOptionsDto.builder().build();
        boolean excel = com.autoscientist.util.ExcelImportService.isExcelFilename(originalFilename);

        // Canonical analysis bytes: the uploaded CSV itself, or the converted CSV
        // rendition of the chosen Excel sheet + header row. The original workbook is
        // stored separately and never modified.
        byte[] analysisBytes;
        String sourceStoredFilename = null;
        String sheetName = null;
        Integer headerRow = null;
        String sourceFormat = "CSV";

        if (excel) {
            List<com.autoscientist.util.ExcelImportService.SheetInfo> sheets =
                    excelImportService.listSheets(file);
            if (sheets.isEmpty()) {
                throw new BadRequestException("The workbook contains no sheets.");
            }
            com.autoscientist.util.ExcelImportService.SheetInfo chosenSheet;
            if (opts.getSheetName() != null && !opts.getSheetName().isBlank()) {
                final String wanted = opts.getSheetName().trim();
                chosenSheet = sheets.stream()
                        .filter(s -> s.getName().equalsIgnoreCase(wanted))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException(
                                "Sheet '" + wanted + "' does not exist in this workbook."));
            } else if (opts.getSheetIndex() != null
                    && opts.getSheetIndex() >= 0 && opts.getSheetIndex() < sheets.size()) {
                chosenSheet = sheets.get(opts.getSheetIndex());
            } else {
                chosenSheet = sheets.get(0);
            }

            if (opts.getHeaderRow() != null) {
                headerRow = opts.getHeaderRow();
            } else {
                headerRow = excelImportService.detectHeaderRow(file, chosenSheet.getName(), chosenSheet.getIndex());
            }

            com.autoscientist.util.ExcelImportService.ConversionResult conv =
                    excelImportService.toCsv(file, chosenSheet.getName(), chosenSheet.getIndex(), headerRow);
            analysisBytes = excelImportService.toCsvBytes(conv.getCsv());
            sheetName = conv.getSheetName();
            sourceFormat = originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".xls") ? "XLS" : "XLSX";

            // Keep the original workbook for provenance; conversion is one-way.
            sourceStoredFilename = fileStorageService.storeFile(file);
        } else {
            try {
                analysisBytes = file.getBytes();
            } catch (Exception e) {
                throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
            }
            // Structural validation (works on CSV bytes; Excel path is validated by conversion).
            try (InputStream testIn = new ByteArrayInputStream(analysisBytes)) {
                CsvValidator.validateCsv(testIn);
            } catch (Exception e) {
                throw new BadRequestException("CSV validation failed: " + e.getMessage());
            }
        }

        String storedFilename = fileStorageService.storeBytes(
                analysisBytes, excel ? sheetName + ".csv" : originalFilename);

        String datasetName = (name != null && !name.trim().isEmpty()) ? name.trim() : originalFilename;

        Dataset dataset = Dataset.builder()
                .owner(owner)
                .name(datasetName)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .sourceStoredFilename(sourceStoredFilename)
                .fileSize(file.getSize())
                .description(description)
                .sourceFormat(sourceFormat)
                .sheetName(sheetName)
                .headerRow(headerRow)
                .build();
        dataset.setImportOptionsJson(safeJson(opts));

        dataset = datasetRepository.save(dataset);

        long started = System.currentTimeMillis();

        // Apply user's explicit type overrides (wizard step 2) while profiling.
        java.util.Set<String> forceId = opts.getForceIdentifier() != null
                ? new java.util.HashSet<>(opts.getForceIdentifier()) : java.util.Collections.emptySet();
        java.util.Set<String> forceNonId = opts.getForceNonIdentifier() != null
                ? new java.util.HashSet<>(opts.getForceNonIdentifier()) : java.util.Collections.emptySet();

        // Run Profiler
        try (InputStream in = new ByteArrayInputStream(analysisBytes)) {
            DatasetProfiler.ProfilingResult profResult = datasetProfiler.profile(in, dataset, forceId, forceNonId);
            dataset.setRowCount(profResult.getRowCount());
            dataset.setColumnCount(profResult.getColumnCount());
            dataset.setDatasetType(profResult.getDatasetType());
            dataset.setColumns(profResult.getColumns());
        } catch (Exception e) {
            fileStorageService.deleteFile(storedFilename);
            if (sourceStoredFilename != null) fileStorageService.deleteFile(sourceStoredFilename);
            throw new BadRequestException("Profiling failed: " + e.getMessage());
        }

        // Validate wizard-requested exclusions against the real column list.
        if (opts.getExcludedColumns() != null && !opts.getExcludedColumns().isEmpty()) {
            Set<String> known = dataset.getColumns().stream()
                    .map(DatasetColumn::getColumnName)
                    .collect(Collectors.toSet());
            List<String> validatedExclusions = new ArrayList<>();
            for (String col : opts.getExcludedColumns()) {
                String match = known.stream().filter(k -> k.equalsIgnoreCase(col)).findFirst().orElse(null);
                if (match == null) {
                    throw new BadRequestException("Unknown column in exclusion list: " + col);
                }
                if (!validatedExclusions.contains(match)) validatedExclusions.add(match);
            }
            dataset.setExcludedColumnsJson(validatedExclusions.isEmpty() ? null : safeJson(validatedExclusions));
        }

        // Run Quality Engine
        DataQualityReport qualityReport;
        try (InputStream in = new ByteArrayInputStream(analysisBytes)) {
            qualityReport = dataQualityEngine.evaluate(in, dataset, dataset.getColumns());
            dataset.setQualityReport(qualityReport);
        } catch (Exception e) {
            fileStorageService.deleteFile(storedFilename);
            if (sourceStoredFilename != null) fileStorageService.deleteFile(sourceStoredFilename);
            throw new BadRequestException("Data quality audit failed: " + e.getMessage());
        }

        dataset = datasetRepository.save(dataset);

        long duration = System.currentTimeMillis() - started;
        Map<String, Object> importParams = new LinkedHashMap<>();
        importParams.put("format", sourceFormat);
        if (sheetName != null) importParams.put("sheet", sheetName);
        if (headerRow != null) importParams.put("headerRow", headerRow);
        if (opts.getExcludedColumns() != null && !opts.getExcludedColumns().isEmpty()) {
            importParams.put("excluded", String.join(",", opts.getExcludedColumns()));
        }
        analysisRunService.record(dataset, AnalysisOperations.IMPORT, importParams,
                String.format("Imported %d rows x %d columns from %s%s",
                        dataset.getRowCount(), dataset.getColumnCount(), originalFilename,
                        sheetName != null ? " (sheet '" + sheetName + "', header row " + headerRow + ")" : ""),
                Map.of("rows", dataset.getRowCount(), "columns", dataset.getColumnCount(),
                        "format", sourceFormat),
                duration, owner);
        analysisRunService.record(dataset, AnalysisOperations.PROFILE, Map.of("version", 1),
                String.format("Profiled %d columns (%s)", dataset.getColumnCount(), dataset.getDatasetType()),
                null, duration, owner);
        analysisRunService.record(dataset, AnalysisOperations.QUALITY, Map.of("version", 1),
                String.format("Quality audit score %.1f/100 with %d issue(s)",
                        qualityReport.getOverallScore(),
                        qualityReport.getIssues() != null ? qualityReport.getIssues().size() : 0),
                Map.of("score", qualityReport.getOverallScore()), duration, owner);

        return mapToDetailDto(dataset);
    }

    /**
     * Re-run type inference and statistics for an existing dataset without touching the
     * uploaded file. Bumps the dataset version so cached profiles can be invalidated.
     */
    @Transactional
    public DatasetDetailDto reprofile(Long id, ReprofileRequestDto request, User owner) {
        Dataset dataset = getDatasetEntityWithOwnership(id, owner);
        Set<String> forceId = request != null && request.getForceIdentifier() != null
                ? new HashSet<>(request.getForceIdentifier()) : Collections.emptySet();
        Set<String> forceNonId = request != null && request.getForceNonIdentifier() != null
                ? new HashSet<>(request.getForceNonIdentifier()) : Collections.emptySet();

        long started = System.currentTimeMillis();
        try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
            DatasetProfiler.ProfilingResult result = datasetProfiler.profile(in, dataset, forceId, forceNonId);
            dataset.setRowCount(result.getRowCount());
            dataset.setColumnCount(result.getColumnCount());
            dataset.setDatasetType(result.getDatasetType());
            // Replace the collection in place: assigning a new List to a mapped
            // collection with orphanRemoval throws "collection was no longer referenced".
            List<DatasetColumn> profiled = result.getColumns();
            if (dataset.getColumns() == null) {
                dataset.setColumns(new ArrayList<>(profiled));
            } else {
                dataset.getColumns().clear();
                for (DatasetColumn col : profiled) {
                    col.setDataset(dataset);
                    dataset.getColumns().add(col);
                }
            }
        } catch (Exception e) {
            throw new BadRequestException("Re-profiling failed: " + e.getMessage());
        }

        // Quality issues depend on the profile, so refresh the audit as well.
        try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
            DataQualityReport fresh = dataQualityEngine.evaluate(in, dataset, dataset.getColumns());
            DataQualityReport existing = dataset.getQualityReport();
            if (existing == null) {
                dataset.setQualityReport(fresh);
            } else {
                // The report row is unique per dataset. Hibernate inserts before it
                // deletes during a flush, so swapping in a fresh entity would trip the
                // unique (dataset_id) constraint — update the existing row in place and
                // replace only its issue rows (different table, no such constraint).
                existing.setOverallScore(fresh.getOverallScore());
                existing.setMissingValueScore(fresh.getMissingValueScore());
                existing.setDuplicateScore(fresh.getDuplicateScore());
                existing.setOutlierScore(fresh.getOutlierScore());
                existing.setConsistencyScore(fresh.getConsistencyScore());
                existing.setTotalIssues(fresh.getTotalIssues());
                List<com.autoscientist.entity.DataQualityIssue> replacements =
                        fresh.getIssues() != null ? fresh.getIssues() : Collections.emptyList();
                existing.getIssues().clear();
                for (com.autoscientist.entity.DataQualityIssue issue : replacements) {
                    issue.setReport(existing);
                    existing.getIssues().add(issue);
                }
                dataset.setQualityReport(existing);
            }
        } catch (Exception e) {
            log.warn("Quality re-audit failed during reprofile of dataset {}: {}", id, e.getMessage());
        }

        int newVersion = (dataset.getVersion() != null ? dataset.getVersion() : 1) + 1;
        dataset.setVersion(newVersion);
        dataset = datasetRepository.save(dataset);

        analysisRunService.record(dataset, AnalysisOperations.PROFILE, Map.of("version", newVersion),
                String.format("Re-profiled (version %d)%s%s", newVersion,
                        forceId.isEmpty() ? "" : ", forced identifiers: " + String.join(", ", forceId),
                        forceNonId.isEmpty() ? "" : ", explicitly included: " + String.join(", ", forceNonId)),
                Map.of("version", newVersion), System.currentTimeMillis() - started, owner);

        return mapToDetailDto(dataset);
    }

    /**
     * Update analysis-time column exclusions. Non-destructive: the stored file keeps all
     * columns; excluded columns are skipped by analytics engines. Bumps the dataset version.
     */
    @Transactional
    public DatasetDetailDto updateExclusions(Long id, DatasetExclusionRequestDto request, User owner) {
        Dataset dataset = getDatasetEntityWithOwnership(id, owner);
        List<String> requested = request != null && request.getExcludedColumns() != null
                ? request.getExcludedColumns() : Collections.emptyList();

        Set<String> known = dataset.getColumns().stream()
                .map(DatasetColumn::getColumnName)
                .collect(Collectors.toSet());
        List<String> validated = new ArrayList<>();
        for (String col : requested) {
            String match = known.stream().filter(k -> k.equalsIgnoreCase(col)).findFirst().orElse(null);
            if (match == null) {
                throw new BadRequestException("Unknown column in exclusion list: " + col);
            }
            if (!validated.contains(match)) validated.add(match);
        }

        dataset.setExcludedColumnsJson(validated.isEmpty() ? null : safeJson(validated));
        int newVersion = (dataset.getVersion() != null ? dataset.getVersion() : 1) + 1;
        dataset.setVersion(newVersion);
        dataset = datasetRepository.save(dataset);

        analysisRunService.record(dataset, AnalysisOperations.IMPORT,
                Map.of("exclusions", String.join(",", validated)),
                validated.isEmpty() ? "All columns included in analysis"
                        : "Excluded from analysis: " + String.join(", ", validated),
                Map.of("excluded", validated), null, owner);

        return mapToDetailDto(dataset);
    }

    @Transactional(readOnly = true)
    public List<DatasetDto> listUserDatasets(User owner) {
        return datasetRepository.findByOwnerOrderByCreatedAtDesc(owner)
                .stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DatasetDetailDto getDatasetDetail(Long id, User owner) {
        Dataset dataset = getDatasetEntityWithOwnership(id, owner);
        return mapToDetailDto(dataset);
    }

    @Transactional(readOnly = true)
    public Dataset getDatasetEntityWithOwnership(Long id, User owner) {
        Dataset dataset = datasetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found with id: " + id));

        if (!dataset.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("You do not have permission to access dataset id: " + id);
        }

        return dataset;
    }

    /**
     * Deletes a dataset and every dependent record in FK-safe order. Rows owned by other
     * entities are removed explicitly because JPA does not cascade across relations the
     * Dataset entity does not map (a pre-existing source of FK failures on MySQL).
     */
    @Transactional
    public void deleteDataset(Long id, User owner) {
        Dataset dataset = getDatasetEntityWithOwnership(id, owner);
        String storedFilename = dataset.getStoredFilename();

        List<com.autoscientist.entity.ResearchQuestion> questions =
                researchQuestionRepository.findByDatasetOrderByCreatedAtDesc(dataset);
        reportRepository.deleteAll(reportRepository.findByDatasetOrderByCreatedAtDesc(dataset));
        findingRepository.deleteAll(findingRepository.findByDatasetOrderByCreatedAtDesc(dataset));
        statisticalTestRepository.deleteAll(statisticalTestRepository.findByDatasetOrderByCreatedAtDesc(dataset));
        for (com.autoscientist.entity.ResearchQuestion q : questions) {
            if (q.getHypotheses() != null && !q.getHypotheses().isEmpty()) {
                hypothesisRepository.deleteAll(q.getHypotheses());
            }
        }
        researchQuestionRepository.deleteAll(questions);

        for (com.autoscientist.entity.Experiment e : experimentRepository.findByDatasetOrderByCreatedAtDesc(dataset)) {
            if (e.getVersions() != null) {
                for (com.autoscientist.entity.ExperimentVersion v : e.getVersions()) {
                    modelRunRepository.deleteAll(modelRunRepository.findByExperimentVersion(v));
                }
                experimentVersionRepository.deleteAll(e.getVersions());
            }
        }
        experimentRepository.deleteAll(experimentRepository.findByDatasetOrderByCreatedAtDesc(dataset));

        analysisRunRepository.deleteByDatasetId(dataset.getId());
        driftSnapshotRepository.deleteAll(driftSnapshotRepository.findByDatasetOrderByCreatedAtDesc(dataset));
        associationRepository.deleteByDataset(dataset);

        datasetRepository.delete(dataset);
        fileStorageService.deleteFile(storedFilename);
        if (dataset.getSourceStoredFilename() != null) {
            fileStorageService.deleteFile(dataset.getSourceStoredFilename());
        }
    }

    public DatasetDto mapToSummaryDto(Dataset dataset) {
        Double score = null;
        if (dataset.getQualityReport() != null) {
            score = dataset.getQualityReport().getOverallScore();
        }

        return DatasetDto.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .originalFilename(dataset.getOriginalFilename())
                .fileSize(dataset.getFileSize())
                .rowCount(dataset.getRowCount())
                .columnCount(dataset.getColumnCount())
                .datasetType(dataset.getDatasetType())
                .description(dataset.getDescription())
                .qualityScore(score)
                .sourceFormat(dataset.getSourceFormat())
                .sheetName(dataset.getSheetName())
                .version(dataset.getVersion())
                .parentDatasetId(dataset.getParentDatasetId())
                .excludedColumns(ColumnSelection.excludedNames(dataset).stream().sorted().collect(Collectors.toList()))
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    public DatasetDetailDto mapToDetailDto(Dataset dataset) {
        Set<String> excluded = ColumnSelection.excludedNames(dataset);
        List<DatasetColumnDto> columnDtos = dataset.getColumns().stream()
                .map(col -> DatasetColumnDto.builder()
                        .id(col.getId())
                        .columnIndex(col.getColumnIndex())
                        .columnName(col.getColumnName())
                        .dataType(col.getDataType())
                        .typeConfidence(col.getTypeConfidence())
                        .missingCount(col.getMissingCount())
                        .missingPct(col.getMissingPct())
                        .uniqueCount(col.getUniqueCount())
                        .minVal(col.getMinVal())
                        .maxVal(col.getMaxVal())
                        .meanVal(col.getMeanVal())
                        .medianVal(col.getMedianVal())
                        .stdDev(col.getStdDev())
                        .variance(col.getVariance())
                        .q1(col.getQ1())
                        .q3(col.getQ3())
                        .skewness(col.getSkewness())
                        .categoriesJson(col.getCategoriesJson())
                        .distributionJson(col.getDistributionJson())
                        .excluded(excluded.contains(col.getColumnName()))
                        .build())
                .collect(Collectors.toList());

        DataQualityReportDto qualityReportDto = null;
        if (dataset.getQualityReport() != null) {
            DataQualityReport rep = dataset.getQualityReport();
            List<DataQualityIssueDto> issueDtos = rep.getIssues().stream()
                    .map(issue -> DataQualityIssueDto.builder()
                            .id(issue.getId())
                            .columnName(issue.getColumnName())
                            .issueType(issue.getIssueType())
                            .severity(issue.getSeverity())
                            .description(issue.getDescription())
                            .recommendation(issue.getRecommendation())
                            .affectedRows(issue.getAffectedRows())
                            .build())
                    .collect(Collectors.toList());

            qualityReportDto = DataQualityReportDto.builder()
                    .id(rep.getId())
                    .overallScore(rep.getOverallScore())
                    .missingValueScore(rep.getMissingValueScore())
                    .duplicateScore(rep.getDuplicateScore())
                    .outlierScore(rep.getOutlierScore())
                    .consistencyScore(rep.getConsistencyScore())
                    .totalIssues(rep.getTotalIssues())
                    .issues(issueDtos)
                    .build();
        }

        return DatasetDetailDto.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .originalFilename(dataset.getOriginalFilename())
                .fileSize(dataset.getFileSize())
                .rowCount(dataset.getRowCount())
                .columnCount(dataset.getColumnCount())
                .datasetType(dataset.getDatasetType())
                .description(dataset.getDescription())
                .sourceFormat(dataset.getSourceFormat())
                .sheetName(dataset.getSheetName())
                .headerRow(dataset.getHeaderRow())
                .version(dataset.getVersion())
                .parentDatasetId(dataset.getParentDatasetId())
                .excludedColumns(new ArrayList<>(excluded))
                .importOptions(readImportOptions(dataset))
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .columns(columnDtos)
                .qualityReport(qualityReportDto)
                .build();
    }

    private Map<String, Object> readImportOptions(Dataset dataset) {
        if (dataset.getImportOptionsJson() == null || dataset.getImportOptionsJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(dataset.getImportOptionsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
