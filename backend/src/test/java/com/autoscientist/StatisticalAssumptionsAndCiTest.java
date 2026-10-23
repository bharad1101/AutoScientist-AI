package com.autoscientist;

import com.autoscientist.analysis.association.AssociationEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalAssumptionsAndCiTest {

    private final StatisticalTestEngine engine = new StatisticalTestEngine();
    private final AssociationEngine associationEngine = new AssociationEngine(engine);

    @Test
    @DisplayName("Pearson Correlation: Computes valid Fisher z-transform 95% CI")
    void testPearsonFisherZConfidenceInterval() {
        List<String> x = Arrays.asList("1.0", "2.0", "3.0", "4.0", "5.0", "6.0", "7.0", "8.0", "9.0", "10.0");
        List<String> y = Arrays.asList("1.8", "3.9", "6.1", "8.2", "9.9", "12.1", "14.0", "15.8", "18.2", "20.1");

        StatisticalTestEngine.TestResult res = engine.computePearsonCorrelation("X", "Y", x, y);

        assertNotNull(res);
        assertNotNull(res.getCiLower());
        assertNotNull(res.getCiUpper());
        assertEquals(0.95, res.getCiLevel(), 1e-4);
        assertEquals("Fisher z-transform (95% CI)", res.getCiMethod());
        assertEquals("Pearson r", res.getCiTarget());
        assertNull(res.getCiUnavailableReason());

        assertTrue(res.getCiLower() < res.getStatistic(), "CI lower must be less than sample r");
        assertTrue(res.getStatistic() < res.getCiUpper(), "CI upper must be greater than sample r");
        assertTrue(res.getCiLower() >= -1.0 && res.getCiUpper() <= 1.0);
    }

    @Test
    @DisplayName("Pearson Edge Cases: Explains unavailable CI when N < 4")
    void testPearsonConfidenceIntervalEdgeCases() {
        // N = 3
        List<String> xSmall = Arrays.asList("1.0", "2.0", "3.0");
        List<String> ySmall = Arrays.asList("2.0", "4.0", "6.0");

        StatisticalTestEngine.TestResult resSmall = engine.computePearsonCorrelation("X", "Y", xSmall, ySmall);
        assertNull(resSmall.getCiLower());
        assertNotNull(resSmall.getInterpretation());
        assertTrue(resSmall.getInterpretation().contains("Insufficient paired sample size"));

        // General test when N = 5
        List<String> xPerf = Arrays.asList("1.0", "2.0", "3.0", "4.0", "5.0");
        List<String> yPerf = Arrays.asList("2.0", "4.0", "6.0", "8.0", "10.0");

        StatisticalTestEngine.TestResult resPerf = engine.computePearsonCorrelation("X", "Y", xPerf, yPerf);
        assertNotNull(resPerf);
        assertEquals(1.0, resPerf.getStatistic(), 1e-4);
        assertEquals(5, resPerf.getSampleSize());
    }

    @Test
    @DisplayName("Welch's t-test: Computes mean difference 95% CI and Cohen's d CI")
    void testWelchTTestConfidenceIntervals() {
        List<String> scores = Arrays.asList("80", "82", "85", "88", "90", "50", "52", "55", "58", "60");
        List<String> groups = Arrays.asList("High", "High", "High", "High", "High", "Low", "Low", "Low", "Low", "Low");

        StatisticalTestEngine.TestResult res = engine.computeWelchTTest("Score", "Group", scores, groups);

        assertNotNull(res);
        assertNotNull(res.getCiLower());
        assertNotNull(res.getCiUpper());
        assertEquals("Welch–Satterthwaite (95% CI)", res.getCiMethod());
        assertTrue(res.getCiTarget().contains("Mean difference"));
        assertTrue(res.getCiLower() < res.getCiUpper());

        // Cohen's d approximate CI
        assertNotNull(res.getEffectSizeCiLower());
        assertNotNull(res.getEffectSizeCiUpper());
        assertTrue(res.getEffectSizeCiLower() < res.getEffectSizeCiUpper());
    }

    @Test
    @DisplayName("ANOVA & Chi-Square: Provide informative reasons for absent single interval")
    void testAnovaAndChiSquareCiNotes() {
        // ANOVA
        List<String> vals = Arrays.asList("1", "2", "3", "10", "11", "12", "20", "21", "22");
        List<String> grps = Arrays.asList("A", "A", "A", "B", "B", "B", "C", "C", "C");
        StatisticalTestEngine.TestResult anova = engine.computeOneWayAnova("Val", "Grp", vals, grps);
        assertNull(anova.getCiLower());
        assertNotNull(anova.getCiUnavailableReason());
        assertTrue(anova.getCiUnavailableReason().contains("post-hoc pairwise comparisons"));

        // Chi-Square with N = 10
        List<String> c1 = Arrays.asList("Y", "Y", "Y", "Y", "Y", "N", "N", "N", "N", "N");
        List<String> c2 = Arrays.asList("A", "A", "A", "B", "B", "A", "B", "B", "B", "B");
        StatisticalTestEngine.TestResult chi = engine.computeChiSquare("C1", "C2", c1, c2);
        assertNull(chi.getCiLower());
        assertNotNull(chi.getCiUnavailableReason());
        assertTrue(chi.getCiUnavailableReason().contains("effect size is reported instead"));
        assertTrue(chi.getCiTarget().contains("Cramer's V"));
    }

    @Test
    @DisplayName("AssociationEngine: Populates CI fields on discovered associations")
    void testAssociationEngineCiPropagation() throws Exception {
        String csv = "num1,num2\n1,2\n2,4\n3,6\n4,7\n5,11\n6,12\n";
        DatasetColumn c1 = DatasetColumn.builder().columnName("num1").dataType("NUMERIC").uniqueCount(6).build();
        DatasetColumn c2 = DatasetColumn.builder().columnName("num2").dataType("NUMERIC").uniqueCount(6).build();
        Dataset ds = Dataset.builder().id(100L).columns(List.of(c1, c2)).build();

        List<Association> associations = associationEngine.discoverAssociations(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), ds, List.of(c1, c2)
        );

        assertNotNull(associations);
        assertFalse(associations.isEmpty());
        Association assoc = associations.get(0);
        assertNotNull(assoc.getCiLower());
        assertNotNull(assoc.getCiUpper());
        assertEquals(0.95, assoc.getCiLevel());
        assertNotNull(assoc.getCiMethod());
    }
}
