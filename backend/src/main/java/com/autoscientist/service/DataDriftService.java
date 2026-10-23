package com.autoscientist.service;

import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.analysis.drift.DataDriftEngine;
import com.autoscientist.dto.drift.DatasetDriftReportDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DriftSnapshot;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.DriftSnapshotRepository;
import com.autoscientist.util.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataDriftService {

    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final DataDriftEngine dataDriftEngine;
    private final DriftSnapshotRepository driftSnapshotRepository;
    private final AnalysisRunService analysisRunService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public DatasetDriftReportDto compareDatasets(Long referenceDatasetId, Long targetDatasetId, User user) {
        if (referenceDatasetId == null || targetDatasetId == null) {
            throw new BadRequestException("Both referenceDatasetId and targetDatasetId are required for drift analysis.");
        }
        Dataset refDataset = datasetService.getDatasetEntityWithOwnership(referenceDatasetId, user);
        Dataset targetDataset = datasetService.getDatasetEntityWithOwnership(targetDatasetId, user);

        try (InputStream refStream = fileStorageService.getInputStream(refDataset.getStoredFilename());
             InputStream targetStream = fileStorageService.getInputStream(targetDataset.getStoredFilename())) {

            long started = System.currentTimeMillis();
            DatasetDriftReportDto report = dataDriftEngine.analyzeDrift(refDataset, refStream, targetDataset, targetStream);

            // Persist so reports and the evidence graph can cite real drift results later.
            driftSnapshotRepository.save(DriftSnapshot.builder()
                    .dataset(targetDataset)
                    .referenceDatasetId(refDataset.getId())
                    .referenceDatasetName(refDataset.getName())
                    .overallStatus(report.getOverallStatus())
                    .overallDriftIndex(report.getOverallDriftIndex())
                    .reportJson(objectMapper.writeValueAsString(report))
                    .build());

            analysisRunService.record(targetDataset, AnalysisOperations.DRIFT,
                    Map.of("referenceId", referenceDatasetId, "targetId", targetDatasetId),
                    String.format("Drift vs '%s': %s (avg PSI=%.4f, %d/%d features drifted)",
                            refDataset.getName(), report.getOverallStatus(), report.getOverallDriftIndex(),
                            report.getDriftedFeaturesCount(), report.getTotalFeaturesAnalyzed()),
                    Map.of("status", report.getOverallStatus(), "index", report.getOverallDriftIndex()),
                    System.currentTimeMillis() - started, user);

            return report;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Drift comparison failed between {} and {}", referenceDatasetId, targetDatasetId, e);
            throw new BadRequestException("Data drift analysis failed: " + e.getMessage());
        }
    }
}
