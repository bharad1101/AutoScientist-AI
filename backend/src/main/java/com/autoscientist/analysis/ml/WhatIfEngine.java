package com.autoscientist.analysis.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes RAW what-if scenarios into the model's feature space.
 *
 * <p>Training applies median/mode imputation, one-hot expansion and standard
 * scaling. A scenario must be encoded with exactly the same transformation,
 * otherwise a user typed value (e.g. income = 50000) would be fed to the model
 * as if it were already a standardized score. The transformation is recorded in
 * {@code DatasetSplit.getPreprocessingConfig()} and replayed here.</p>
 */
public class WhatIfEngine {

    /** A raw (untransformed) column of the training matrix. */
    public static class RawFeature {
        public String name;
        public String kind;                 // NUMERIC | CATEGORICAL
        public Object imputeValue;          // median (numeric) or mode (categorical)
        public Double min;                  // numeric only
        public Double max;                  // numeric only
        public List<String> categories = new ArrayList<>();
        public List<String> transformedNames = new ArrayList<>();
    }

    /** Result of encoding a scenario. */
    public static class Encoded {
        public double[] vector = new double[0];
        /** Raw values actually applied, in schema order. */
        public Map<String, Object> appliedValues = new LinkedHashMap<>();
        /** Human readable notes about values that had to be adjusted. */
        public List<String> warnings = new ArrayList<>();
        /** Raw feature names supplied by the caller that are not part of the model. */
        public List<String> unrecognised = new ArrayList<>();
    }

    /** Reads the raw feature schema recorded at training time. */
    @SuppressWarnings("unchecked")
    public static List<RawFeature> readSchema(Map<String, Object> preprocConfig) {
        List<RawFeature> out = new ArrayList<>();
        if (preprocConfig == null) return out;
        Object raw = preprocConfig.get("rawFeatures");
        if (!(raw instanceof List)) return out;
        for (Object item : (List<Object>) raw) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) item;
            RawFeature f = new RawFeature();
            f.name = str(m.get("name"));
            f.kind = str(m.get("kind"));
            f.imputeValue = m.get("imputeValue");
            Object min = m.get("min");
            Object max = m.get("max");
            f.min = (min instanceof Number) ? ((Number) min).doubleValue() : null;
            f.max = (max instanceof Number) ? ((Number) max).doubleValue() : null;
            Object cats = m.get("categories");
            if (cats instanceof List) {
                for (Object c : (List<Object>) cats) {
                    if (c != null) f.categories.add(String.valueOf(c));
                }
            }
            Object tn = m.get("transformedNames");
            if (tn instanceof List) {
                for (Object c : (List<Object>) tn) {
                    if (c != null) f.transformedNames.add(String.valueOf(c));
                }
            }
            if (f.name != null) out.add(f);
        }
        return out;
    }

    /**
     * Encodes a scenario.
     *
     * @param schema   raw feature schema recorded at training time
     * @param transformedNames transformed model feature names, in matrix order
     * @param mean     per-feature scaler means, aligned with {@code transformedNames}
     * @param std      per-feature scaler standard deviations, aligned with {@code transformedNames}
     * @param inputs   raw user inputs keyed by RAW column name
     */
    public static Encoded encode(List<RawFeature> schema,
                                 List<String> transformedNames,
                                 List<Double> mean,
                                 List<Double> std,
                                 Map<String, Object> inputs) {
        Encoded result = new Encoded();
        result.vector = new double[transformedNames.size()];

        // 1. Resolve the raw value for every feature (user input, else reference value).
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (RawFeature f : schema) {
            Object value = null;
            boolean provided = inputs != null && inputs.containsKey(f.name);
            if (provided) {
                value = inputs.get(f.name);
                if (value == null || String.valueOf(value).trim().isEmpty()) {
                    provided = false;
                    value = null;
                }
            }
            if (!provided) {
                value = f.imputeValue;
                resolved.put(f.name, value);
                continue;
            }
            if ("NUMERIC".equals(f.kind)) {
                Double parsed = toDouble(value);
                if (parsed == null) {
                    result.warnings.add("'" + f.name + "' was not numeric; the training median was used instead.");
                    value = f.imputeValue;
                } else {
                    if (f.min != null && parsed < f.min) {
                        result.warnings.add("'" + f.name + "' (" + trim(parsed) + ") is below the training minimum ("
                                + trim(f.min) + "); the model is extrapolating.");
                    } else if (f.max != null && parsed > f.max) {
                        result.warnings.add("'" + f.name + "' (" + trim(parsed) + ") is above the training maximum ("
                                + trim(f.max) + "); the model is extrapolating.");
                    }
                    value = parsed;
                }
            } else {
                value = String.valueOf(value).trim();
                if (!f.categories.isEmpty() && !f.categories.contains(value)) {
                    result.warnings.add("'" + f.name + "' value '" + value
                            + "' was not observed during training; it is encoded as 'none of the known categories'.");
                }
            }
            resolved.put(f.name, value);
        }
        result.appliedValues.putAll(resolved);

        // 2. Expand to the transformed feature space (one-hot for categoricals).
        Map<String, Double> byTransformed = new LinkedHashMap<>();
        for (RawFeature f : schema) {
            Object value = resolved.get(f.name);
            if ("NUMERIC".equals(f.kind)) {
                Double d = toDouble(value);
                for (String tn : f.transformedNames) {
                    byTransformed.put(tn, d == null ? 0.0 : d);
                }
            } else {
                String cat = value == null ? null : String.valueOf(value);
                for (String tn : f.transformedNames) {
                    String suffix = tn.substring(f.name.length() + 1);
                    byTransformed.put(tn, (cat != null && cat.equals(suffix)) ? 1.0 : 0.0);
                }
            }
        }

        // 3. Standardise with the training scaler.
        for (int j = 0; j < transformedNames.size(); j++) {
            String tn = transformedNames.get(j);
            double raw = byTransformed.getOrDefault(tn, 0.0);
            double m = (mean != null && j < mean.size() && mean.get(j) != null) ? mean.get(j) : 0.0;
            double s = (std != null && j < std.size() && std.get(j) != null) ? std.get(j) : 1.0;
            if (s == 0.0) s = 1.0;
            result.vector[j] = (raw - m) / s;
        }

        // 4. Report inputs that the model does not know about.
        if (inputs != null) {
            List<String> known = new ArrayList<>();
            for (RawFeature f : schema) known.add(f.name);
            for (String key : inputs.keySet()) {
                if (!known.contains(key)) result.unrecognised.add(key);
            }
        }
        return result;
    }

    private static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String trim(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 10000.0) / 10000.0);
    }

    /** Convenience helper for building an empty input map. */
    public static Map<String, Object> emptyInputs() {
        return Collections.emptyMap();
    }
}
