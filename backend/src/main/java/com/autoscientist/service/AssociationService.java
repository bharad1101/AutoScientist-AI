package com.autoscientist.service;

import com.autoscientist.analysis.association.AssociationEngine;
import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.dto.analysis.AssociationDto;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import com.autoscientist.repository.AssociationRepository;
import com.autoscientist.util.ColumnSelection;
import com.autoscientist.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssociationService {

    private final AssociationRepository associationRepository;
    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final AssociationEngine associationEngine;
    private final AnalysisRunService analysisRunService;

    @Transactional
    public List<AssociationDto> computeAndSaveAssociations(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);

        // Only non-identifier, non-excluded variables enter association discovery.
        List<com.autoscientist.entity.DatasetColumn> analyticCols = ColumnSelection.analytic(dataset);

        // Delete existing associations for fresh recomputation
        associationRepository.deleteByDataset(dataset);

        List<Association> computed;
        long started = System.currentTimeMillis();
        try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
            computed = associationEngine.discoverAssociations(in, dataset, analyticCols);
        } catch (Exception e) {
            throw new RuntimeException("Association discovery failed: " + e.getMessage(), e);
        }

        computed = associationRepository.saveAll(computed);

        long significant = computed.stream().filter(a -> Boolean.TRUE.equals(a.getIsSignificant())).count();
        analysisRunService.record(dataset, AnalysisOperations.ASSOCIATIONS, Map.of("scope", "analytic"),
                String.format("Discovered %d association(s), %d significant at their test thresholds",
                        computed.size(), significant),
                Map.of("total", computed.size(), "significant", significant),
                System.currentTimeMillis() - started, owner);

        return computed.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<AssociationDto> getAssociations(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<Association> list = associationRepository.findByDatasetOrderByRankScoreDesc(dataset);

        if (list.isEmpty()) {
            // Lazy compute if not yet generated (must run in a write transaction,
            // which is why this method is not read-only).
            return computeAndSaveAssociations(datasetId, owner);
        }

        return list.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public AssociationDto mapToDto(Association a) {
        return AssociationDto.builder()
                .id(a.getId())
                .datasetId(a.getDataset().getId())
                .var1(a.getVar1())
                .var2(a.getVar2())
                .var1Type(a.getVar1Type())
                .var2Type(a.getVar2Type())
                .testType(a.getTestType())
                .statistic(a.getStatistic())
                .pValue(a.getPValue())
                .effectSize(a.getEffectSize())
                .sampleSize(a.getSampleSize())
                .degreesOfFreedom(a.getDegreesOfFreedom())
                .isSignificant(a.getIsSignificant())
                .interpretation(a.getInterpretation())
                .rankScore(a.getRankScore())
                .ciLower(a.getCiLower())
                .ciUpper(a.getCiUpper())
                .ciLevel(a.getCiLevel())
                .ciMethod(a.getCiMethod())
                .ciTarget(a.getCiTarget())
                .ciUnavailableReason(a.getCiUnavailableReason())
                .effectSizeCiLower(a.getEffectSizeCiLower())
                .effectSizeCiUpper(a.getEffectSizeCiUpper())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
