package com.autoscientist;

import com.autoscientist.analysis.core.AskDatasetEngine;
import com.autoscientist.analysis.eda.EdaEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.dto.ask.AskDatasetResponseDto;
import com.autoscientist.entity.DataQualityReport;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsFeatureTest {

    private static final String CSV = "id,score,group,when\n"
            + "1,10,A,2024-01-01 01:00\n"
            + "2,20,A,2024-01-01 01:30\n"
            + "3,30,B,2024-01-01 02:10\n"
            + "4,40,B,2024-01-01 02:40\n"
            + "5,50,B,2024-01-02 01:00\n";

    private final EdaEngine edaEngine = new EdaEngine(new StatisticalTestEngine());

    private DatasetColumn col(String name, String type) {
        return DatasetColumn.builder().columnName(name).dataType(type).build();
    }

    private List<DatasetColumn> columns() {
        return List.of(col("id", "IDENTIFIER"),
                DatasetColumn.builder().columnName("score").dataType("NUMERIC")
                        .meanVal(30.0).medianVal(30.0).stdDev(14.14).minVal(10.0).maxVal(50.0)
                        .missingCount(0).missingPct(0.0).uniqueCount(5).build(),
                col("group", "CATEGORICAL"), col("when", "DATE_TIME"));
    }

    private ByteArrayInputStream csv() {
        return new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Histograms, missingness, categorical summaries and scatter use real CSV rows")
    void testEdaExtensions() throws Exception {
        var histograms = edaEngine.computeHistograms(csv(), columns());
        assertEquals(1, histograms.size(), "only the numeric non-identifier column should be histogrammed");
        assertEquals("score", histograms.get(0).getVariable());
        assertEquals(5, histograms.get(0).getNonMissing());

        var missing = edaEngine.computeMissingness(csv(), columns());
        assertEquals(3, missing.size(), "identifier columns are excluded from the missingness audit");
        assertTrue(missing.stream().allMatch(m -> m.getMissingCount() == 0));

        var categorical = edaEngine.computeCategoricalSummaries(csv(), columns());
        var groupSummary = categorical.stream().filter(c -> "group".equals(c.getVariable()))
                .findFirst().orElseThrow();
        assertEquals(2, groupSummary.getUniqueCount());
        assertEquals(3, groupSummary.getTopCategories().get(0).getCount(), "B is the largest group");

        var scatter = edaEngine.computeScatter(csv(), "score", "id");
        assertEquals(5, scatter.getTotalRows());
        assertFalse(scatter.isDownsampled());
        assertEquals(5, scatter.getPoints().size());
    }

    @Test
    @DisplayName("Time series buckets ISO timestamps and honours the aggregation mode")
    void testTimeSeriesAggregation() throws Exception {
        var mean = edaEngine.computeTimeSeries(csv(), "when", "score", 0);
        assertTrue(mean.size() >= 3);
        assertEquals("2024-01-01 01", mean.get(0).getTimestamp());
        assertEquals(15.0, mean.get(0).getValue(), 1e-9);

        var max = edaEngine.computeTimeSeries(csv(), "when", "score", 3);
        assertEquals(20.0, max.get(0).getValue(), 1e-9);
    }

    @Test
    @DisplayName("Ask engine parses paraphrases, rejects causal questions and cites evidence")
    void testAskIntentParsing() {
        AskDatasetEngine engine = new AskDatasetEngine();
        Dataset dataset = Dataset.builder()
                .id(1L).name("Scores").rowCount(5).columnCount(4)
                .columns(columns()).build();
        DataQualityReport quality = DataQualityReport.builder()
                .overallScore(90.0).missingValueScore(100.0).duplicateScore(100.0)
                .outlierScore(95.0).consistencyScore(98.0).totalIssues(0).build();

        AskDatasetResponseDto mean = engine.answerQuery("What is the typical score?",
                dataset, quality, List.of(), List.of());
        assertEquals("NUMERIC_CENTRAL_TENDENCY", mean.getDetectedIntent());
        assertFalse(mean.getCitations().isEmpty());

        AskDatasetResponseDto causal = engine.answerQuery("Does group cause score?",
                dataset, quality, List.of(), List.of());
        assertEquals("CAUSAL_INFERENCE_UNSUPPORTED", causal.getDetectedIntent());
        assertTrue(causal.getAnswerMarkdown().toLowerCase().contains("observational"));

        AskDatasetResponseDto qualityAnswer = engine.answerQuery("How is data quality?",
                dataset, quality, List.of(), List.of());
        assertEquals("DATA_QUALITY_ASSESSMENT", qualityAnswer.getDetectedIntent());
        assertNotNull(qualityAnswer.getChart());
        assertEquals(4, qualityAnswer.getChart().getLabels().size());

        AskDatasetResponseDto underscored = engine.answerQuery("What is the average score?",
                dataset, quality, List.of(), List.of());
        assertEquals("NUMERIC_CENTRAL_TENDENCY", underscored.getDetectedIntent());
        assertTrue(underscored.getRelevantColumns().contains("score"),
                "underscored column names must be matched after normalization");

        AskDatasetResponseDto grouped = engine.answerQuery("Compare score by group",
                dataset, quality, List.of(), List.of());
        assertEquals("GROUP_COMPARISON", grouped.getDetectedIntent());

        AskDatasetResponseDto unknown = engine.answerQuery("Will it rain tomorrow?",
                dataset, quality, List.of(), List.of());
        assertEquals("UNSUPPORTED", unknown.getDetectedIntent());
    }
}
