package com.autoscientist;

import com.autoscientist.analysis.profiling.DatasetProfiler;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DatasetProfilerTest {

    private final DatasetProfiler profiler = new DatasetProfiler();

    @Test
    void testProfilingNumericAndCategoricalColumns() throws Exception {
        String csv = "name,age,salary,gender\n" +
                "Alice,25,50000,F\n" +
                "Bob,30,60000,M\n" +
                "Charlie,35,70000,M\n" +
                "Diana,40,80000,F\n";

        Dataset dataset = Dataset.builder().name("Test").build();
        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        DatasetProfiler.ProfilingResult result = profiler.profile(in, dataset);

        assertNotNull(result);
        assertEquals(4, result.getRowCount());
        assertEquals(4, result.getColumnCount());

        DatasetColumn ageCol = result.getColumns().stream()
                .filter(c -> c.getColumnName().equals("age"))
                .findFirst().orElseThrow();

        assertEquals("NUMERIC", ageCol.getDataType());
        assertEquals(25.0, ageCol.getMinVal());
        assertEquals(40.0, ageCol.getMaxVal());
        assertEquals(32.5, ageCol.getMeanVal());
        assertEquals(0, ageCol.getMissingCount());

        DatasetColumn genderCol = result.getColumns().stream()
                .filter(c -> c.getColumnName().equals("gender"))
                .findFirst().orElseThrow();

        assertEquals("CATEGORICAL", genderCol.getDataType());
        assertEquals(2, genderCol.getUniqueCount());
        assertNotNull(genderCol.getCategoriesJson());
    }

    @Test
    void testMissingValuesDetection() throws Exception {
        String csv = "id,score\n" +
                "1,10.0\n" +
                "2,NA\n" +
                "3,\n" +
                "4,40.0\n";

        Dataset dataset = Dataset.builder().name("Test").build();
        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        DatasetProfiler.ProfilingResult result = profiler.profile(in, dataset);

        DatasetColumn scoreCol = result.getColumns().stream()
                .filter(c -> c.getColumnName().equals("score"))
                .findFirst().orElseThrow();

        assertEquals(2, scoreCol.getMissingCount());
        assertEquals(50.0, scoreCol.getMissingPct());
    }

    @Test
    void testIdentifierColumnDetection() throws Exception {
        String csv = "customer_id,age,salary\n" +
                "CUST_101,28,45000\n" +
                "CUST_102,34,52000\n" +
                "CUST_103,42,68000\n";

        Dataset dataset = Dataset.builder().name("Customer Dataset").build();
        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        DatasetProfiler.ProfilingResult result = profiler.profile(in, dataset);

        DatasetColumn idCol = result.getColumns().stream()
                .filter(c -> c.getColumnName().equals("customer_id"))
                .findFirst().orElseThrow();
        assertEquals("IDENTIFIER", idCol.getDataType());

        DatasetColumn ageCol = result.getColumns().stream()
                .filter(c -> c.getColumnName().equals("age"))
                .findFirst().orElseThrow();
        assertEquals("NUMERIC", ageCol.getDataType());
    }

    @Test
    void testProfilingDetectsSpaceSeparatedTimestamps() throws Exception {
        String csv = "id,when,score\n" +
                "1,2024-01-01 01:00,10\n" +
                "2,2024-01-01 02:00,20\n" +
                "3,2024-01-02 03:00,30\n";

        Dataset dataset = Dataset.builder().name("Timestamps").build();
        DatasetProfiler.ProfilingResult result = profiler.profile(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), dataset);

        DatasetColumn when = result.getColumns().stream()
                .filter(c -> "when".equals(c.getColumnName())).findFirst().orElseThrow();
        assertEquals("DATE_TIME", when.getDataType());
    }
}
