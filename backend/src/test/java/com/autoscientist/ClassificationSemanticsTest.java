package com.autoscientist;

import com.autoscientist.analysis.ml.ClassificationSemantics;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationSemanticsTest {

    @Test
    void binaryPositiveClassIsEncodedClassOneWithItsActualLabel() {
        Map<Integer, String> reverse = Map.of(0, "Approve", 1, "Deny");

        assertTrue(ClassificationSemantics.isBinaryClassification("CLASSIFICATION", reverse));
        assertEquals(1, ClassificationSemantics.positiveClassIndex("CLASSIFICATION", reverse));
        assertEquals("Deny", ClassificationSemantics.positiveClassLabel("CLASSIFICATION", reverse));
    }

    @Test
    void multiclassRegressionAndUnknownMapsDisableProbabilityMetadata() {
        assertNull(ClassificationSemantics.positiveClassIndex("CLASSIFICATION", Map.of(0, "A", 1, "B", 2, "C")));
        assertNull(ClassificationSemantics.positiveClassLabel("CLASSIFICATION", Map.of(0, "A", 1, "B", 2, "C")));
        assertNull(ClassificationSemantics.positiveClassIndex("CLASSIFICATION", Map.of(0, "No")));
        assertNull(ClassificationSemantics.positiveClassIndex("REGRESSION", Map.of(0, "No", 1, "Yes")));
        assertNull(ClassificationSemantics.positiveClassIndex("CLASSIFICATION", null));
    }

    @Test
    void metadataIdentifiesProbabilityClassAndDecisionThreshold() {
        Map<String, Object> metadata = ClassificationSemantics.metadata(
                "CLASSIFICATION", Map.of(0, "Negative", 1, "Positive"));

        assertEquals(Boolean.TRUE, metadata.get("binaryClassification"));
        assertEquals(1, metadata.get("positiveClassIndex"));
        assertEquals("Positive", metadata.get("positiveClassLabel"));
        assertEquals(1, metadata.get("probabilityClassIndex"));
        assertEquals("Positive", metadata.get("probabilityClassLabel"));
        assertEquals("AVAILABLE", metadata.get("probabilityStatus"));
        assertEquals(0.5, ((Number) metadata.get("decisionThreshold")).doubleValue(), 1e-9);

        Map<String, Object> multiclass = ClassificationSemantics.metadata(
                "CLASSIFICATION", Map.of(0, "A", 1, "B", 2, "C"));
        assertEquals(Boolean.FALSE, multiclass.get("binaryClassification"));
        assertEquals("NOT_BINARY", multiclass.get("probabilityStatus"));
        assertFalse(multiclass.containsKey("unexpected"));
    }

    @Test
    void scoreValidationAcceptsOnlyFiniteUnitIntervalValues() {
        assertTrue(ClassificationSemantics.isUsableScore(0.0));
        assertTrue(ClassificationSemantics.isUsableScore(0.5));
        assertTrue(ClassificationSemantics.isUsableScore(1.0));
        assertFalse(ClassificationSemantics.isUsableScore(-0.01));
        assertFalse(ClassificationSemantics.isUsableScore(1.01));
        assertFalse(ClassificationSemantics.isUsableScore(Double.NaN));
        assertFalse(ClassificationSemantics.isUsableScore(Double.POSITIVE_INFINITY));
    }
}
