package com.autoscientist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.Min;

@Validated

/**
 * Typed configuration properties expressing operational resource budgets.
 * All fields have generous defaults; production environments can override them via
 * environment variables (e.g. RESOURCE_BUDGET_MAX_COLUMNS, RESOURCE_BUDGET_MAX_ROWS).
 */

@Component
@ConfigurationProperties(prefix = "resource-budget")
public class ResourceBudgetProperties {

    /** Maximum memory (in MB) that can be used for in‑memory processing. */
    @Min(1)
    private int maxMemoryMb = 2048;

    /** Maximum temporary storage (in MB) for transient files. */
    @Min(1)
    private int maxTempStorageMb = 1024;

    /** Maximum number of points for chart rendering. */
    @Min(1)
    private int chartMaxPoints = 5000;

    /** Maximum number of columns that can be processed synchronously (in‑memory). */
    @Min(1)
    private int maxColumns = 5000;
    @Min(1)
    private int maxRows = 1_000_000;
    @Min(1)
    private int maxCells = 10_000_000;
    @Min(1)
    private int maxSheets = 20;
    @Min(1)
    private int maxMb = 50;
    @Min(1)
    private int associationConcurrency = 4;
    @Min(1)
    private int jobConcurrency = 4;
    @Min(1)
    private int tempFileTtlHours = 24;
    @Min(1)
    private int columnBlockSize = 200;
    @Min(1)
    private int defaultSampleRows = 10_000;

        public int getMaxMemoryMb() { return maxMemoryMb; }
    public void setMaxMemoryMb(int maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }

    public int getMaxTempStorageMb() { return maxTempStorageMb; }
    public void setMaxTempStorageMb(int maxTempStorageMb) { this.maxTempStorageMb = maxTempStorageMb; }

    public int getChartMaxPoints() { return chartMaxPoints; }
    public void setChartMaxPoints(int chartMaxPoints) { this.chartMaxPoints = chartMaxPoints; }

    public int getMaxColumns() { return maxColumns; }
    public void setMaxColumns(int maxColumns) { this.maxColumns = maxColumns; }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
    public int getMaxCells() { return maxCells; }
    public void setMaxCells(int maxCells) { this.maxCells = maxCells; }
    public int getMaxSheets() { return maxSheets; }
    public void setMaxSheets(int maxSheets) { this.maxSheets = maxSheets; }
    public int getMaxMb() { return maxMb; }
    public void setMaxMb(int maxMb) { this.maxMb = maxMb; }
    public int getAssociationConcurrency() { return associationConcurrency; }
    public void setAssociationConcurrency(int associationConcurrency) { this.associationConcurrency = associationConcurrency; }
    public int getJobConcurrency() { return jobConcurrency; }
    public void setJobConcurrency(int jobConcurrency) { this.jobConcurrency = jobConcurrency; }
    public int getTempFileTtlHours() { return tempFileTtlHours; }
    public void setTempFileTtlHours(int tempFileTtlHours) { this.tempFileTtlHours = tempFileTtlHours; }
    public int getColumnBlockSize() { return columnBlockSize; }
    public void setColumnBlockSize(int columnBlockSize) { this.columnBlockSize = columnBlockSize; }
    public int getDefaultSampleRows() { return defaultSampleRows; }
    public void setDefaultSampleRows(int defaultSampleRows) { this.defaultSampleRows = defaultSampleRows; }
}
