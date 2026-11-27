package com.autoscientist.analysis.ml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelArtifactCodecTest {

    @Test
    void currentVersionArtifactRoundTrips() throws Exception {
        ServingArtifact decoded = ModelArtifactCodec.deserialize(ModelArtifactCodec.serialize(artifact()));

        assertEquals("outcome", decoded.getTargetColumn());
        assertEquals("REGRESSION", decoded.getProblemType());
        assertEquals(List.of("age", "visits"), decoded.getFeatureNames());
        assertArrayEquals(new double[]{1.5, -0.5}, decoded.getModel().getWeights());
    }

    @Test
    void legacyArtifactWithoutVersionHeaderIsStillReadable() throws Exception {
        byte[] legacyPayload = legacySerialize(artifact());

        ServingArtifact decoded = ModelArtifactCodec.deserialize(legacyPayload);

        assertEquals("outcome", decoded.getTargetColumn());
        assertArrayEquals(new double[]{1.5, -0.5}, decoded.getModel().getWeights());
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws Exception {
        byte[] payload = ModelArtifactCodec.serialize(artifact());
        byte[] unsupported = ByteBuffer.allocate(Integer.BYTES)
                .putInt(ModelArtifactCodec.SCHEMA_VERSION + 1)
                .array();
        System.arraycopy(unsupported, 0, payload, ModelArtifactCodec.HEADER_MAGIC.length, Integer.BYTES);

        IOException failure = assertThrows(IOException.class, () -> ModelArtifactCodec.deserialize(payload));
        assertTrue(failure.getMessage().contains("Unsupported model artifact schema version"));
    }

    @Test
    void oversizedCompressedPayloadIsRejectedBeforeFullDeserialization() throws Exception {
        byte[] payload = legacySerialize("x".repeat(4096));

        IOException failure = assertThrows(IOException.class,
                () -> ModelArtifactCodec.deserialize(payload, 1024));
        assertTrue(failure.getMessage().contains("decompressed size"));
    }

    @Test
    void malformedPayloadsAreRejected() throws Exception {
        assertThrows(IOException.class, () -> ModelArtifactCodec.deserialize(null));
        assertThrows(IOException.class, () -> ModelArtifactCodec.deserialize(new byte[0]));
        assertThrows(IOException.class, () -> ModelArtifactCodec.deserialize(new byte[32]));
        assertThrows(IOException.class,
                () -> ModelArtifactCodec.deserialize(new byte[]{0x1f, (byte) 0x8b}));
        assertThrows(IOException.class, () -> ModelArtifactCodec.deserialize(legacySerialize("wrong type")));
    }

    private static ServingArtifact artifact() {
        return new ServingArtifact(
                new TestModel(new double[]{1.5, -0.5}),
                List.of("age", "visits"),
                Map.of("scalerMean", List.of(30.0, 2.0), "scalerStd", List.of(10.0, 1.0)),
                Map.of(0, "no", 1, "yes"),
                "outcome",
                "REGRESSION",
                "TEST_LINEAR");
    }

    private static byte[] legacySerialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             ObjectOutputStream out = new ObjectOutputStream(gzip)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static final class TestModel implements AutoMlEngine.PredictiveModel {

        private static final long serialVersionUID = 1L;

        private final double[] weights;

        private TestModel(double[] weights) {
            this.weights = weights;
        }

        @Override
        public double predictSingle(double[] x) {
            double sum = 0.0;
            for (int i = 0; i < Math.min(weights.length, x.length); i++) {
                sum += weights[i] * x[i];
            }
            return sum;
        }

        @Override
        public double[] predict(double[][] rows) {
            return Arrays.stream(rows).mapToDouble(this::predictSingle).toArray();
        }

        @Override
        public double[] getWeights() {
            return weights;
        }
    }
}
