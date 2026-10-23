package com.autoscientist.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Row-level presence grid for the missing-value matrix heat-map.
 * {@code present[row][column]} is true when the cell holds an observed value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingnessMatrixDto {
    private List<String> variables;
    private int[] rowIndexes;
    private boolean[][] present;
    private long totalRows;
    /** true when the dataset has more rows than the rendered sample. */
    private boolean truncated;
}
