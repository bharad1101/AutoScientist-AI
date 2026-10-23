package com.autoscientist.dto.dataset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetDto {
    private Long id;
    private String name;
    private String originalFilename;
    private Long fileSize;
    private Integer rowCount;
    private Integer columnCount;
    private String datasetType;
    private String description;
    private Double qualityScore;
    private String sourceFormat;
    private String sheetName;
    private Integer version;
    private Long parentDatasetId;
    private List<String> excludedColumns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
