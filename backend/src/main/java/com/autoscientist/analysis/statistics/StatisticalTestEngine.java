package com.autoscientist.analysis.statistics;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StatisticalTestEngine {

    public static class TestResult {
        private final String testType;
        private final double statistic;
        private final double pValue;
        private final double effectSize;
        private final String effectSizeType;
        private final int sampleSize;
        private final Double degreesOfFreedom;
        private final boolean isSignificant;
        private final String interpretation;
        private final double rankScore;
        private final Double ciLower;
        private final Double ciUpper;
        private final Double ciLevel;
        private final String ciMethod;
        private final String ciTarget;
        private final String ciUnavailableReason;
        private final Double effectSizeCiLower;
        private final Double effectSizeCiUpper;

        public TestResult(String testType, double statistic, double pValue, double effectSize,
                          String effectSizeType, int sampleSize, Double degreesOfFreedom,
                          boolean isSignificant, String interpretation, double rankScore) {
            this(testType, statistic, pValue, effectSize, effectSizeType, sampleSize, degreesOfFreedom,
                    isSignificant, interpretation, rankScore, null, null, null, null, null, null, null, null);
        }

        public TestResult(String testType, double statistic, double pValue, double effectSize,
                          String effectSizeType, int sampleSize, Double degreesOfFreedom,
                          boolean isSignificant, String interpretation, double rankScore,
                          Double ciLower, Double ciUpper, Double ciLevel, String ciMethod,
                          String ciTarget, String ciUnavailableReason,
                          Double effectSizeCiLower, Double effectSizeCiUpper) {
            this.testType = testType;
            this.statistic = statistic;
            this.pValue = pValue;
            this.effectSize = effectSize;
            this.effectSizeType = effectSizeType;
            this.sampleSize = sampleSize;
            this.degreesOfFreedom = degreesOfFreedom;
            this.isSignificant = isSignificant;
            this.interpretation = interpretation;
            this.rankScore = rankScore;
            this.ciLower = ciLower;
            this.ciUpper = ciUpper;
            this.ciLevel = ciLevel;
            this.ciMethod = ciMethod;
            this.ciTarget = ciTarget;
            this.ciUnavailableReason = ciUnavailableReason;
            this.effectSizeCiLower = effectSizeCiLower;
            this.effectSizeCiUpper = effectSizeCiUpper;
        }

        public String getTestType() { return testType; }
        public double getStatistic() { return statistic; }
        public double getPValue() { return pValue; }
        public double getEffectSize() { return effectSize; }
        public String getEffectSizeType() { return effectSizeType; }
        public int getSampleSize() { return sampleSize; }
        public Double getDegreesOfFreedom() { return degreesOfFreedom; }
        public boolean isSignificant() { return isSignificant; }
        public String getInterpretation() { return interpretation; }
        public double getRankScore() { return rankScore; }
        public Double getCiLower() { return ciLower; }
        public Double getCiUpper() { return ciUpper; }
        public Double getCiLevel() { return ciLevel; }
        public String getCiMethod() { return ciMethod; }
        public String getCiTarget() { return ciTarget; }
        public String getCiUnavailableReason() { return ciUnavailableReason; }
        public Double getEffectSizeCiLower() { return effectSizeCiLower; }
        public Double getEffectSizeCiUpper() { return effectSizeCiUpper; }
    }

    /**
     * Pearson Correlation for Numeric + Numeric pairs with aligned paired filtering.
     */
    public TestResult computePearsonCorrelation(String var1, String var2, List<String> rawX, List<String> rawY) {
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        int nTotal = Math.min(rawX.size(), rawY.size());
        for (int i = 0; i < nTotal; i++) {
            String sx = rawX.get(i);
            String sy = rawY.get(i);
            if (isValidNumber(sx) && isValidNumber(sy)) {
                x.add(Double.parseDouble(sx));
                y.add(Double.parseDouble(sy));
            }
        }

        int n = x.size();
        if (n < 4) {
            return new TestResult("PEARSON", 0.0, 1.0, 0.0, "r_squared", n, null, false,
                    String.format("Insufficient paired sample size (N=%d < 4) between %s and %s.", n, var1, var2), 0.0);
        }

        DescriptiveStatistics statsX = new DescriptiveStatistics();
        DescriptiveStatistics statsY = new DescriptiveStatistics();
        x.forEach(statsX::addValue);
        y.forEach(statsY::addValue);

        double stdX = statsX.getStandardDeviation();
        double stdY = statsY.getStandardDeviation();

        if (stdX == 0.0 || stdY == 0.0) {
            return new TestResult("PEARSON", 0.0, 1.0, 0.0, "r_squared", n, (double)(n - 2), false,
                    String.format("Zero variance detected in %s or %s; correlation cannot be determined.", var1, var2), 0.0);
        }

        double meanX = statsX.getMean();
        double meanY = statsY.getMean();
        double cov = 0.0;
        for (int i = 0; i < n; i++) {
            cov += (x.get(i) - meanX) * (y.get(i) - meanY);
        }
        cov /= (n - 1);

        double r = cov / (stdX * stdY);
        r = Math.max(-1.0, Math.min(1.0, r)); // clamp

        double df = n - 2;
        double tStat = 0.0;
        double pValue = 1.0;
        if (Math.abs(r) < 1.0) {
            tStat = r * Math.sqrt(df / (1.0 - r * r));
            try {
                TDistribution tDist = new TDistribution(df);
                pValue = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(tStat)));
                pValue = Math.max(0.0, Math.min(1.0, pValue));
            } catch (Exception e) {
                pValue = 1.0;
            }
        } else {
            pValue = 0.0;
            tStat = Double.POSITIVE_INFINITY;
        }

        double rSquared = r * r;
        boolean significant = pValue < 0.05;
        double rankScore = Math.abs(r) * (1.0 - pValue) * Math.log10(n + 1);

        // 95% CI for the population correlation via the Fisher z-transform.
        Double ciLower = null;
        Double ciUpper = null;
        String ciMethod = null;
        String ciTarget = "Pearson r";
        String ciUnavailableReason = null;
        if (n > 3 && Math.abs(r) < 1.0) {
            double z = 0.5 * Math.log((1 + r) / (1 - r));
            double seZ = 1.0 / Math.sqrt(n - 3);
            try {
                TDistribution tDist = new TDistribution(n - 3);
                double tCrit = tDist.inverseCumulativeProbability(0.975);
                double zLower = z - tCrit * seZ;
                double zUpper = z + tCrit * seZ;
                ciLower = round(Math.tanh(zLower), 4);
                ciUpper = round(Math.tanh(zUpper), 4);
                ciMethod = "Fisher z-transform (95% CI)";
            } catch (Exception e) {
                ciUnavailableReason = "The Fisher z-transform confidence interval could not be computed for this sample.";
            }
        } else if (n <= 3) {
            ciUnavailableReason = "At least 4 paired observations are required for a confidence interval.";
        } else {
            ciUnavailableReason = "A perfect correlation has no finite confidence interval.";
        }

        String magnitude = Math.abs(r) > 0.7 ? "strong" : Math.abs(r) > 0.4 ? "moderate" : "weak";
        String direction = r > 0 ? "positive" : "negative";
        String interpretation = significant
                ? String.format("Evidence of a statistically significant %s %s linear association between %s and %s (r=%.3f, p=%.4f, N=%d). Approximately %.1f%% of variance is shared. Note: This indicates association, not causality.",
                magnitude, direction, var1, var2, r, pValue, n, rSquared * 100.0)
                : String.format("No statistically significant linear association was detected between %s and %s (r=%.3f, p=%.4f, N=%d).",
                var1, var2, r, pValue, n);

        return new TestResult("PEARSON", round(r, 4), round(pValue, 4), round(rSquared, 4),
                "r_squared", n, df, significant, interpretation, round(rankScore, 4),
                ciLower, ciUpper, 0.95, ciMethod, ciTarget, ciUnavailableReason, null, null);
    }

    /**
     * Welch's t-test for Numeric + Two-Category pairs with paired alignment.
     */
    public TestResult computeWelchTTest(String numVar, String catVar, List<String> rawNum, List<String> rawCat) {
        Map<String, List<Double>> groups = new HashMap<>();

        int nTotal = Math.min(rawNum.size(), rawCat.size());
        for (int i = 0; i < nTotal; i++) {
            String sNum = rawNum.get(i);
            String sCat = rawCat.get(i);
            if (isValidNumber(sNum) && sCat != null && !sCat.trim().isEmpty() && !sCat.equalsIgnoreCase("na")) {
                groups.computeIfAbsent(sCat.trim(), k -> new ArrayList<>()).add(Double.parseDouble(sNum));
            }
        }

        List<String> validGroupKeys = new ArrayList<>(groups.keySet());
        if (validGroupKeys.size() != 2) {
            return new TestResult("WELCH_T_TEST", 0.0, 1.0, 0.0, "cohens_d", 0, null, false,
                    "Welch's t-test requires exactly 2 distinct non-empty categorical groups.", 0.0);
        }

        String group1Name = validGroupKeys.get(0);
        String group2Name = validGroupKeys.get(1);
        List<Double> g1 = groups.get(group1Name);
        List<Double> g2 = groups.get(group2Name);

        if (g1.size() < 2 || g2.size() < 2) {
            return new TestResult("WELCH_T_TEST", 0.0, 1.0, 0.0, "cohens_d", g1.size() + g2.size(), null, false,
                    "Insufficient observations per group for independent t-test (at least 2 per group required).", 0.0);
        }

        DescriptiveStatistics stats1 = new DescriptiveStatistics();
        DescriptiveStatistics stats2 = new DescriptiveStatistics();
        g1.forEach(stats1::addValue);
        g2.forEach(stats2::addValue);

        double m1 = stats1.getMean();
        double m2 = stats2.getMean();
        double v1 = stats1.getVariance();
        double v2 = stats2.getVariance();
        int n1 = g1.size();
        int n2 = g2.size();
        int sampleSize = n1 + n2;

        if (v1 == 0.0 && v2 == 0.0) {
            return new TestResult("WELCH_T_TEST", 0.0, 1.0, 0.0, "cohens_d", sampleSize, null, false,
                    "Zero variance in both groups; t-test cannot be calculated.", 0.0);
        }

        double denom = Math.sqrt((v1 / n1) + (v2 / n2));
        double tStat = (denom > 0) ? (m1 - m2) / denom : 0.0;

        // Welch-Satterthwaite degrees of freedom
        double dfNum = Math.pow((v1 / n1) + (v2 / n2), 2);
        double dfDen = (Math.pow(v1 / n1, 2) / (n1 - 1)) + (Math.pow(v2 / n2, 2) / (n2 - 1));
        double df = (dfDen > 0) ? dfNum / dfDen : 1.0;

        double pValue = 1.0;
        try {
            TDistribution tDist = new TDistribution(df);
            pValue = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(tStat)));
            pValue = Math.max(0.0, Math.min(1.0, pValue));
        } catch (Exception e) {
            pValue = 1.0;
        }

        // Cohen's d
        double pooledStd = Math.sqrt(((n1 - 1) * v1 + (n2 - 1) * v2) / (n1 + n2 - 2));
        double cohensD = (pooledStd > 0) ? Math.abs(m1 - m2) / pooledStd : 0.0;

        // 95% CI for the mean difference (group1 - group2) using the Welch df.
        double seDiff = denom;
        double meanDiff = m1 - m2;
        Double ciLower = null;
        Double ciUpper = null;
        String ciMethod = null;
        String ciTarget = "Mean difference (" + group1Name + " − " + group2Name + ")";
        String ciUnavailableReason = null;
        try {
            TDistribution tDist = new TDistribution(df);
            double tCrit = tDist.inverseCumulativeProbability(0.975);
            ciLower = round(meanDiff - tCrit * seDiff, 4);
            ciUpper = round(meanDiff + tCrit * seDiff, 4);
            ciMethod = "Welch–Satterthwaite (95% CI)";
        } catch (Exception e) {
            ciUnavailableReason = "The mean-difference confidence interval could not be computed for this sample.";
        }

        // Approximate 95% CI for Cohen's d (normal-theory standard error).
        Double dCiLower = null;
        Double dCiUpper = null;
        if (n1 + n2 > 3 && pooledStd > 0) {
            double seD = Math.sqrt((double) (n1 + n2) / (n1 * n2)
                    + (cohensD * cohensD) / (2.0 * (n1 + n2 - 2)));
            dCiLower = round(cohensD - 1.96 * seD, 4);
            dCiUpper = round(cohensD + 1.96 * seD, 4);
        }

        boolean significant = pValue < 0.05;
        double rankScore = Math.min(3.0, cohensD) * (1.0 - pValue) * Math.log10(sampleSize + 1);

        String effectMag = cohensD >= 0.8 ? "large" : cohensD >= 0.5 ? "medium" : "small";
        String interpretation = significant
                ? String.format("Evidence of a statistically significant difference in %s between group '%s' (mean=%.2f) and '%s' (mean=%.2f) via Welch's t-test (t=%.3f, p=%.4f, df=%.1f, Cohen's d=%.2f [%s effect]). Does not imply causal direction.",
                numVar, group1Name, m1, group2Name, m2, tStat, pValue, df, cohensD, effectMag)
                : String.format("No statistically significant difference in %s was found between '%s' and '%s' (t=%.3f, p=%.4f, df=%.1f).",
                numVar, group1Name, group2Name, tStat, pValue, df);

        return new TestResult("WELCH_T_TEST", round(tStat, 4), round(pValue, 4), round(cohensD, 4),
                "cohens_d", sampleSize, round(df, 1), significant, interpretation, round(rankScore, 4),
                ciLower, ciUpper, 0.95, ciMethod, ciTarget, ciUnavailableReason, dCiLower, dCiUpper);
    }

    /**
     * One-Way ANOVA for Numeric + Multi-Category (>2 groups).
     */
    public TestResult computeOneWayAnova(String numVar, String catVar, List<String> rawNum, List<String> rawCat) {
        Map<String, List<Double>> groups = new HashMap<>();

        int nTotal = Math.min(rawNum.size(), rawCat.size());
        for (int i = 0; i < nTotal; i++) {
            String sNum = rawNum.get(i);
            String sCat = rawCat.get(i);
            if (isValidNumber(sNum) && sCat != null && !sCat.trim().isEmpty() && !sCat.equalsIgnoreCase("na")) {
                groups.computeIfAbsent(sCat.trim(), k -> new ArrayList<>()).add(Double.parseDouble(sNum));
            }
        }

        int k = groups.size();
        if (k < 3) {
            return new TestResult("ONE_WAY_ANOVA", 0.0, 1.0, 0.0, "eta_squared", 0, null, false,
                    "ANOVA requires at least 3 distinct groups.", 0.0);
        }

        int totalN = 0;
        double grandSum = 0.0;
        for (List<Double> g : groups.values()) {
            if (g.size() < 2) {
                return new TestResult("ONE_WAY_ANOVA", 0.0, 1.0, 0.0, "eta_squared", 0, null, false,
                        "Every group must have at least 2 observations for One-Way ANOVA.", 0.0);
            }
            totalN += g.size();
            for (double v : g) grandSum += v;
        }

        double grandMean = grandSum / totalN;
        double ssBetween = 0.0;
        double ssWithin = 0.0;

        for (List<Double> g : groups.values()) {
            double gSum = 0;
            for (double v : g) gSum += v;
            double gMean = gSum / g.size();
            ssBetween += g.size() * Math.pow(gMean - grandMean, 2);

            for (double v : g) {
                ssWithin += Math.pow(v - gMean, 2);
            }
        }

        int dfBetween = k - 1;
        int dfWithin = totalN - k;

        if (dfWithin <= 0 || ssWithin == 0.0) {
            return new TestResult("ONE_WAY_ANOVA", 0.0, 1.0, 0.0, "eta_squared", totalN, (double) dfBetween, false,
                    "Zero within-group variance or insufficient degrees of freedom.", 0.0);
        }

        double msBetween = ssBetween / dfBetween;
        double msWithin = ssWithin / dfWithin;
        double fStat = (msWithin > 0) ? msBetween / msWithin : 0.0;

        double pValue = 1.0;
        try {
            FDistribution fDist = new FDistribution(dfBetween, dfWithin);
            pValue = 1.0 - fDist.cumulativeProbability(fStat);
            pValue = Math.max(0.0, Math.min(1.0, pValue));
        } catch (Exception e) {
            pValue = 1.0;
        }

        double etaSquared = (ssBetween + ssWithin > 0) ? ssBetween / (ssBetween + ssWithin) : 0.0;
        boolean significant = pValue < 0.05;
        double rankScore = etaSquared * (1.0 - pValue) * Math.log10(totalN + 1);

        String effectDesc = etaSquared >= 0.14 ? "large" : etaSquared >= 0.06 ? "medium" : "small";
        String interpretation = significant
                ? String.format("Evidence of statistically significant variation in %s across %d '%s' groups (One-way ANOVA: F=%.3f, p=%.4f, eta^2=%.3f [%s effect size], N=%d).",
                numVar, k, catVar, fStat, pValue, etaSquared, effectDesc, totalN)
                : String.format("No statistically significant differences in %s were found across '%s' groups (F=%.3f, p=%.4f, N=%d).",
                numVar, catVar, fStat, pValue, totalN);

        return new TestResult("ONE_WAY_ANOVA", round(fStat, 4), round(pValue, 4), round(etaSquared, 4),
                "eta_squared", totalN, (double) dfBetween, significant, interpretation, round(rankScore, 4),
                null, null, 0.95, null, "Group mean difference",
                "A single confidence interval is not defined for a one-way ANOVA; use the post-hoc pairwise comparisons for interval estimates.",
                null, null);
    }

    /**
     * Chi-Square Test of Independence for Categorical + Categorical pairs.
     */
    public TestResult computeChiSquare(String var1, String var2, List<String> raw1, List<String> raw2) {
        Map<String, Map<String, Integer>> table = new HashMap<>();
        Set<String> cols = new HashSet<>();

        int nTotal = Math.min(raw1.size(), raw2.size());
        for (int i = 0; i < nTotal; i++) {
            String v1 = raw1.get(i);
            String v2 = raw2.get(i);
            if (v1 != null && !v1.trim().isEmpty() && !v1.equalsIgnoreCase("na") &&
                    v2 != null && !v2.trim().isEmpty() && !v2.equalsIgnoreCase("na")) {
                table.computeIfAbsent(v1.trim(), k -> new HashMap<>())
                        .merge(v2.trim(), 1, Integer::sum);
                cols.add(v2.trim());
            }
        }

        int r = table.size();
        int c = cols.size();

        if (r < 2 || c < 2) {
            return new TestResult("CHI_SQUARE", 0.0, 1.0, 0.0, "cramers_v", 0, null, false,
                    "Contingency table must have at least 2 rows and 2 columns for Chi-Square testing.", 0.0);
        }

        // Calculate marginal row and column totals
        Map<String, Integer> rowTotals = new HashMap<>();
        Map<String, Integer> colTotals = new HashMap<>();
        int N = 0;

        for (Map.Entry<String, Map<String, Integer>> rowEntry : table.entrySet()) {
            int rowSum = 0;
            for (Map.Entry<String, Integer> cell : rowEntry.getValue().entrySet()) {
                rowSum += cell.getValue();
                colTotals.merge(cell.getKey(), cell.getValue(), Integer::sum);
            }
            rowTotals.put(rowEntry.getKey(), rowSum);
            N += rowSum;
        }

        if (N < 5) {
            return new TestResult("CHI_SQUARE", 0.0, 1.0, 0.0, "cramers_v", N, null, false,
                    "Insufficient sample size (N < 5) for Chi-Square test of independence.", 0.0);
        }

        double chiSquare = 0.0;
        for (String rowKey : table.keySet()) {
            int rowSum = rowTotals.get(rowKey);
            for (String colKey : cols) {
                int observed = table.get(rowKey).getOrDefault(colKey, 0);
                int colSum = colTotals.getOrDefault(colKey, 0);
                double expected = ((double) rowSum * colSum) / N;

                if (expected > 0) {
                    chiSquare += Math.pow(observed - expected, 2) / expected;
                }
            }
        }

        double df = (r - 1) * (c - 1);
        double pValue = 1.0;
        try {
            ChiSquaredDistribution chiDist = new ChiSquaredDistribution(df);
            pValue = 1.0 - chiDist.cumulativeProbability(chiSquare);
            pValue = Math.max(0.0, Math.min(1.0, pValue));
        } catch (Exception e) {
            pValue = 1.0;
        }

        // Cramer's V
        double minDim = Math.min(r - 1, c - 1);
        double cramersV = (minDim > 0 && N > 0) ? Math.sqrt(chiSquare / (N * minDim)) : 0.0;
        cramersV = Math.min(1.0, cramersV);

        boolean significant = pValue < 0.05;
        double rankScore = cramersV * (1.0 - pValue) * Math.log10(N + 1);

        String effectMagnitude = cramersV > 0.5 ? "strong" : cramersV > 0.3 ? "moderate" : "weak";
        String interpretation = significant
                ? String.format("Evidence of statistically significant dependence between '%s' and '%s' via Pearson Chi-Square (chi^2=%.2f, df=%.0f, p=%.4f, Cramer's V=%.3f [%s association], N=%d). Does not establish causality.",
                var1, var2, chiSquare, df, pValue, cramersV, effectMagnitude, N)
                : String.format("No statistically significant association observed between '%s' and '%s' (chi^2=%.2f, p=%.4f, df=%.0f, N=%d).",
                var1, var2, chiSquare, pValue, df, N);

        return new TestResult("CHI_SQUARE", round(chiSquare, 4), round(pValue, 4), round(cramersV, 4),
                "cramers_v", N, df, significant, interpretation, round(rankScore, 4),
                null, null, 0.95, null, "Association strength (Cramer's V)",
                "A single confidence interval is not defined for a Chi-Square test of independence; the effect size is reported instead.",
                null, null);
    }

    private boolean isValidNumber(String s) {
        if (s == null || s.trim().isEmpty() || s.equalsIgnoreCase("na") || s.equalsIgnoreCase("nan") || s.equalsIgnoreCase("null")) {
            return false;
        }
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double round(double val, int places) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.0;
        double factor = Math.pow(10, places);
        return Math.round(val * factor) / factor;
    }
}
