package com.autoscientist;

import com.autoscientist.analysis.eda.EdaEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EdaRegressionTest {

    private final EdaEngine edaEngine = new EdaEngine(new StatisticalTestEngine());

    private static final String CSV_DATA = "id,age,income,category,city\n"
            + "1,25,30000,A,NY\n"
            + "2,30,45000,A,NY\n"
            + "3,35,50000,B,SF\n"
            + "4,40,65000,B,SF\n"
            + "5,45,70000,B,NY\n"
            + "6,50,85000,C,SF\n"
            + "7,55,90000,C,NY\n"
            + "8,60,110000,C,SF\n"
            + "9,28,40000,A,NY\n"
            + "10,95,500000,A,SF\n"; // row 10 has extreme outlier for income

    private List<DatasetColumn> columns() {
        return List.of(
                DatasetColumn.builder().columnName("id").dataType("IDENTIFIER").build(),
                DatasetColumn.builder().columnName("age").dataType("NUMERIC")
                        .meanVal(46.3).minVal(25.0).maxVal(95.0).missingCount(0).build(),
                DatasetColumn.builder().columnName("income").dataType("NUMERIC")
                        .meanVal(108500.0).minVal(30000.0).maxVal(500000.0).missingCount(0).build(),
                DatasetColumn.builder().columnName("category").dataType("CATEGORICAL").missingCount(0).build(),
                DatasetColumn.builder().columnName("city").dataType("CATEGORICAL").missingCount(0).build()
        );
    }

    private ByteArrayInputStream getInputStream() {
        return new ByteArrayInputStream(CSV_DATA.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Correlation Matrix: Computes coefficients, p-values, sample sizes, and diagonal 1.0")
    void testCorrelationMatrixWithPValues() throws Exception {
        EdaEngine.CorrelationMatrix matrix = edaEngine.computeCorrelationMatrix(getInputStream(), columns());

        assertNotNull(matrix);
        assertEquals(2, matrix.getVariables().size(), "Only analytic numeric variables (age, income) should be included");
        assertTrue(matrix.getVariables().contains("age"));
        assertTrue(matrix.getVariables().contains("income"));

        double[][] r = matrix.getMatrix();
        assertEquals(1.0, r[0][0], 1e-6, "Diagonal r should be 1.0");
        assertEquals(1.0, r[1][1], 1e-6, "Diagonal r should be 1.0");
        assertEquals(r[0][1], r[1][0], 1e-6, "Matrix should be symmetric");
        assertTrue(r[0][1] > 0.7, "Age and income should have a strong positive correlation");

        double[][] p = matrix.getPValues();
        assertNotNull(p, "P-values matrix should be computed");
        assertEquals(0.0, p[0][0], 1e-6, "Self-correlation p-value should be 0.0");
        assertEquals(p[0][1], p[1][0], 1e-6, "P-values matrix should be symmetric");
        assertTrue(p[0][1] < 0.05, "P-value should be statistically significant");

        int[] n = matrix.getSampleSizes();
        assertNotNull(n);
        assertTrue(n.length >= 2);
        assertEquals(10, n[0]);
    }

    @Test
    @DisplayName("Box Plots: Five-number summary, IQR, and outlier detection")
    void testBoxPlots() throws Exception {
        List<EdaEngine.BoxPlotData> boxPlots = edaEngine.computeBoxPlots(getInputStream(), columns());

        assertNotNull(boxPlots);
        assertEquals(2, boxPlots.size(), "Only analytic numeric columns should have box plots");

        EdaEngine.BoxPlotData incomeBp = boxPlots.stream()
                .filter(b -> "income".equals(b.getVariable()))
                .findFirst().orElseThrow();

        assertTrue(incomeBp.getMin() <= incomeBp.getQ1());
        assertTrue(incomeBp.getQ1() <= incomeBp.getMedian());
        assertTrue(incomeBp.getMedian() <= incomeBp.getQ3());
        assertTrue(incomeBp.getQ3() <= incomeBp.getMax());
        assertEquals(incomeBp.getQ3() - incomeBp.getQ1(), incomeBp.getIqr(), 1e-6);

        // Outlier 500000 should be identified for income
        assertNotNull(incomeBp.getOutliers());
        assertTrue(incomeBp.getOutliers().contains(500000.0), "Income 500000 should be classified as an outlier");
    }

    @Test
    @DisplayName("Scatter Plot: Computes coordinates and linear regression fit metrics")
    void testScatterWithLinearFit() throws Exception {
        EdaEngine.ScatterData scatter = edaEngine.computeScatter(getInputStream(), "age", "income");

        assertNotNull(scatter);
        assertEquals(10, scatter.getTotalRows());
        assertFalse(scatter.isDownsampled());
        assertEquals(10, scatter.getPoints().size());

        EdaEngine.LinearFit fit = scatter.getFit();
        assertNotNull(fit, "Linear regression fit should be computed");
        assertTrue(fit.getSlope() > 0, "Slope should be positive for age vs income");
        assertTrue(fit.getRSquared() > 0.5, "R-squared should indicate strong fit");
        assertTrue(fit.getStandardError() >= 0.0);
    }

    @Test
    @DisplayName("Grouped Distributions: Groups numeric variable across categorical levels")
    void testGroupDistributions() throws Exception {
        List<EdaEngine.GroupDistribution> distributions =
                edaEngine.computeGroupDistributions(getInputStream(), "income", "category");

        assertNotNull(distributions);
        assertEquals(3, distributions.size(), "Should have categories A, B, and C");

        EdaEngine.GroupDistribution groupA = distributions.stream()
                .filter(g -> "A".equals(g.getGroupName()))
                .findFirst().orElseThrow();

        assertEquals(4, groupA.getCount());
        assertTrue(groupA.getMean() > 0);
        assertTrue(groupA.getMedian() > 0);
    }

    @Test
    @DisplayName("Missingness Matrix: Emits row-level completeness indicators")
    void testMissingnessMatrix() throws Exception {
        String csvWithMissing = "id,score,passed\n"
                + "1,10,true\n"
                + "2,,false\n"
                + "3,30,\n"
                + "4,40,true\n";

        List<DatasetColumn> cols = List.of(
                DatasetColumn.builder().columnName("id").dataType("IDENTIFIER").build(),
                DatasetColumn.builder().columnName("score").dataType("NUMERIC").build(),
                DatasetColumn.builder().columnName("passed").dataType("CATEGORICAL").build()
        );

        ByteArrayInputStream is = new ByteArrayInputStream(csvWithMissing.getBytes(StandardCharsets.UTF_8));
        EdaEngine.MissingnessMatrix matrix = edaEngine.computeMissingnessMatrix(is, cols, 100);

        assertNotNull(matrix);
        assertEquals(4, matrix.getTotalRows());
        assertEquals(2, matrix.getVariables().size(), "IDENTIFIER column id should be excluded from matrix");
        assertEquals("score", matrix.getVariables().get(0));
        assertEquals("passed", matrix.getVariables().get(1));

        boolean[][] present = matrix.getPresent();
        // Row 1: both present -> [true, true]
        assertTrue(present[0][0]);
        assertTrue(present[0][1]);

        // Row 2: score missing -> [false, true]
        assertFalse(present[1][0]);
        assertTrue(present[1][1]);

        // Row 3: passed missing -> [true, false]
        assertTrue(present[2][0]);
        assertFalse(present[2][1]);
    }
}
