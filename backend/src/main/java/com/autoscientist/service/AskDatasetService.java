package com.autoscientist.service;

import com.autoscientist.analysis.core.AskDatasetEngine;
import com.autoscientist.dto.ask.AskDatasetRequestDto;
import com.autoscientist.dto.ask.AskDatasetResponseDto;
import com.autoscientist.entity.*;
import com.autoscientist.repository.AssociationRepository;
import com.autoscientist.repository.DataQualityReportRepository;
import com.autoscientist.repository.ExperimentRepository;
import com.autoscientist.repository.ModelRunRepository;
import com.autoscientist.util.ColumnSelection;
import com.autoscientist.util.CsvColumnLoader;
import com.autoscientist.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AskDatasetService {

    private final DatasetService datasetService;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final AssociationRepository associationRepository;
    private final ExperimentRepository experimentRepository;
    private final ModelRunRepository modelRunRepository;
    private final AskDatasetEngine askDatasetEngine;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public AskDatasetResponseDto askQuestion(Long datasetId, AskDatasetRequestDto request, User user) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new com.autoscientist.exception.BadRequestException("A non-empty question (query) is required.");
        }
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        DataQualityReport qualityReport = dataQualityReportRepository.findByDataset(dataset).orElse(null);
        List<Association> associations = associationRepository.findByDatasetOrderByRankScoreDesc(dataset);

        List<Experiment> experiments = experimentRepository.findByUserOrderByCreatedAtDesc(user);
        List<ModelRun> modelRuns = experiments.stream()
                .filter(e -> e.getDataset().getId().equals(datasetId))
                .findFirst()
                .map(e -> e.getVersions().isEmpty() ? Collections.<ModelRun>emptyList()
                        : modelRunRepository.findByExperimentVersion(e.getVersions().get(e.getVersions().size() - 1)))
                .orElse(Collections.emptyList());

        // Two-step ask pipeline: parse first, then load only the columns the question needs.
        List<DatasetColumn> active = ColumnSelection.active(dataset);
        AskDatasetEngine.ParsedQuery parsed = askDatasetEngine.parse(request.getQuery(), active);

        Set<String> needed = new LinkedHashSet<>(parsed.mentioned);
        if (parsed.groupBy != null) needed.add(parsed.groupBy);
        if (parsed.filterColumn != null) needed.add(parsed.filterColumn);
        if ("TIME_TREND".equals(parsed.intent)) {
            active.stream()
                    .filter(c -> "DATE_TIME".equals(c.getDataType())
                            || c.getColumnName().toLowerCase(Locale.ROOT).contains("date")
                            || c.getColumnName().toLowerCase(Locale.ROOT).contains("time"))
                    .forEach(c -> needed.add(c.getColumnName()));
        }

        Map<String, List<String>> rawColumns = Collections.emptyMap();
        if (!needed.isEmpty()) {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                rawColumns = CsvColumnLoader.load(in, needed, CsvColumnLoader.DEFAULT_MAX_ROWS);
                if (parsed.filterColumn != null && parsed.filterValue != null
                        && rawColumns.containsKey(parsed.filterColumn)) {
                    rawColumns = applyEqualityFilter(rawColumns, parsed.filterColumn, parsed.filterValue);
                }
            } catch (Exception e) {
                log.warn("Ask-dataset could not load live rows for dataset {}: {}", datasetId, e.getMessage());
            }
        }

        return askDatasetEngine.answerQuery(request.getQuery(), dataset, qualityReport,
                associations, modelRuns, rawColumns);
    }

    /**
     * Applies a simple "where <column> = <value>" filter in lock-step across all loaded
     * columns so aggregates computed downstream stay row-aligned.
     */
    private Map<String, List<String>> applyEqualityFilter(Map<String, List<String>> columns,
                                                          String filterColumn, String filterValue) {
        List<String> key = columns.get(filterColumn);
        if (key == null) return columns;
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < key.size(); i++) {
            if (key.get(i) != null && key.get(i).trim().equalsIgnoreCase(filterValue.trim())) {
                keep.add(i);
            }
        }
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : columns.entrySet()) {
            List<String> values = e.getValue();
            List<String> kept = new ArrayList<>(keep.size());
            for (int idx : keep) {
                if (idx < values.size()) kept.add(values.get(idx));
            }
            filtered.put(e.getKey(), kept);
        }
        return filtered;
    }
}
