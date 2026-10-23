package com.autoscientist.analysis.core;

/**
 * Canonical operation names recorded in the {@code analysis_runs} table.
 * Keep values stable: workflow stages, report sections and the evidence graph
 * all key off these strings.
 */
public final class AnalysisOperations {

    private AnalysisOperations() {}

    public static final String PROFILE = "PROFILE";
    public static final String QUALITY = "QUALITY";
    public static final String IMPORT = "IMPORT";
    public static final String EDA_CORRELATIONS = "EDA_CORRELATIONS";
    public static final String EDA_BOXPLOTS = "EDA_BOXPLOTS";
    public static final String EDA_GROUPED = "EDA_GROUPED";
    public static final String EDA_HISTOGRAMS = "EDA_HISTOGRAMS";
    public static final String EDA_SCATTER = "EDA_SCATTER";
    public static final String EDA_MISSINGNESS = "EDA_MISSINGNESS";
    public static final String EDA_CATEGORICAL = "EDA_CATEGORICAL";
    public static final String EDA_TIMESERIES = "EDA_TIMESERIES";
    public static final String ASSOCIATIONS = "ASSOCIATIONS";
    public static final String RESEARCH_QUESTION = "RESEARCH_QUESTION";
    public static final String STAT_TEST = "STAT_TEST";
    public static final String AUTOML_TRAIN = "AUTOML_TRAIN";
    public static final String XAI_EXPLAIN = "XAI_EXPLAIN";
    public static final String FAIRNESS = "FAIRNESS";
    public static final String WHATIF = "WHATIF";
    public static final String DRIFT = "DRIFT";
    public static final String FINDING = "FINDING";
    public static final String REPORT = "REPORT";
    public static final String ASK = "ASK";
    public static final String CLEAN = "CLEAN";
}
