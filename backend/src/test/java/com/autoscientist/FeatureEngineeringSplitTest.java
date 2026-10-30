package com.autoscientist;

import com.autoscientist.analysis.feature.FeatureEngineeringEngine;
import com.autoscientist.entity.DatasetColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureEngineeringSplitTest {

    private final FeatureEngineeringEngine engine = new FeatureEngineeringEngine();

    private List<DatasetColumn> columns() {
        return List.of(
                DatasetColumn.builder().columnName("x").dataType("NUMERIC").uniqueCount(10).build(),
                DatasetColumn.builder().columnName("y").dataType("NUMERIC").uniqueCount(10).build());
    }

    private FeatureEngineeringEngine.DatasetSplit split(String csv) throws Exception {
        return engine.prepareDataset(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                columns(), "y", "REGRESSION");
    }

    @Test
    @DisplayName("Two rows produce disjoint train and test partitions (no leakage)")
    void twoRowsAreSplitDisjointly() throws Exception {
        FeatureEngineeringEngine.DatasetSplit split = split("x,y\n1,10\n2,20\n");

        assertThat(split.getXTrain().length).isEqualTo(1);
        assertThat(split.getXVal().length).isZero();
        assertThat(split.getXTest().length).isEqualTo(1);
        assertThat(split.getXVal().length + split.getXTrain().length + split.getXTest().length).isEqualTo(2);
    }

    @Test
    @DisplayName("Four rows keep every partition disjoint and sum to the input size")
    void fourRowsAreSplitDisjointly() throws Exception {
        FeatureEngineeringEngine.DatasetSplit split = split("x,y\n1,10\n2,20\n3,30\n4,40\n");

        int train = split.getXTrain().length;
        int val = split.getXVal().length;
        int test = split.getXTest().length;

        assertThat(train).isPositive();
        assertThat(val).isPositive();
        assertThat(test).isPositive();
        assertThat(train + val + test).isEqualTo(4);
        assertThat(split.getPreprocessingConfig()).containsEntry("smallSample", true);
    }

    @Test
    @DisplayName("Single row degrades to train-only instead of duplicating rows")
    void singleRowHasNoHeldOutDuplicates() throws Exception {
        FeatureEngineeringEngine.DatasetSplit split = split("x,y\n1,10\n");

        assertThat(split.getXTrain().length).isEqualTo(1);
        assertThat(split.getXVal().length).isZero();
        assertThat(split.getXTest().length).isZero();
        assertThat(split.getPreprocessingConfig()).containsEntry("smallSample", true);
    }
}
