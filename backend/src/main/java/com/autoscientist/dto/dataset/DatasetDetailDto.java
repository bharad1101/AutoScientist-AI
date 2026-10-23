package com.autoscientist.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDetailDto {
    private Long id;
    private String name;
    private String originalFilename;
    private Long fileSize;
    private Integer rowCount;
    private Integer columnCount;
    private String datasetType;
    private String description;
    private String sourceFormat;
    private String sheetName;
    private Integer headerRow;
    private Integer version;
    private Long parentDatasetId;
    private List<String> excludedColumns;
    private Map<String, Object> importOptions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DatasetColumnDto> columns;
    private DataQualityReportDto qualityReport;
}
