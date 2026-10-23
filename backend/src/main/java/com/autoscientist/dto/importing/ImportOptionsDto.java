package com.autoscientist.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Options the import wizard lets the user choose before a file is committed.
 * All fields are optional — missing values fall back to safe auto-detection.
 * Serialized verbatim into Dataset.importOptionsJson for provenance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOptionsDto {
    /** Worksheet name (Excel only). Null → first sheet. */
    private String sheetName;
    /** 0-based worksheet index (Excel only, used when sheetName is null). */
    private Integer sheetIndex;
    /** 1-based header row number. Null → auto-detect. */
    private Integer headerRow;
    /** Columns removed from analysis (the file itself is never modified). */
    private List<String> excludedColumns;
    /** Columns the user explicitly forces to identifier semantics. */
    private List<String> forceIdentifier;
    /** Columns the user explicitly includes in analysis despite an identifier guess. */
    private List<String> forceNonIdentifier;
}
