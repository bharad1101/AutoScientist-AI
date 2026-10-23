package com.autoscientist.dto.importing;

import com.autoscientist.dto.dataset.DatasetColumnDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Everything the import wizard needs to show before committing an upload:
 * workbook sheets, the header row that will be used, a data preview, inferred
 * column types (computed on the preview sample, honestly labelled) and any
 * warnings that the user should fix before importing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewDto {

    private String originalFilename;
    private Long fileSize;
    /** CSV, XLSX or XLS. */
    private String sourceFormat;
    private boolean excel;

    private List<SheetDto> sheets;
    private String sheetName;
    /** 1-based header row that will be used (detected or user-specified). */
    private Integer headerRow;
    private boolean headerRowDetected;

    private List<String> headers;
    /** First rows of data (after the header), for the preview table. */
    private List<List<String>> previewRows;

    private Integer rowCount;
    private Integer columnCount;
    /** Inferred types/stats computed on the preview sample. */
    private List<DatasetColumnDto> columns;

    /** Human-readable issues: duplicate headers, ragged rows, identifier columns... */
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SheetDto {
        private Integer index;
        private String name;
        private Integer rowCount;
        private Integer columnCount;
    }
}
