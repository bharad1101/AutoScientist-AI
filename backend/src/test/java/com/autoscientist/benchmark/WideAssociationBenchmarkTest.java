package com.autoscientist.benchmark;

import com.autoscientist.analysis.association.AssociationEngine;
import com.autoscientist.analysis.statistics.StatisticalTestEngine;
import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.entity.Association;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in wide-data benchmark. Run with:
 *
 * <pre>./mvnw -Dtest=WideAssociationBenchmarkTest -Dbenchmark=true test</pre>
 *
 * The small functional correctness test always runs in the normal suite; the
 * expensive wide benchmark is gated by the same {@code benchmark} property used
 * by {@code WorkflowPerformanceBenchmarkTest}.
 */
class WideAssociationBenchmarkTest {

    @Test
    void discoversPerfectLinearAssociationOnSmallDataset() throws Exception {
        StringBuilder csv = new StringBuilder("x,y\n");
        for (int i = 0; i < 20; i++) {
            csv.append(i).append(',').append(i * 2).append('\n');
        }
        List<DatasetColumn> columns = List.of(
                DatasetColumn.builder().columnName("x").dataType("NUMERIC").uniqueCount(20).build(),
                DatasetColumn.builder().columnName("y").dataType("NUMERIC").uniqueCount(20).build());

        Dataset dataset = Dataset.builder().id(1L).name("small").build();
        AssociationEngine engine = new AssociationEngine(new StatisticalTestEngine(), new ResourceBudgetProperties());
        List<Association> result = engine.discoverAssociations(
                new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)), dataset, columns);

        assertEquals(1, result.size());
        Association association = result.get(0);
        assertEquals("x", association.getVar1());
        assertEquals("y", association.getVar2());
        assertNotNull(association.getTestType());
        assertEquals("PEARSON", association.getTestType());
        assertEquals(20, association.getSampleSize());
        assertTrue(association.getStatistic() > 0.999,
                "Pearson r should be approximately 1.0 for y = 2x");
        assertTrue(association.getIsSignificant());
    }

    @Test
    @EnabledIfSystemProperty(named = "benchmark", matches = "true")
    void benchmarkWide() throws Exception {
        run(20_000, 200, 20);
    }

    private void run(int rows, int numericCols, int categoricalCols) throws Exception {
        Random random = new Random(42);
        List<DatasetColumn> columns = new ArrayList<>();
        StringBuilder header = new StringBuilder("id");
        for (int c = 0; c < numericCols; c++) {
            header.append(",n").append(c);
            columns.add(DatasetColumn.builder().columnName("n" + c).dataType("NUMERIC")
                    .uniqueCount(rows).build());
        }
        for (int c = 0; c < categoricalCols; c++) {
            header.append(",c").append(c);
            columns.add(DatasetColumn.builder().columnName("c" + c).dataType("CATEGORICAL")
                    .uniqueCount(5).build());
        }
        header.append('\n');

        StringBuilder csv = new StringBuilder(header);
        for (int r = 0; r < rows; r++) {
            csv.append(r);
            for (int c = 0; c < numericCols; c++) {
                double base = (r % 97) * 0.3 + c * 0.7 + random.nextGaussian();
                csv.append(',').append(String.format(Locale.ROOT, "%.5f", base));
            }
            for (int c = 0; c < categoricalCols; c++) {
                csv.append(',').append("g").append((r + c) % 5);
            }
            csv.append('\n');
        }

        Dataset dataset = Dataset.builder().id(1L).name("wide").build();
        // Use default budget properties with generous column limit
        com.autoscientist.config.ResourceBudgetProperties budgetProps = new com.autoscientist.config.ResourceBudgetProperties();
        AssociationEngine engine = new AssociationEngine(new StatisticalTestEngine(), budgetProps);
        long start = System.nanoTime();
        List<Association> result = engine.discoverAssociations(
                new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)), dataset, columns);
        long ms = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf(Locale.ROOT,
                "WIDE-BENCH rows=%d numeric=%d categorical=%d pairs_out=%d ms=%d%n",
                rows, numericCols, categoricalCols, result.size(), ms);
    }
}
