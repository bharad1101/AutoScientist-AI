package com.autoscientist.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Analysis-time column exclusions. Non-destructive: the uploaded file keeps every
 * column; excluded columns are simply skipped by analytics engines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetExclusionRequestDto {
    private List<String> excludedColumns;
}
