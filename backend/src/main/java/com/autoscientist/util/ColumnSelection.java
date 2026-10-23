package com.autoscientist.util;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.DatasetColumn;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Single place that decides which columns take part in analysis.
 *
 *  - {@link #active(Dataset)}: the dataset's columns minus any the user excluded
 *    in the import wizard (the uploaded file itself is never modified).
 *  - {@link #analytic(Collection)}: active columns minus IDENTIFIER-typed columns,
 *    i.e. the variables that may enter statistics, correlations, charts and AutoML
 *    by default. Users can still reference an identifier explicitly where the API
 *    allows it, but nothing selects one implicitly.
 */
public final class ColumnSelection {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> IDENTIFIER_TYPES = Set.of("IDENTIFIER");

    private ColumnSelection() {}

    public static Set<String> excludedNames(Dataset dataset) {
        if (dataset == null || dataset.getExcludedColumnsJson() == null
                || dataset.getExcludedColumnsJson().isBlank()) {
            return Collections.emptySet();
        }
        try {
            List<String> list = MAPPER.readValue(dataset.getExcludedColumnsJson(),
                    new TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /** All columns the user has not excluded. */
    public static List<DatasetColumn> active(Dataset dataset) {
        if (dataset == null || dataset.getColumns() == null) return Collections.emptyList();
        Set<String> excluded = excludedNames(dataset);
        if (excluded.isEmpty()) return new ArrayList<>(dataset.getColumns());
        List<DatasetColumn> out = new ArrayList<>();
        for (DatasetColumn c : dataset.getColumns()) {
            if (!excluded.contains(c.getColumnName())) out.add(c);
        }
        return out;
    }

    /** Active columns that are not identifiers — the default set for analysis. */
    public static List<DatasetColumn> analytic(Dataset dataset) {
        return analytic(active(dataset));
    }

    public static List<DatasetColumn> analytic(Collection<DatasetColumn> columns) {
        List<DatasetColumn> out = new ArrayList<>();
        if (columns == null) return out;
        for (DatasetColumn c : columns) {
            if (!IDENTIFIER_TYPES.contains(c.getDataType())) out.add(c);
        }
        return out;
    }

    public static List<DatasetColumn> numericAnalytic(Collection<DatasetColumn> columns) {
        List<DatasetColumn> out = new ArrayList<>();
        if (columns == null) return out;
        for (DatasetColumn c : columns) {
            if ("NUMERIC".equals(c.getDataType())) out.add(c);
        }
        return out;
    }

    public static boolean isIdentifier(DatasetColumn column) {
        return column != null && IDENTIFIER_TYPES.contains(column.getDataType());
    }
}
