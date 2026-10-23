package com.autoscientist.analysis.drift;

import com.autoscientist.dto.drift.ColumnDriftDto;
import com.autoscientist.dto.drift.DatasetDriftReportDto;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class DataDriftEngine {

    public DatasetDriftReportDto analyzeDrift(Dataset refDataset, InputStream refStream,
                                              Dataset targetDataset, InputStream targetStream) throws Exception {

        Map<String, List<String>> refData = parseCsv(refStream);
        Map<String, List<String>> targetData = parseCsv(targetStream);

        // Find common columns
        Set<String> commonCols = new LinkedHashSet<>(refData.keySet());
        commonCols.retainAll(targetData.keySet());

        List<ColumnDriftDto> columnDrifts = new ArrayList<>();
        double totalPsi = 0.0;
        int driftedCount = 0;

        for (String colName : commonCols) {
            List<String> refVals = refData.get(colName);
            List<String> tarVals = targetData.get(colName);

            // Determine if column is numeric or categorical
            DatasetColumn colMeta = refDataset.getColumns().stream()
                    .filter(c -> c.getColumnName().equalsIgnoreCase(colName))
                    .findFirst()
                    .orElse(null);

            boolean isNumeric = colMeta != null && "NUMERIC".equalsIgnoreCase(colMeta.getDataType());

            ColumnDriftDto colDrift;
            if (isNumeric) {
                colDrift = computeNumericDrift(colName, refVals, tarVals);
            } else {
                colDrift = computeCategoricalDrift(colName, refVals, tarVals);
            }

            if (colDrift != null) {
                columnDrifts.add(colDrift);
                totalPsi += colDrift.getPsiScore();
                if (!"NO_DRIFT".equals(colDrift.getDriftStatus())) {
                    driftedCount++;
                }
            }
        }

        int totalFeatures = columnDrifts.size();
        double avgPsi = totalFeatures > 0 ? totalPsi / totalFeatures : 0.0;
        avgPsi = Math.round(avgPsi * 1000.0) / 1000.0;

        String overallStatus = (avgPsi >= 0.25 || driftedCount >= 3) ? "ACTION_REQUIRED"
                : (avgPsi >= 0.1 || driftedCount > 0) ? "LOW_RISK" : "STABLE";

        String summaryRec = overallStatus.equals("STABLE")
                ? "All feature distributions match baseline characteristics (Average PSI = " + avgPsi + "). Model predictions remain calibrated."
                : overallStatus.equals("LOW_RISK")
                ? String.format("Moderate shift detected in %d of %d features (Average PSI = %.3f). Monitor downstream predictions closely.",
                driftedCount, totalFeatures, avgPsi)
                : String.format("Significant distribution drift identified in %d of %d features (Average PSI = %.3f). Retraining or data recalibration is strongly recommended.",
                driftedCount, totalFeatures, avgPsi);

        return DatasetDriftReportDto.builder()
                .referenceDatasetId(refDataset.getId())
                .referenceDatasetName(refDataset.getName())
                .targetDatasetId(targetDataset.getId())
                .targetDatasetName(targetDataset.getName())
                .overallDriftIndex(avgPsi)
                .overallStatus(overallStatus)
                .totalFeaturesAnalyzed(totalFeatures)
                .driftedFeaturesCount(driftedCount)
                .columnDrifts(columnDrifts)
                .summaryRecommendation(summaryRec)
                .build();
    }

    private ColumnDriftDto computeNumericDrift(String colName, List<String> refRaw, List<String> tarRaw) {
        List<Double> refNums = parseDoubles(refRaw);
        List<Double> tarNums = parseDoubles(tarRaw);

        if (refNums.size() < 5 || tarNums.size() < 5) return null;

        Collections.sort(refNums);
        Collections.sort(tarNums);

        double refMean = computeMean(refNums);
        double tarMean = computeMean(tarNums);
        double shiftPct = (Math.abs(refMean) > 1e-6) ? ((tarMean - refMean) / Math.abs(refMean)) * 100.0 : 0.0;

        // 10 Quantile bins based on reference dataset
        int numBins = 10;
        double[] binEdges = new double[numBins + 1];
        binEdges[0] = refNums.get(0) - 1e-5;
        binEdges[numBins] = refNums.get(refNums.size() - 1) + 1e-5;
        for (int i = 1; i < numBins; i++) {
            int idx = (int) Math.round((i / (double) numBins) * (refNums.size() - 1));
            binEdges[i] = refNums.get(idx);
        }

        // Count bins
        double[] refCounts = countBins(refNums, binEdges);
        double[] tarCounts = countBins(tarNums, binEdges);

        double psi = calculatePsi(refCounts, tarCounts, refNums.size(), tarNums.size());
        psi = Math.round(psi * 10000.0) / 10000.0;

        double pVal = 1.0;
        double ksStat = Double.NaN;
        double ksP = Double.NaN;
        try {
            double[] refArr = refNums.stream().mapToDouble(Double::doubleValue).toArray();
            double[] tarArr = tarNums.stream().mapToDouble(Double::doubleValue).toArray();
            pVal = TestUtils.tTest(refArr, tarArr);
            pVal = Math.round(pVal * 10000.0) / 10000.0;
            org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest ks =
                    new org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest();
            ksStat = Math.round(ks.kolmogorovSmirnovStatistic(refArr, tarArr) * 10000.0) / 10000.0;
            ksP = Math.round(ks.kolmogorovSmirnovTest(refArr, tarArr) * 10000.0) / 10000.0;
        } catch (Exception ignored) {}

        String status = psi >= 0.25 ? "SIGNIFICANT_DRIFT" : (psi >= 0.10 ? "MODERATE_DRIFT" : "NO_DRIFT");
        String interp = String.format("PSI = %.4f (Mean shift: %.1f%%, t-test p = %.4f, KS D = %.4f, KS p = %.4f). %s",
                psi, shiftPct, pVal, ksStat, ksP,
                status.equals("NO_DRIFT") ? "Distribution is statistically consistent with reference."
                        : status.equals("MODERATE_DRIFT") ? "Mild distribution drift detected; examine sample shifts."
                        : "Substantial statistical divergence detected. Feature requires monitoring.");

        return ColumnDriftDto.builder()
                .columnName(colName)
                .dataType("NUMERIC")
                .psiScore(psi)
                .driftStatus(status)
                .pValue(pVal)
                .ksStatistic(Double.isNaN(ksStat) ? null : ksStat)
                .ksPValue(Double.isNaN(ksP) ? null : ksP)
                .referenceMean(Math.round(refMean * 100.0) / 100.0)
                .targetMean(Math.round(tarMean * 100.0) / 100.0)
                .meanShiftPct(Math.round(shiftPct * 10.0) / 10.0)
                .binEdges(toBoxedList(binEdges))
                .referenceBinProportions(toProportions(refCounts, refNums.size()))
                .targetBinProportions(toProportions(tarCounts, tarNums.size()))
                .interpretation(interp)
                .build();
    }

    private List<Double> toBoxedList(double[] values) {
        List<Double> out = new ArrayList<>();
        for (double v : values) out.add(Math.round(v * 10000.0) / 10000.0);
        return out;
    }

    /** Converts bin counts into shares so the two datasets can be plotted together. */
    private List<Double> toProportions(double[] counts, int total) {
        List<Double> out = new ArrayList<>();
        if (total <= 0) {
            for (int i = 0; i < counts.length; i++) out.add(0.0);
            return out;
        }
        for (double c : counts) {
            out.add(Math.round((c / total) * 10000.0) / 10000.0);
        }
        return out;
    }

    private ColumnDriftDto computeCategoricalDrift(String colName, List<String> refRaw, List<String> tarRaw) {
        Map<String, Integer> refCounts = new HashMap<>();
        for (String s : refRaw) {
            if (s != null && !s.isBlank()) refCounts.put(s.trim(), refCounts.getOrDefault(s.trim(), 0) + 1);
        }
        Map<String, Integer> tarCounts = new HashMap<>();
        for (String s : tarRaw) {
            if (s != null && !s.isBlank()) tarCounts.put(s.trim(), tarCounts.getOrDefault(s.trim(), 0) + 1);
        }

        Set<String> allCats = new HashSet<>();
        allCats.addAll(refCounts.keySet());
        allCats.addAll(tarCounts.keySet());

        if (allCats.isEmpty()) return null;

        int nRef = refRaw.size();
        int nTar = tarRaw.size();

        double psi = 0.0;
        double eps = 1e-4;
        Map<String, Double> refDist = new HashMap<>();
        Map<String, Double> tarDist = new HashMap<>();

        for (String cat : allCats) {
            double pRef = (refCounts.getOrDefault(cat, 0) + eps) / (nRef + eps * allCats.size());
            double pTar = (tarCounts.getOrDefault(cat, 0) + eps) / (nTar + eps * allCats.size());
            psi += (pTar - pRef) * Math.log(pTar / pRef);

            refDist.put(cat, Math.round(pRef * 1000.0) / 1000.0);
            tarDist.put(cat, Math.round(pTar * 1000.0) / 1000.0);
        }
        psi = Math.round(psi * 10000.0) / 10000.0;

        String status = psi >= 0.25 ? "SIGNIFICANT_DRIFT" : (psi >= 0.10 ? "MODERATE_DRIFT" : "NO_DRIFT");
        String interp = String.format("Categorical PSI = %.4f across %d unique categories. %s",
                psi, allCats.size(),
                status.equals("NO_DRIFT") ? "Category frequencies align with reference baseline." : "Category frequency divergence observed.");

        return ColumnDriftDto.builder()
                .columnName(colName)
                .dataType("CATEGORICAL")
                .psiScore(psi)
                .driftStatus(status)
                .pValue(1.0)
                .referenceDistribution(refDist)
                .targetDistribution(tarDist)
                .interpretation(interp)
                .build();
    }

    private double calculatePsi(double[] refCounts, double[] tarCounts, int nRef, int nTar) {
        double psi = 0;
        double eps = 1e-4;
        for (int i = 0; i < refCounts.length; i++) {
            double p = (refCounts[i] + eps) / (nRef + eps * refCounts.length);
            double q = (tarCounts[i] + eps) / (nTar + eps * tarCounts.length);
            psi += (q - p) * Math.log(q / p);
        }
        return Math.max(0.0, psi);
    }

    private double[] countBins(List<Double> values, double[] edges) {
        int numBins = edges.length - 1;
        double[] counts = new double[numBins];
        for (double v : values) {
            for (int b = 0; b < numBins; b++) {
                if (b == numBins - 1) {
                    if (v >= edges[b] && v <= edges[b + 1]) counts[b]++;
                } else {
                    if (v >= edges[b] && v < edges[b + 1]) counts[b]++;
                }
            }
        }
        return counts;
    }

    private List<Double> parseDoubles(List<String> raw) {
        List<Double> list = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                try {
                    list.add(Double.parseDouble(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    private double computeMean(List<Double> nums) {
        double sum = 0;
        for (double d : nums) sum += d;
        return sum / nums.size();
    }

    private Map<String, List<String>> parseCsv(InputStream stream) throws Exception {
        Map<String, List<String>> data = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            for (String h : headers) {
                data.put(h, new ArrayList<>());
            }

            for (CSVRecord rec : parser) {
                for (String h : headers) {
                    data.get(h).add(rec.isMapped(h) ? rec.get(h) : "");
                }
            }
        }
        return data;
    }
}
