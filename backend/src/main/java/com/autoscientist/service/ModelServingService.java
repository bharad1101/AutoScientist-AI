package com.autoscientist.service;

import com.autoscientist.analysis.ml.ClassificationSemantics;
import com.autoscientist.analysis.ml.ModelArtifactCodec;
import com.autoscientist.analysis.ml.ServingArtifact;
import com.autoscientist.analysis.ml.WhatIfEngine;
import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.ml.ModelPredictionResponseDto;
import com.autoscientist.entity.ModelArtifact;
import com.autoscientist.entity.User;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.ModelArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serves predictions from persisted model artifacts. Inference replays the
 * exact preprocessing transformation recorded at training time; no synthetic
 * or default predictions are returned when an artifact cannot be loaded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelServingService {

    private final ModelArtifactRepository modelArtifactRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<ModelPredictionResponseDto>> predict(
            Long modelId, Map<String, Object> payload, User user) {
        ModelArtifact artifactEntity = modelArtifactRepository.findById(modelId)
                .orElseThrow(() -> new ResourceNotFoundException("Model not found with id: " + modelId));

        Long ownerId = artifactEntity.getExperimentVersion().getExperiment().getUser().getId();
        if (!ownerId.equals(user.getId())) {
            throw new ResourceNotFoundException("Model not found with id: " + modelId);
        }

        ServingArtifact artifact;
        try {
            artifact = ModelArtifactCodec.deserialize(artifactEntity.getPayload());
        } catch (Exception e) {
            log.error("Unable to deserialize model artifact {}", modelId, e);
            throw new IllegalStateException("Model artifact is unreadable; retrain the model to enable predictions.");
        }

        Map<String, Object> preproc = artifact.getPreprocessingConfig();
        List<Double> scalerMean = doubleList(preproc == null ? null : preproc.get("scalerMean"));
        List<Double> scalerStd = doubleList(preproc == null ? null : preproc.get("scalerStd"));
        List<WhatIfEngine.RawFeature> schema = WhatIfEngine.readSchema(preproc);

        WhatIfEngine.Encoded encoded = WhatIfEngine.encode(
                schema, artifact.getFeatureNames(), scalerMean, scalerStd, payload);

        double rawPrediction = artifact.getModel().predictSingle(encoded.vector);
        if (!Double.isFinite(rawPrediction)) {
            throw new IllegalStateException("Model produced a non-finite prediction.");
        }

        String predictedClass = null;
        Integer predictedClassIndex = null;
        Double probability = null;
        boolean probabilityAvailable = false;
        Integer positiveClassIndex = null;
        String positiveClassLabel = null;
        String probabilityStatus = ClassificationSemantics.PROBABILITY_STATUS_NOT_BINARY;
        if ("CLASSIFICATION".equals(artifact.getProblemType())) {
            int classIndex = (int) Math.round(rawPrediction);
            Map<Integer, String> reverse = artifact.getReverseClassMap();
            predictedClassIndex = classIndex;
            predictedClass = reverse != null
                    ? reverse.getOrDefault(classIndex, String.valueOf(classIndex))
                    : String.valueOf(classIndex);
            positiveClassIndex = ClassificationSemantics.positiveClassIndex(artifact.getProblemType(), reverse);
            positiveClassLabel = ClassificationSemantics.positiveClassLabel(artifact.getProblemType(), reverse);

            if (positiveClassIndex != null) {
                double score = artifact.getModel().predictProba(encoded.vector);
                if (ClassificationSemantics.isUsableScore(score)) {
                    probability = score;
                    probabilityAvailable = true;
                    probabilityStatus = ClassificationSemantics.PROBABILITY_STATUS_AVAILABLE;
                } else {
                    probabilityStatus = ClassificationSemantics.PROBABILITY_STATUS_UNAVAILABLE_NO_SCORES;
                }
            }
        }

        ModelPredictionResponseDto response = ModelPredictionResponseDto.builder()
                .modelId(modelId)
                .modelName(artifactEntity.getModelName())
                .algorithm(artifact.getAlgorithm())
                .problemType(artifact.getProblemType())
                .targetColumn(artifact.getTargetColumn())
                .prediction(rawPrediction)
                .predictedClass(predictedClass)
                .predictedClassIndex(predictedClassIndex)
                .probability(probability)
                .probabilityAvailable(probabilityAvailable)
                .positiveClassProbability(probability)
                .positiveClassIndex(positiveClassIndex)
                .positiveClassLabel(positiveClassLabel)
                .probabilityClassIndex(positiveClassIndex)
                .probabilityClassLabel(positiveClassLabel)
                .probabilityStatus(probabilityStatus)
                .decisionThreshold(ClassificationSemantics.BINARY_DECISION_THRESHOLD)
                .appliedValues(encoded.appliedValues)
                .warnings(encoded.warnings)
                .unrecognisedInputs(encoded.unrecognised)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Prediction generated", response));
    }

    private static List<Double> doubleList(Object value) {
        List<Double> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number number) {
                    out.add(number.doubleValue());
                }
            }
        }
        return out;
    }
}
