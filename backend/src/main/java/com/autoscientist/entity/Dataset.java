package com.autoscientist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /**
     * Canonical analysis source. For CSV uploads this is the uploaded file;
     * for Excel it is the CSV rendition of the chosen sheet/header row, so every
     * downstream engine keeps reading one format. The original workbook (if any)
     * is preserved untouched in {@link #sourceStoredFilename}.
     */
    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    /** Original uploaded workbook (.xls/.xlsx) when sourceFormat is Excel; else null. */
    @Column(name = "source_stored_filename")
    private String sourceStoredFilename;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "row_count", nullable = false)
    @Builder.Default
    private Integer rowCount = 0;

    @Column(name = "column_count", nullable = false)
    @Builder.Default
    private Integer columnCount = 0;

    @Column(name = "dataset_type", length = 50)
    @Builder.Default
    private String datasetType = "MIXED";

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Source file format: CSV, XLSX, XLS or DERIVED (cleaned copy of another dataset). */
    @Column(name = "source_format", length = 16)
    @Builder.Default
    private String sourceFormat = "CSV";

    /** Worksheet the rows were read from (Excel only). */
    @Column(name = "sheet_name", length = 128)
    private String sheetName;

    /** 1-based header row number used at import (Excel only). */
    @Column(name = "header_row")
    private Integer headerRow;

    /**
     * Monotonic dataset version. Bumped whenever profiling configuration or
     * analysis-relevant settings change, so cached profiles can be invalidated.
     * The uploaded file itself is never modified.
     */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /** Set when this dataset is a cleaned/derived copy of an original upload. */
    @Column(name = "parent_dataset_id")
    private Long parentDatasetId;

    /** JSON array of column names the user excluded from analysis (original data untouched). */
    @Column(name = "excluded_columns_json", columnDefinition = "TEXT")
    private String excludedColumnsJson;

    /** JSON blob of import-wizard options (row limit, header row, sheet, formula policy...). */
    @Column(name = "import_options_json", columnDefinition = "TEXT")
    private String importOptionsJson;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DatasetColumn> columns = new ArrayList<>();

    @OneToOne(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private DataQualityReport qualityReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.datasetType == null) {
            this.datasetType = "MIXED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
