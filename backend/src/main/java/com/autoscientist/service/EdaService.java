package com.autoscientist.service;

import com.autoscientist.analysis.core.AnalysisOperations;
import com.autoscientist.analysis.eda.EdaEngine;
import com.autoscientist.util.BoundedCache;
import com.autoscientist.dto.analysis.BoxPlotDto;
import com.autoscientist.dto.analysis.CorrelationMatrixDto;
import com.autoscientist.dto.analysis.GroupedSummaryDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
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
public class EdaService {

    private final DatasetService datasetService;
    private final FileStorageService fileStorageService;
    private final EdaEngine edaEngine;
    private final AnalysisRunService analysisRunService;
    private final BoundedCache<String, Object> cache = new BoundedCache<>(64);

    @Transactional(readOnly = true)
    public CorrelationMatrixDto getCorrelationMatrix(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.analytic(dataset);
        if (cols.stream().filter(c -> "NUMERIC".equals(c.getDataType())).count() < 2) {
            throw new BadRequestException(
                    "Correlation analysis needs at least two numeric, non-identifier variables.");
        }

        long started = System.currentTimeMillis();
        EdaEngine.CorrelationMatrix matrix = cached(cacheKey(dataset, "correlations", null), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                EdaEngine.CorrelationMatrix computed = edaEngine.computeCorrelationMatrix(in, cols);
                analysisRunService.record(dataset, AnalysisOperations.EDA_CORRELATIONS, Map.of("scope", "numeric-analytic"),
                        "Correlation matrix computed over " + computed.getVariables().size() + " variable(s)",
                        computed.getVariables(), System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute correlation matrix: " + e.getMessage(), e);
            }
        });
        return CorrelationMatrixDto.builder()
                .variables(matrix.getVariables())
                .matrix(matrix.getMatrix())
                .pValues(matrix.getPValues())
                .sampleSizes(matrix.getSampleSizes())
                .build();
    }

    @Transactional(readOnly = true)
    public List<BoxPlotDto> getBoxPlots(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.analytic(dataset);
        if (cols.stream().filter(c -> "NUMERIC".equals(c.getDataType())).count() < 1) {
            throw new BadRequestException("Box plots need at least one numeric, non-identifier variable.");
        }

        long started = System.currentTimeMillis();
        List<EdaEngine.BoxPlotData> boxPlots = cached(cacheKey(dataset, "boxplots", null), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                List<EdaEngine.BoxPlotData> computed = edaEngine.computeBoxPlots(in, cols);
                analysisRunService.record(dataset, AnalysisOperations.EDA_BOXPLOTS, Map.of("scope", "numeric-analytic"),
                        "Distribution plots computed for " + computed.size() + " variable(s)",
                        computed.stream().map(EdaEngine.BoxPlotData::getVariable).collect(Collectors.toList()),
                        System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute box plots: " + e.getMessage(), e);
            }
        });
        return boxPlots.stream()
                .map(b -> BoxPlotDto.builder()
                        .variable(b.getVariable())
                        .min(b.getMin())
                        .q1(b.getQ1())
                        .median(b.getMedian())
                        .q3(b.getQ3())
                        .max(b.getMax())
                        .iqr(b.getIqr())
                        .outliers(b.getOutliers())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GroupedSummaryDto> getGroupedSummary(Long datasetId, String numVar, String catVar, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);

        // Validate the requested variables against the active, analytic column set so an
        // identifier can never silently enter a group comparison.
        List<DatasetColumn> active = ColumnSelection.active(dataset);
        DatasetColumn numeric = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(numVar))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown numeric variable: " + numVar));
        DatasetColumn categorical = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(catVar))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown categorical variable: " + catVar));

        if (ColumnSelection.isIdentifier(numeric)) {
            throw new BadRequestException("'" + numVar + "' is an identifier column and cannot be summarised. "
                    + "Include it explicitly from the dataset page if this is intentional.");
        }
        if (!"NUMERIC".equals(numeric.getDataType())) {
            throw new BadRequestException("'" + numVar + "' is not numeric (detected: " + numeric.getDataType() + ").");
        }
        if (ColumnSelection.isIdentifier(categorical)) {
            throw new BadRequestException("'" + catVar + "' is an identifier column and cannot be used for grouping.");
        }

        long started = System.currentTimeMillis();
        List<GroupedSummaryDto> dtos = cached(cacheKey(dataset, "grouped", numeric.getColumnName() + "|" + categorical.getColumnName()), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                List<EdaEngine.GroupSummary> summaries = edaEngine.computeGroupedSummary(in,
                        numeric.getColumnName(), categorical.getColumnName());
                analysisRunService.record(dataset, AnalysisOperations.EDA_GROUPED,
                        Map.of("numeric", numeric.getColumnName(), "categorical", categorical.getColumnName()),
                        "Grouped comparison of " + numeric.getColumnName() + " by " + categorical.getColumnName(),
                        summaries.size(), System.currentTimeMillis() - started, owner);
                return summaries.stream()
                        .map(s -> GroupedSummaryDto.builder()
                                .groupName(s.getGroupName())
                                .count(s.count())
                                .mean(s.getMean())
                                .stdDev(s.getStdDev())
                                .median(s.getMedian())
                                .min(s.getMin())
                                .max(s.getMax())
                                .build())
                        .collect(Collectors.toList());
            } catch (BadRequestException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute grouped summary: " + e.getMessage(), e);
            }
        });
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<com.autoscientist.dto.analysis.HistogramDto> getHistograms(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.analytic(dataset);
        if (cols.stream().filter(c -> "NUMERIC".equals(c.getDataType())).count() < 1) {
            throw new BadRequestException("Histograms need at least one numeric, non-identifier variable.");
        }
        long started = System.currentTimeMillis();
        List<com.autoscientist.dto.analysis.HistogramDto> dtos = cached(cacheKey(dataset, "histograms", null), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                List<com.autoscientist.dto.analysis.HistogramDto> computed = edaEngine.computeHistograms(in, cols).stream()
                        .map(h -> com.autoscientist.dto.analysis.HistogramDto.builder()
                                .variable(h.getVariable())
                                .binStarts(h.getBinStarts())
                                .binEnds(h.getBinEnds())
                                .counts(h.getCounts())
                                .nonMissing(h.getNonMissing())
                                .build())
                        .collect(Collectors.toList());
                analysisRunService.record(dataset, AnalysisOperations.EDA_HISTOGRAMS, Map.of("scope", "numeric-analytic"),
                        "Histograms computed for " + computed.size() + " variable(s)",
                        computed.size(), System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute histograms: " + e.getMessage(), e);
            }
        });
        return dtos;
    }

    @Transactional(readOnly = true)
    public com.autoscientist.dto.analysis.ScatterDto getScatter(Long datasetId, String xVar, String yVar, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> active = ColumnSelection.active(dataset);
        DatasetColumn x = resolveNumericVariable(active, xVar);
        DatasetColumn y = resolveNumericVariable(active, yVar);
        long started = System.currentTimeMillis();
        EdaEngine.ScatterData data = cached(cacheKey(dataset, "scatter", x.getColumnName() + "|" + y.getColumnName()), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                EdaEngine.ScatterData computed = edaEngine.computeScatter(in, x.getColumnName(), y.getColumnName());
                analysisRunService.record(dataset, AnalysisOperations.EDA_SCATTER,
                        Map.of("x", x.getColumnName(), "y", y.getColumnName()),
                        "Scatter of " + x.getColumnName() + " vs " + y.getColumnName(),
                        computed.getPoints().size(), System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute scatter data: " + e.getMessage(), e);
            }
        });
        return com.autoscientist.dto.analysis.ScatterDto.builder()
                .xVariable(data.getXVariable())
                .yVariable(data.getYVariable())
                .points(data.getPoints())
                .totalRows(data.getTotalRows())
                .downsampled(data.isDownsampled())
                .fit(data.getFit() == null ? null : com.autoscientist.dto.analysis.LinearFitDto.builder()
                        .slope(data.getFit().getSlope())
                        .intercept(data.getFit().getIntercept())
                        .rSquared(data.getFit().getRSquared())
                        .n(data.getFit().getN())
                        .standardError(data.getFit().getStandardError())
                        .build())
                .build();
    }

    /**
     * Per-cohort distributions of a numeric variable, measured on shared bins so the
     * resulting density overlays are directly comparable.
     */
    @Transactional(readOnly = true)
    public List<com.autoscientist.dto.analysis.GroupDistributionDto> getGroupDistributions(Long datasetId,
                                                                                            String numVar,
                                                                                            String catVar,
                                                                                            User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> active = ColumnSelection.active(dataset);
        DatasetColumn numeric = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(numVar))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown numeric variable: " + numVar));
        DatasetColumn categorical = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(catVar))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown categorical variable: " + catVar));

        if (ColumnSelection.isIdentifier(numeric) || ColumnSelection.isIdentifier(categorical)) {
            throw new BadRequestException("Identifier columns cannot be used for distribution comparison. "
                    + "Include the column explicitly from the dataset page if this is intentional.");
        }
        if (!"NUMERIC".equals(numeric.getDataType())) {
            throw new BadRequestException("'" + numVar + "' is not numeric (detected: " + numeric.getDataType() + ").");
        }

        long started = System.currentTimeMillis();
        List<com.autoscientist.dto.analysis.GroupDistributionDto> dtos =
                cached(cacheKey(dataset, "groupdist", numeric.getColumnName() + "|" + categorical.getColumnName()), () -> {
                    try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                        List<com.autoscientist.dto.analysis.GroupDistributionDto> computed =
                                edaEngine.computeGroupDistributions(in, numeric.getColumnName(), categorical.getColumnName())
                                        .stream()
                                        .map(g -> com.autoscientist.dto.analysis.GroupDistributionDto.builder()
                                                .groupName(g.getGroupName())
                                                .count(g.getCount())
                                                .mean(g.getMean())
                                                .stdDev(g.getStdDev())
                                                .median(g.getMedian())
                                                .binStarts(g.getBinStarts())
                                                .binEnds(g.getBinEnds())
                                                .counts(g.getCounts())
                                                .proportions(g.getProportions())
                                                .build())
                                        .collect(Collectors.toList());
                        analysisRunService.record(dataset, AnalysisOperations.EDA_GROUPED,
                                Map.of("numeric", numeric.getColumnName(), "categorical", categorical.getColumnName()),
                                "Cohort density comparison of " + numeric.getColumnName() + " by " + categorical.getColumnName(),
                                computed.size(), System.currentTimeMillis() - started, owner);
                        return computed;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to compute group distributions: " + e.getMessage(), e);
                    }
                });
        return dtos;
    }

    /**
     * Row-level presence grid powering the missing-value matrix heat-map.
     */
    @Transactional(readOnly = true)
    public com.autoscientist.dto.analysis.MissingnessMatrixDto getMissingnessMatrix(Long datasetId, int limit, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.active(dataset);
        long started = System.currentTimeMillis();
        com.autoscientist.dto.analysis.MissingnessMatrixDto dto =
                cached(cacheKey(dataset, "missingmatrix", String.valueOf(limit)), () -> {
                    try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                        EdaEngine.MissingnessMatrix matrix = edaEngine.computeMissingnessMatrix(in, cols, limit);
                        com.autoscientist.dto.analysis.MissingnessMatrixDto computed = com.autoscientist.dto.analysis.MissingnessMatrixDto.builder()
                                .variables(matrix.getVariables())
                                .rowIndexes(matrix.getRowIndexes())
                                .present(matrix.getPresent())
                                .totalRows(matrix.getTotalRows())
                                .truncated(matrix.isTruncated())
                                .build();
                        analysisRunService.record(dataset, AnalysisOperations.EDA_MISSINGNESS, Map.of("scope", "row-presence-grid"),
                                "Missing-value matrix rendered for " + computed.getRowIndexes().length + " row(s)",
                                computed.getRowIndexes().length, System.currentTimeMillis() - started, owner);
                        return computed;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to compute missingness matrix: " + e.getMessage(), e);
                    }
                });
        return dto;
    }

    @Transactional(readOnly = true)
    public List<com.autoscientist.dto.analysis.MissingnessDto> getMissingness(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.active(dataset);
        long started = System.currentTimeMillis();
        List<com.autoscientist.dto.analysis.MissingnessDto> dtos = cached(cacheKey(dataset, "missingness", null), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                List<com.autoscientist.dto.analysis.MissingnessDto> computed = edaEngine.computeMissingness(in, cols).stream()
                        .map(m -> com.autoscientist.dto.analysis.MissingnessDto.builder()
                                .variable(m.getVariable())
                                .missingCount(m.getMissingCount())
                                .totalCount(m.getTotalCount())
                                .missingPct(m.getMissingPct())
                                .build())
                        .collect(Collectors.toList());
                analysisRunService.record(dataset, AnalysisOperations.EDA_MISSINGNESS, Map.of("scope", "active-columns"),
                        "Missingness profiled across " + computed.size() + " variable(s)",
                        computed.size(), System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute missingness: " + e.getMessage(), e);
            }
        });
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<com.autoscientist.dto.analysis.CategoricalSummaryDto> getCategoricalSummaries(Long datasetId, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> cols = ColumnSelection.analytic(dataset);
        if (cols.stream().filter(c -> !ColumnSelection.isIdentifier(c) && !"NUMERIC".equals(c.getDataType())).count() < 1) {
            throw new BadRequestException("Categorical summaries need at least one categorical, non-identifier variable.");
        }
        long started = System.currentTimeMillis();
        List<com.autoscientist.dto.analysis.CategoricalSummaryDto> dtos = cached(cacheKey(dataset, "categorical", null), () -> {
            try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                List<com.autoscientist.dto.analysis.CategoricalSummaryDto> computed = edaEngine.computeCategoricalSummaries(in, cols).stream()
                        .map(s -> com.autoscientist.dto.analysis.CategoricalSummaryDto.builder()
                                .variable(s.getVariable())
                                .uniqueCount(s.getUniqueCount())
                                .missingCount(s.getMissingCount())
                                .topCategories(s.getTopCategories().stream()
                                        .map(c -> com.autoscientist.dto.analysis.CategoryCountDto.builder()
                                                .category(c.getCategory())
                                                .count(c.getCount())
                                                .pct(c.getPct())
                                                .build())
                                        .collect(Collectors.toList()))
                                .build())
                        .collect(Collectors.toList());
                analysisRunService.record(dataset, AnalysisOperations.EDA_CATEGORICAL, Map.of("scope", "categorical-analytic"),
                        "Categorical summaries computed for " + computed.size() + " variable(s)",
                        computed.size(), System.currentTimeMillis() - started, owner);
                return computed;
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute categorical summaries: " + e.getMessage(), e);
            }
        });
        return dtos;
    }

    @Transactional(readOnly = true)
    public List<com.autoscientist.dto.analysis.TimeSeriesPointDto> getTimeSeries(Long datasetId, String timeVar,
                                                                                String valueVar, int aggregation, User owner) {
        Dataset dataset = datasetService.getDatasetEntityWithOwnership(datasetId, owner);
        List<DatasetColumn> active = ColumnSelection.active(dataset);
        DatasetColumn value = resolveNumericVariable(active, valueVar);
        DatasetColumn time = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(timeVar))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown time variable: " + timeVar));
        if (ColumnSelection.isIdentifier(time)) {
            throw new BadRequestException("'" + timeVar + "' is an identifier column and cannot be used as a time axis.");
        }
        long started = System.currentTimeMillis();
        List<com.autoscientist.dto.analysis.TimeSeriesPointDto> dtos =
                cached(cacheKey(dataset, "timeseries", time.getColumnName() + "|" + value.getColumnName() + "|" + aggregation), () -> {
                    try (InputStream in = fileStorageService.getInputStream(dataset.getStoredFilename())) {
                        List<EdaEngine.TimeSeriesPoint> points = edaEngine.computeTimeSeries(in,
                                time.getColumnName(), value.getColumnName(), aggregation);
                        if (points.isEmpty()) {
                            throw new BadRequestException("No plottable time/value pairs found. The time variable must contain "
                                    + "ISO-like timestamps (yyyy-MM-dd...) or ordered category labels.");
                        }
                        List<com.autoscientist.dto.analysis.TimeSeriesPointDto> computed = points.stream()
                                .map(p -> com.autoscientist.dto.analysis.TimeSeriesPointDto.builder()
                                        .timestamp(p.getTimestamp())
                                        .value(p.getValue())
                                        .build())
                                .collect(Collectors.toList());
                        analysisRunService.record(dataset, AnalysisOperations.EDA_TIMESERIES,
                                Map.of("time", time.getColumnName(), "value", value.getColumnName(), "aggregation", String.valueOf(aggregation)),
                                "Time series of " + value.getColumnName() + " by " + time.getColumnName(),
                                computed.size(), System.currentTimeMillis() - started, owner);
                        return computed;
                    } catch (BadRequestException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to compute time series: " + e.getMessage(), e);
                    }
                });
        return dtos;
    }

    public void invalidateDataset(Long datasetId) {
        if (datasetId != null) {
            cache.invalidatePrefix(datasetId + "@");
        }
    }

    public void clearCache() {
        cache.clear();
    }

    private String cacheKey(Dataset dataset, String operation, String qualifier) {
        StringBuilder key = new StringBuilder()
                .append(dataset.getId()).append('@')
                .append(dataset.getVersion() == null ? 1 : dataset.getVersion())
                .append(':').append(operation);
        if (qualifier != null) key.append(':').append(qualifier);
        return key.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> supplier) {
        return (T) cache.getOrCompute(key, supplier::get);
    }

    private DatasetColumn resolveNumericVariable(List<DatasetColumn> active, String name) {
        DatasetColumn col = active.stream()
                .filter(c -> c.getColumnName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown variable: " + name));
        if (ColumnSelection.isIdentifier(col)) {
            throw new BadRequestException("'" + name + "' is an identifier column and cannot be analysed. "
                    + "Include it explicitly from the dataset page if this is intentional.");
        }
        if (!"NUMERIC".equals(col.getDataType())) {
            throw new BadRequestException("'" + name + "' is not numeric (detected: " + col.getDataType() + ").");
        }
        return col;
    }
}
