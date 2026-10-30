package com.autoscientist.service;

import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.analysis.fairness.FairnessEngine;
import com.autoscientist.analysis.feature.FeatureEngineeringEngine;
import com.autoscientist.analysis.feature.FeatureEngineeringEngine.DatasetSplit;
import com.autoscientist.analysis.ml.AutoMlEngine;
import com.autoscientist.analysis.ml.AutoMlEngine.ModelBenchmarkResult;
import com.autoscientist.analysis.ml.AutoMlEngine.PredictiveModel;
import com.autoscientist.analysis.ml.WhatIfEngine;
import com.autoscientist.analysis.xai.ExplainabilityEngine;
import com.autoscientist.dto.ml.*;
import com.autoscientist.entity.*;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.repository.ExperimentRepository;
import com.autoscientist.repository.ExperimentVersionRepository;
import com.autoscientist.repository.ModelRunRepository;
import com.autoscientist.util.ColumnSelection;
import com.autoscientist.util.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoMlService {

    /** Split/shuffle seed used by the feature engineering engine; recorded for reproducibility. */
    public static final long RANDOM_SEED = 42L;
    public static final String ENGINE_VERSION = "autoscientist-ml-1.1";

    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final FeatureEngineeringEngine featureEngineeringEngine;
    private final AutoMlEngine autoMlEngine;
    private final ExplainabilityEngine explainabilityEngine;
    private final FairnessEngine fairnessEngine;
    private final ExperimentRepository experimentRepository;
    private final ExperimentVersionRepository experimentVersionRepository;
    private final ModelRunRepository modelRunRepository;
    private final AnalysisRunService analysisRunService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Bounded cache of active trained models for instant What-If simulator predictions.
    // Entries are evicted by size so a long-lived process cannot grow without limit.
    private static final int MODEL_CACHE_MAX = 32;
    private static final Map<Long, CachedModelContext> MODEL_CACHE = new ConcurrentHashMap<>();

    private record CachedModelContext(
            PredictiveModel model,
            DatasetSplit split,
            double baselineMean,
            List<FeatureImportanceDto> featureImportances
    ) {}

    private static void cachePut(Long datasetId, CachedModelContext ctx) {
        synchronized (MODEL_CACHE) {
            if (MODEL_CACHE.size() >= MODEL_CACHE_MAX && !MODEL_CACHE.containsKey(datasetId)) {
                Iterator<Long> it = MODEL_CACHE.keySet().iterator();
                if (it.hasNext()) MODEL_CACHE.remove(it.next());
            }
            MODEL_CACHE.put(datasetId, ctx);
        }
    }

    @Transactional
    public AutoMlResponseDto trainAndBenchmark(Long datasetId, AutoMlTrainRequest request, User user) {
        long startedAt = System.currentTimeMillis();
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);

        String requestedTarget = request.getTargetColumn();
        if (requestedTarget == null || requestedTarget.isBlank()) {
            throw new BadRequestException("Target column must be specified for AutoML.");
        }

        // Case-insensitive target resolution: remember the canonical column name so
        // downstream engines (which historically resolved case-sensitively) agree.
        // The target must be an active (non-excluded) column; identifiers may only be
        // used as a target if explicitly excluded from that rule below — never silently.
        List<DatasetColumn> activeColumns = ColumnSelection.active(dataset);
        String targetCol = activeColumns.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(requestedTarget.trim()))
                .map(DatasetColumn::getColumnName)
                .findFirst()
                .orElseThrow(() -> activeColumns.stream()
                        .anyMatch(c -> c.getColumnName().equalsIgnoreCase(requestedTarget.trim()))
                        ? new BadRequestException("'" + requestedTarget.trim() + "' is excluded from analysis. Re-include it to use it as a prediction target.")
                        : new BadRequestException("Column '" + requestedTarget + "' not found in dataset."));

        if (activeColumns.stream()
                .filter(c -> c.getColumnName().equals(targetCol))
                .anyMatch(c -> "IDENTIFIER".equals(c.getDataType()))) {
            throw new BadRequestException("'" + targetCol + "' is an identifier column and cannot be used as a prediction target.");
        }

        DatasetSplit split;
        try (InputStream is = fileStorageService.getInputStream(dataset.getStoredFilename())) {
            split = featureEngineeringEngine.prepareDataset(is, activeColumns, targetCol, request.getProblemType());
        } catch (Exception e) {
            log.error("Feature engineering failed for dataset {}", datasetId, e);
            throw new BadRequestException("Feature preprocessing failed: " + e.getMessage());
        }

        if (split.getXTrain().length == 0) {
            throw new BadRequestException("No usable training rows remained after preprocessing. Check the target column for missing values.");
        }

        // Run AutoML Benchmark
        List<ModelBenchmarkResult> benchmarkResults = autoMlEngine.runAutoMlBenchmark(split);
        if (benchmarkResults.isEmpty()) {
            throw new BadRequestException("No suitable models could be trained on this dataset.");
        }

        ModelBenchmarkResult bestModel = benchmarkResults.get(0);

        // Compute Explainability (Feature Importance)
        List<FeatureImportanceDto> featureImportances = explainabilityEngine.computeFeatureImportance(split, bestModel);

        // Compute Fairness
        FairnessDto fairnessDto = fairnessEngine.evaluateFairness(
                activeColumns, split, bestModel, request.getSensitiveColumn());

        // Baseline mean for target
        double baselineMean = 0;
        for (double v : split.getYTrain()) baselineMean += v;
        if (split.getYTrain().length > 0) baselineMean /= split.getYTrain().length;

        // Repeated training on the same (dataset, target, problem type) creates a new
        // VERSION of the existing experiment instead of a duplicate experiment row.
        String expName = request.getExperimentName() != null && !request.getExperimentName().isBlank()
                ? request.getExperimentName()
                : "AutoML - " + targetCol;

        Experiment experiment = experimentRepository.findFirstByDatasetAndUserAndTargetColumnAndProblemTypeOrderByCreatedAtDesc(
                dataset, user, targetCol, split.getProblemType());

        if (experiment == null) {
            experiment = Experiment.builder()
                    .dataset(dataset)
                    .user(user)
                    .name(expName)
                    .description(request.getDescription())
                    .targetColumn(targetCol)
                    .problemType(split.getProblemType())
                    .status("COMPLETED")
                    .build();
        } else if (request.getExperimentName() != null && !request.getExperimentName().isBlank()) {
            experiment.setName(expName);
            if (request.getDescription() != null) experiment.setDescription(request.getDescription());
        }
        experiment = experimentRepository.save(experiment);

        Double bestScore = "REGRESSION".equals(split.getProblemType())
                ? bestModel.getValMetrics().getOrDefault("r2", 0.0)
                : bestModel.getValMetrics().getOrDefault("f1", 0.0);

        String prepJson = safeJson(split.getPreprocessingConfig());
        String summaryJson = safeJson(buildSummaryMap(benchmarkResults));

        int nextVersion = 1;
        List<ExperimentVersion> existingVersions = experimentVersionRepository.findByExperimentOrderByVersionNumberDesc(experiment);
        if (!existingVersions.isEmpty()) {
            nextVersion = existingVersions.get(0).getVersionNumber() + 1;
        }

        ExperimentVersion version = ExperimentVersion.builder()
                .experiment(experiment)
                .versionNumber(nextVersion)
                .preprocessingJson(prepJson)
                .featureSelectionJson(safeJson(Map.of(
                        "target", targetCol,
                        "features", split.getFeatureNames(),
                        "featureCount", split.getFeatureNames().size())))
                .modelsSummaryJson(summaryJson)
                .bestModelName(bestModel.getModelName())
                .bestMetricValue(bestScore)
                .datasetVersion(dataset.getVersion() != null ? dataset.getVersion() : 1)
                .randomSeed(RANDOM_SEED)
                .engineVersion(ENGINE_VERSION)
                .build();
        version = experimentVersionRepository.save(version);

        // Persist Model Runs
        List<ModelRunDto> runDtos = new ArrayList<>();
        for (ModelBenchmarkResult res : benchmarkResults) {
            ModelRun run = ModelRun.builder()
                    .experimentVersion(version)
                    .modelName(res.getModelName())
                    .algorithm(res.getAlgorithm())
                    .hyperparametersJson(safeJson(res.getHyperparameters()))
                    .trainMetricsJson(safeJson(res.getTrainMetrics()))
                    .valMetricsJson(safeJson(res.getValMetrics()))
                    .testMetricsJson(safeJson(res.getTestMetrics()))
                    .isBestModel(res.isBest())
                    .fairnessMetricsJson(res.isBest() ? safeJson(fairnessDto) : null)
                    .featureImportancesJson(res.isBest() ? safeJson(featureImportances) : null)
                    .evaluationJson(res.getEvaluation().isEmpty() ? null : safeJson(res.getEvaluation()))
                    .randomSeed(RANDOM_SEED)
                    .trainRows(split.getXTrain().length)
                    .valRows(split.getXVal().length)
                    .testRows(split.getXTest().length)
                    .build();
            run = modelRunRepository.save(run);

            runDtos.add(ModelRunDto.builder()
                    .id(run.getId())
                    .modelName(res.getModelName())
                    .algorithm(res.getAlgorithm())
                    .hyperparameters(res.getHyperparameters())
                    .trainMetrics(res.getTrainMetrics())
                    .valMetrics(res.getValMetrics())
                    .testMetrics(res.getTestMetrics())
                    .isBestModel(res.isBest())
                    .featureImportances(res.isBest() ? featureImportances : null)
                    .evaluation(res.getEvaluation().isEmpty() ? null : res.getEvaluation())
                    .randomSeed(RANDOM_SEED)
                    .fairnessMetrics(res.isBest() ? Map.of(
                            "disparateImpactStatus", fairnessDto.getDisparateImpactStatus(),
                            "demographicParityRatio", fairnessDto.getDemographicParityRatio(),
                            "sensitiveColumn", fairnessDto.getSensitiveColumn() == null ? "" : fairnessDto.getSensitiveColumn()
                    ) : null)
                    .build());
        }

        cachePut(datasetId, new CachedModelContext(
                bestModel.getTrainedModel(),
                split,
                baselineMean,
                featureImportances
        ));

        long duration = System.currentTimeMillis() - startedAt;
        analysisRunService.record(dataset, AnalysisOperations.AUTOML_TRAIN, Map.of("target", targetCol, "problemType", split.getProblemType()),
                String.format("Trained %d models on target '%s'; best %s (v%d)", benchmarkResults.size(), targetCol, bestModel.getModelName(), nextVersion),
                Map.of("bestModel", bestModel.getModelName(), "bestScore", bestScore, "version", nextVersion),
                duration, user);
        if (!featureImportances.isEmpty()) {
            analysisRunService.record(dataset, AnalysisOperations.XAI_EXPLAIN, Map.of("target", targetCol),
                    "Permutation importance computed for " + bestModel.getModelName(),
                    Map.of("topFeature", featureImportances.get(0).getFeatureName()), duration, user);
        }
        analysisRunService.record(dataset, AnalysisOperations.FAIRNESS, Map.of("target", targetCol),
                "Fairness audit: " + fairnessDto.getDisparateImpactStatus() + " (sensitive column: " + fairnessDto.getSensitiveColumn() + ")",
                Map.of("status", fairnessDto.getDisparateImpactStatus()), duration, user);

        // Formulate Scientific Summary
        String metricName = "REGRESSION".equals(split.getProblemType()) ? "R²" : "Macro-F1";
        String topDriver = featureImportances.isEmpty() ? "None" : featureImportances.get(0).getFeatureName();
        String summary = String.format("AutoML evaluated %d candidate architectures on target '%s'. Best performer is %s with validation %s of %.3f. The top predictive driver is '%s'. Fairness verification: %s.",
                benchmarkResults.size(), targetCol, bestModel.getModelName(), metricName, bestScore, topDriver, fairnessDto.getDisparateImpactStatus());

        return AutoMlResponseDto.builder()
                .experimentId(experiment.getId())
                .experimentVersionId(version.getId())
                .experimentName(expName)
                .problemType(split.getProblemType())
                .targetColumn(targetCol)
                .models(runDtos)
                .bestModel(runDtos.get(0))
                .featureImportances(featureImportances)
                .explanationStatus(featureImportances.isEmpty() ? "UNAVAILABLE" : "AVAILABLE")
                .fairness(fairnessDto)
                .scientificSummary(summary)
                .build();
    }

    @Transactional
    public WhatIfResponseDto predictWhatIf(Long datasetId, WhatIfRequestDto req, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        CachedModelContext context = loadModelContext(dataset, user);

        DatasetSplit split = context.split();
        PredictiveModel model = context.model();
        List<String> featureNames = split.getFeatureNames();
        Map<String, Object> inputVals = req.getFeatureValues() != null ? req.getFeatureValues() : Collections.emptyMap();

        Map<String, Object> preproc = split.getPreprocessingConfig();
        List<WhatIfEngine.RawFeature> schema = WhatIfEngine.readSchema(preproc);
        List<Double> scalerMean = doubleList(preproc == null ? null : preproc.get("scalerMean"));
        List<Double> scalerStd = doubleList(preproc == null ? null : preproc.get("scalerStd"));

        // Reference profile: median for numeric features, mode for categorical ones.
        WhatIfEngine.Encoded reference = WhatIfEngine.encode(schema, featureNames, scalerMean, scalerStd,
                WhatIfEngine.emptyInputs());
        // Scenario: user supplied raw values, encoded exactly like the training matrix.
        WhatIfEngine.Encoded scenario = WhatIfEngine.encode(schema, featureNames, scalerMean, scalerStd, inputVals);

        double pred = model.predictSingle(scenario.vector);
        double referencePred = model.predictSingle(reference.vector);
        double delta = pred - referencePred;

        String predClass = null;
        Double conf = null;
        String refClass = null;
        if ("CLASSIFICATION".equals(split.getProblemType())) {
            int classIdx = (int) Math.round(pred);
            predClass = split.getReverseClassMap() != null ? split.getReverseClassMap().get(classIdx) : String.valueOf(classIdx);
            if (predClass == null) predClass = "Class " + classIdx;

            // Honest confidence: only reported when the model can produce a score.
            double proba = model.predictProba(scenario.vector);
            if (!Double.isNaN(proba) && proba >= 0.0 && proba <= 1.0) {
                conf = Math.max(proba, 1.0 - proba);
            }
            int refIdx = (int) Math.round(referencePred);
            if (split.getReverseClassMap() != null) {
                refClass = split.getReverseClassMap().get(refIdx);
            }
        }

        List<String> influences = context.featureImportances().stream()
                .limit(3)
                .map(f -> f.getFeatureName() + " (" + f.getRelativePercentage() + "%)")
                .collect(Collectors.toList());

        // Per-feature attribution: revert one feature at a time to its reference value.
        boolean exact = model.getWeights() != null && model.getWeights().length > 0;
        List<WhatIfContributionDto> contributions = new ArrayList<>();
        for (WhatIfEngine.RawFeature rf : schema) {
            Object scenarioValue = scenario.appliedValues.get(rf.name);
            Object referenceValue = reference.appliedValues.get(rf.name);
            if (Objects.equals(String.valueOf(scenarioValue), String.valueOf(referenceValue))) continue;

            Map<String, Object> reverted = new LinkedHashMap<>(inputVals);
            reverted.remove(rf.name);
            WhatIfEngine.Encoded ablated = WhatIfEngine.encode(schema, featureNames, scalerMean, scalerStd, reverted);
            double ablatedPred = model.predictSingle(ablated.vector);
            double contribution = pred - ablatedPred;

            contributions.add(WhatIfContributionDto.builder()
                    .featureName(rf.name)
                    .kind(rf.kind)
                    .scenarioValue(scenarioValue)
                    .referenceValue(referenceValue)
                    .scenarioPrediction(round3(pred))
                    .referencePrediction(round3(ablatedPred))
                    .delta(round3(contribution))
                    .absoluteContribution(round3(Math.abs(contribution)))
                    .direction(contribution > 0 ? "INCREASE" : contribution < 0 ? "DECREASE" : "NEUTRAL")
                    .build());
        }
        contributions.sort(Comparator.comparing(
                (WhatIfContributionDto c) -> c.getAbsoluteContribution() == null ? 0.0 : c.getAbsoluteContribution())
                .reversed());

        List<WhatIfFeatureDto> featureSchema = schema.stream()
                .map(rf -> WhatIfFeatureDto.builder()
                        .name(rf.name)
                        .kind(rf.kind)
                        .referenceValue(rf.imputeValue)
                        .min(rf.min)
                        .max(rf.max)
                        .categories(rf.categories)
                        .build())
                .collect(Collectors.toList());

        analysisRunService.record(dataset, AnalysisOperations.WHATIF, Map.of("target", split.getTargetColumn()),
                "What-if simulation on " + split.getTargetColumn(), null, null, user);

        return WhatIfResponseDto.builder()
                .targetColumn(split.getTargetColumn())
                .problemType(split.getProblemType())
                .predictedValue(round3(pred))
                .predictedClass(predClass)
                .confidence(conf)
                .confidenceAvailable(conf != null)
                .baselineMean(round3(context.baselineMean()))
                .deltaFromBaseline(round3(delta))
                .keyInfluences(influences)
                .referencePrediction(round3(referencePred))
                .referenceDescription("Reference profile: training " + (refClass != null ? "class " + refClass
                        : "median for numeric and mode for categorical features"))
                .featureSchema(featureSchema)
                .contributions(contributions)
                .contributionMethod("SINGLE-FEATURE ABLATION")
                .contributionsExact(exact)
                .appliedValues(scenario.appliedValues)
                .warnings(scenario.warnings)
                .unrecognisedInputs(scenario.unrecognised)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Double> doubleList(Object value) {
        List<Double> out = new ArrayList<>();
        if (value instanceof List) {
            for (Object o : (List<Object>) value) {
                if (o instanceof Number) out.add(((Number) o).doubleValue());
                else out.add(null);
            }
        }
        return out;
    }

    /**
     * Returns the trained model for the dataset, re-running the recorded experiment
     * when this process has no warm model in memory.
     */
    private CachedModelContext loadModelContext(Dataset dataset, User user) {
        Long datasetId = dataset.getId();
        CachedModelContext context = MODEL_CACHE.get(datasetId);
        if (context != null) return context;

        // No warm model in this process: re-run the recorded experiment so the
        // simulation uses the same target/problem type the user trained before.
        List<Experiment> exps = experimentRepository.findByDatasetAndUserOrderByCreatedAtDesc(dataset, user);
        Experiment matchingExp = exps.stream()
                .filter(e -> e.getDataset().getId().equals(datasetId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No trained model exists for dataset " + datasetId
                        + ". Please run AutoML training first."));

        AutoMlTrainRequest autoTrainReq = AutoMlTrainRequest.builder()
                .targetColumn(matchingExp.getTargetColumn())
                .problemType(matchingExp.getProblemType())
                .build();
        trainAndBenchmark(datasetId, autoTrainReq, user);
        context = MODEL_CACHE.get(datasetId);
        if (context == null) {
            throw new BadRequestException("Model could not be reloaded for simulation. Please re-run AutoML training.");
        }
        return context;
    }

    /**
     * Describes the raw inputs the trained model expects, so the UI can render
     * numeric fields and categorical selectors with the correct reference values.
     *
     * <p>Not read-only: on a cold cache {@link #loadModelContext} legitimately
     * rebuilds and persists the recorded experiment. MySQL enforces read-only
     * connections, so marking this read-only caused an HTTP 500 after every
     * backend restart.</p>
     */
    @Transactional
    public WhatIfSchemaDto getWhatIfSchema(Long datasetId, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        CachedModelContext context = loadModelContext(dataset, user);
        DatasetSplit split = context.split();
        Map<String, Object> preproc = split.getPreprocessingConfig();
        List<WhatIfEngine.RawFeature> schema = WhatIfEngine.readSchema(preproc);
        List<Double> scalerMean = doubleList(preproc == null ? null : preproc.get("scalerMean"));
        List<Double> scalerStd = doubleList(preproc == null ? null : preproc.get("scalerStd"));

        WhatIfEngine.Encoded reference = WhatIfEngine.encode(schema, split.getFeatureNames(), scalerMean, scalerStd,
                WhatIfEngine.emptyInputs());
        double referencePred = context.model().predictSingle(reference.vector);
        String referenceClass = null;
        if ("CLASSIFICATION".equals(split.getProblemType()) && split.getReverseClassMap() != null) {
            referenceClass = split.getReverseClassMap().get((int) Math.round(referencePred));
        }

        List<WhatIfFeatureDto> features = schema.stream()
                .map(rf -> WhatIfFeatureDto.builder()
                        .name(rf.name)
                        .kind(rf.kind)
                        .referenceValue(rf.imputeValue)
                        .min(rf.min)
                        .max(rf.max)
                        .categories(rf.categories)
                        .build())
                .collect(Collectors.toList());

        return WhatIfSchemaDto.builder()
                .targetColumn(split.getTargetColumn())
                .problemType(split.getProblemType())
                .features(features)
                .referencePrediction(round3(referencePred))
                .referenceClass(referenceClass)
                .referenceDescription("Reference profile: training " + (referenceClass != null
                        ? "class " + referenceClass : "median for numeric and mode for categorical features"))
                .build();
    }

    /**
     * Computes the marginal feature-effect curve across a grid of values (or distinct categories)
     * for a selected feature, evaluated on the warm fitted model holding other variables at
     * their training reference baseline.
     *
     * <p>Not read-only for the same cold-cache rebuild reason as
     * {@link #getWhatIfSchema(Long, User)}.</p>
     */
    @Transactional
    public FeatureEffectDto computeFeatureEffect(Long datasetId, String featureName, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        CachedModelContext context = loadModelContext(dataset, user);
        DatasetSplit split = context.split();
        Map<String, Object> preproc = split.getPreprocessingConfig();
        List<WhatIfEngine.RawFeature> schema = WhatIfEngine.readSchema(preproc);
        List<Double> scalerMean = doubleList(preproc == null ? null : preproc.get("scalerMean"));
        List<Double> scalerStd = doubleList(preproc == null ? null : preproc.get("scalerStd"));

        if (schema.isEmpty()) {
            return FeatureEffectDto.builder()
                    .featureName(featureName)
                    .supported(false)
                    .unsupportedReason("No features available in the trained model schema.")
                    .build();
        }

        WhatIfEngine.RawFeature targetFeature = null;
        if (featureName == null || featureName.isBlank()) {
            targetFeature = schema.get(0);
        } else {
            String trimmed = featureName.trim();
            targetFeature = schema.stream()
                    .filter(rf -> rf.name.equalsIgnoreCase(trimmed))
                    .findFirst()
                    .orElse(null);
        }

        if (targetFeature == null) {
            return FeatureEffectDto.builder()
                    .featureName(featureName)
                    .supported(false)
                    .unsupportedReason("Feature '" + featureName + "' is not an active predictor in the trained model (it may be the target variable or an identifier).")
                    .build();
        }

        WhatIfEngine.Encoded reference = WhatIfEngine.encode(schema, split.getFeatureNames(), scalerMean, scalerStd,
                WhatIfEngine.emptyInputs());
        double refPred = context.model().predictSingle(reference.vector);
        String refClass = null;
        if ("CLASSIFICATION".equals(split.getProblemType()) && split.getReverseClassMap() != null) {
            refClass = split.getReverseClassMap().get((int) Math.round(refPred));
        }

        Double refVal = targetFeature.imputeValue instanceof Number
                ? ((Number) targetFeature.imputeValue).doubleValue()
                : null;

        List<FeatureEffectPointDto> points = new ArrayList<>();
        List<FeatureEffectCategoryDto> categories = new ArrayList<>();

        if ("NUMERIC".equalsIgnoreCase(targetFeature.kind)) {
            double min = targetFeature.min != null ? targetFeature.min : 0.0;
            double max = targetFeature.max != null ? targetFeature.max : 1.0;
            if (min >= max) {
                min -= 1.0;
                max += 1.0;
            }

            int numSteps = 15;
            for (int i = 0; i < numSteps; i++) {
                double xVal = min + (max - min) * i / (double) (numSteps - 1);
                Map<String, Object> inputs = Collections.singletonMap(targetFeature.name, xVal);
                WhatIfEngine.Encoded enc = WhatIfEngine.encode(schema, split.getFeatureNames(), scalerMean, scalerStd, inputs);
                double p = context.model().predictSingle(enc.vector);
                double proba = context.model().predictProba(enc.vector);
                Double probaVal = (Double.isNaN(proba) || Double.isInfinite(proba)) ? null : round3(proba);
                points.add(FeatureEffectPointDto.builder()
                        .x(round3(xVal))
                        .prediction(round3(p))
                        .probability(probaVal)
                        .build());
            }
        } else {
            List<String> catList = targetFeature.categories;
            if (catList == null || catList.isEmpty()) {
                catList = List.of("Default");
            }
            for (String cat : catList) {
                Map<String, Object> inputs = Collections.singletonMap(targetFeature.name, cat);
                WhatIfEngine.Encoded enc = WhatIfEngine.encode(schema, split.getFeatureNames(), scalerMean, scalerStd, inputs);
                double p = context.model().predictSingle(enc.vector);
                double proba = context.model().predictProba(enc.vector);
                Double probaVal = (Double.isNaN(proba) || Double.isInfinite(proba)) ? null : round3(proba);
                categories.add(FeatureEffectCategoryDto.builder()
                        .category(cat)
                        .prediction(round3(p))
                        .probability(probaVal)
                        .build());
            }
        }

        String scientificNote = "Marginal conditional prediction curve for '" + targetFeature.name
                + "' holding other predictors at baseline ("
                + ("NUMERIC".equalsIgnoreCase(targetFeature.kind) ? "median" : "mode")
                + "). Reflects the benchmarked model's learned response function, not a causal claim.";

        return FeatureEffectDto.builder()
                .featureName(targetFeature.name)
                .kind(targetFeature.kind)
                .problemType(split.getProblemType())
                .targetColumn(split.getTargetColumn())
                .referenceValue(refVal)
                .referencePrediction(round3(refPred))
                .referenceClass(refClass)
                .points(points)
                .categories(categories)
                .supported(true)
                .scientificNote(scientificNote)
                .build();
    }

    private static double round3(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.round(v * 1000.0) / 1000.0;
    }

    @Transactional(readOnly = true)
    public AutoMlResponseDto getLatestBenchmark(Long datasetId, User user) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, user);
        CachedModelContext context = MODEL_CACHE.get(datasetId);

        List<Experiment> exps = experimentRepository.findByDatasetAndUserOrderByCreatedAtDesc(dataset, user);
        if (exps.isEmpty()) {
            return null;
        }

        Experiment experiment = exps.get(0);
        List<ExperimentVersion> versions = experimentVersionRepository.findByExperimentOrderByVersionNumberDesc(experiment);
        if (versions.isEmpty()) {
            return null;
        }

        ExperimentVersion version = versions.get(0);
        List<ModelRun> runs = modelRunRepository.findByExperimentVersion(version);

        List<ModelRunDto> runDtos = new ArrayList<>();
        ModelRunDto bestModelDto = null;
        List<FeatureImportanceDto> persistedImportances = Collections.emptyList();
        FairnessDto persistedFairness = null;

        for (ModelRun mr : runs) {
            Map<String, Double> valM = parseMetrics(mr.getValMetricsJson());
            Map<String, Double> testM = parseMetrics(mr.getTestMetricsJson());
            Map<String, Double> trainM = parseMetrics(mr.getTrainMetricsJson());

            List<FeatureImportanceDto> runImportances = parseImportances(mr.getFeatureImportancesJson());

            ModelRunDto dto = ModelRunDto.builder()
                    .id(mr.getId())
                    .modelName(mr.getModelName())
                    .algorithm(mr.getAlgorithm())
                    .hyperparameters(parseMap(mr.getHyperparametersJson()))
                    .trainMetrics(trainM)
                    .valMetrics(valM)
                    .testMetrics(testM)
                    .isBestModel(Boolean.TRUE.equals(mr.getIsBestModel()))
                    .fairnessMetrics(parseMap(mr.getFairnessMetricsJson()))
                    .featureImportances(runImportances)
                    .evaluation(parseMap(mr.getEvaluationJson()))
                    .randomSeed(mr.getRandomSeed())
                    .trainRows(mr.getTrainRows())
                    .valRows(mr.getValRows())
                    .testRows(mr.getTestRows())
                    .build();

            runDtos.add(dto);
            if (Boolean.TRUE.equals(mr.getIsBestModel()) && bestModelDto == null) {
                bestModelDto = dto;
                persistedImportances = runImportances;
                persistedFairness = readFairness(mr.getFairnessMetricsJson());
            }
        }

        if (bestModelDto == null && !runDtos.isEmpty()) {
            bestModelDto = runDtos.get(0);
        }

        // Explanations: prefer persisted importances (survive restart), fall back to the
        // warm cache. Never claim availability when neither exists.
        List<FeatureImportanceDto> featList = !persistedImportances.isEmpty()
                ? persistedImportances
                : (context != null && context.featureImportances() != null && !context.featureImportances().isEmpty()
                    ? context.featureImportances()
                    : Collections.emptyList());

        String explanationStatus;
        if (!persistedImportances.isEmpty()) {
            explanationStatus = "AVAILABLE";
        } else if (!featList.isEmpty()) {
            explanationStatus = "AVAILABLE_IN_MEMORY";
        } else {
            explanationStatus = "UNAVAILABLE_RETRAIN";
        }

        FairnessDto fairnessDto = persistedFairness;
        if (fairnessDto == null && bestModelDto != null && bestModelDto.getFairnessMetrics() != null
                && !bestModelDto.getFairnessMetrics().isEmpty()) {
            // Legacy rows: reconstruct only from values that were actually stored.
            Map<String, Object> fm = bestModelDto.getFairnessMetrics();
            Double ratio = fm.get("demographicParityRatio") instanceof Number n ? n.doubleValue() : null;
            fairnessDto = FairnessDto.builder()
                    .sensitiveColumn(fm.get("sensitiveColumn") != null ? String.valueOf(fm.get("sensitiveColumn")) : null)
                    .demographicParityRatio(ratio != null ? ratio : 1.0)
                    .disparateImpactStatus(fm.get("disparateImpactStatus") != null
                            ? String.valueOf(fm.get("disparateImpactStatus")) : "UNKNOWN")
                    .recommendation(fm.get("recommendation") != null ? String.valueOf(fm.get("recommendation")) : null)
                    .build();
        }

        return AutoMlResponseDto.builder()
                .experimentId(experiment.getId())
                .experimentVersionId(version.getId())
                .experimentName(experiment.getName())
                .problemType(experiment.getProblemType())
                .targetColumn(experiment.getTargetColumn())
                .models(runDtos)
                .bestModel(bestModelDto)
                .featureImportances(featList)
                .explanationStatus(explanationStatus)
                .fairness(fairnessDto)
                .datasetVersion(version.getDatasetVersion())
                .randomSeed(version.getRandomSeed())
                .engineVersion(version.getEngineVersion())
                .scientificSummary("AutoML benchmark history retrieved for target variable '" + experiment.getTargetColumn()
                        + "' (version " + version.getVersionNumber() + ").")
                .build();
    }

    private FairnessDto readFairness(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> m = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (m.isEmpty() || !m.containsKey("sensitiveColumn")) return null;
            FairnessDto.FairnessDtoBuilder b = FairnessDto.builder()
                    .sensitiveColumn(m.get("sensitiveColumn") != null ? String.valueOf(m.get("sensitiveColumn")) : null)
                    .disparateImpactStatus(m.get("disparateImpactStatus") != null ? String.valueOf(m.get("disparateImpactStatus")) : null)
                    .recommendation(m.get("recommendation") != null ? String.valueOf(m.get("recommendation")) : null);
            if (m.get("demographicParityRatio") instanceof Number n) b.demographicParityRatio(n.doubleValue());
            if (m.get("groupPositiveRates") instanceof Map<?, ?> g) {
                Map<String, Double> conv = new LinkedHashMap<>();
                g.forEach((k, v) -> { if (v instanceof Number n) conv.put(String.valueOf(k), n.doubleValue()); });
                b.groupPositiveRates(conv);
            }
            if (m.get("groupErrorRates") instanceof Map<?, ?> g) {
                Map<String, Double> conv = new LinkedHashMap<>();
                g.forEach((k, v) -> { if (v instanceof Number n) conv.put(String.valueOf(k), n.doubleValue()); });
                b.groupErrorRates(conv);
            }
            return b.build();
        } catch (Exception e) {
            return null;
        }
    }

    private List<FeatureImportanceDto> parseImportances(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<FeatureImportanceDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Double> parseMetrics(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> buildSummaryMap(List<ModelBenchmarkResult> results) {
        Map<String, Object> map = new HashMap<>();
        for (ModelBenchmarkResult r : results) {
            map.put(r.getModelName(), Map.of(
                    "algorithm", r.getAlgorithm(),
                    "valMetrics", r.getValMetrics(),
                    "isBest", r.isBest()
            ));
        }
        return map;
    }
}
