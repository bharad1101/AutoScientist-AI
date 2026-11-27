package com.autoscientist.analysis.ml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes trained models into the database BLOB format. Payloads are gzip
 * compressed and deserialization is restricted to this application's classes
 * plus the JDK collection types needed by the fitted model.
 */
public final class ModelArtifactCodec {

    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_DECOMPRESSED_BYTES = 64L * 1024 * 1024;

    static final byte[] HEADER_MAGIC = {'A', 'S', 'C', 'M', 'A', 'R', 'T'};

    private static final byte[] GZIP_MAGIC = {0x1f, (byte) 0x8b};
    private static final String TOO_LARGE = "Model artifact decompressed size exceeds the configured limit";

    private static final ObjectInputFilter INPUT_FILTER = info -> {
        if (info.depth() > 200 || info.references() > 5_000_000 || info.arrayLength() > 5_000_000) {
            return ObjectInputFilter.Status.REJECTED;
        }
        Class<?> type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        String name = type.getName();
        if (name.startsWith("com.autoscientist.")
                || name.startsWith("java.lang.")
                || name.startsWith("java.util.")) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    };

    private ModelArtifactCodec() {
    }

    public static byte[] serialize(ServingArtifact artifact) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(HEADER_MAGIC);
        bytes.write(ByteBuffer.allocate(Integer.BYTES).putInt(SCHEMA_VERSION).array());
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             ObjectOutputStream out = new ObjectOutputStream(gzip)) {
            out.writeObject(artifact);
        }
        return bytes.toByteArray();
    }

    public static ServingArtifact deserialize(byte[] payload) throws IOException, ClassNotFoundException {
        return deserialize(payload, MAX_DECOMPRESSED_BYTES);
    }

    static ServingArtifact deserialize(byte[] payload, long maxDecompressedBytes)
            throws IOException, ClassNotFoundException {
        if (payload == null || payload.length == 0) {
            throw new IOException("Model artifact payload is empty");
        }
        if (maxDecompressedBytes <= 0) {
            throw new IllegalArgumentException("Decompressed-byte limit must be positive");
        }

        PushbackInputStream input = new PushbackInputStream(
                new ByteArrayInputStream(payload), HEADER_MAGIC.length + Integer.BYTES);
        byte[] prefix = new byte[GZIP_MAGIC.length];
        int prefixLength = input.read(prefix);
        if (prefixLength < GZIP_MAGIC.length) {
            throw new IOException("Model artifact payload is truncated");
        }
        input.unread(prefix);

        boolean legacy = prefix[0] == GZIP_MAGIC[0] && prefix[1] == GZIP_MAGIC[1];
        if (!legacy) {
            byte[] header = new byte[HEADER_MAGIC.length + Integer.BYTES];
            readFully(input, header);
            for (int i = 0; i < HEADER_MAGIC.length; i++) {
                if (header[i] != HEADER_MAGIC[i]) {
                    throw new IOException("Unrecognized model artifact header");
                }
            }
            int version = ByteBuffer.wrap(header, HEADER_MAGIC.length, Integer.BYTES).getInt();
            if (version != SCHEMA_VERSION) {
                throw new IOException("Unsupported model artifact schema version: " + version);
            }
        }

        try (InputStream bytes = input;
             GZIPInputStream gzip = new GZIPInputStream(bytes);
             LimitedInputStream limited = new LimitedInputStream(gzip, maxDecompressedBytes);
             ObjectInputStream in = new ObjectInputStream(limited)) {
            in.setObjectInputFilter(INPUT_FILTER);
            Object value = in.readObject();
            if (!(value instanceof ServingArtifact artifact)) {
                throw new IOException("Unexpected model artifact type");
            }
            try {
                if (in.read() != -1) {
                    throw new IOException("Unexpected trailing data in model artifact");
                }
            } catch (OptionalDataException e) {
                throw new IOException("Unexpected trailing data in model artifact", e);
            }
            drainFully(limited);
            return artifact;
        }
    }

    private static void readFully(InputStream in, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = in.read(target, offset, target.length - offset);
            if (count < 0) {
                throw new IOException("Model artifact payload is truncated");
            }
            offset += count;
        }
    }

    private static void drainFully(InputStream in) throws IOException {
        byte[] scratch = new byte[8192];
        while (in.read(scratch) != -1) {
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long consumed;

        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (consumed >= limit) {
                if (super.read() == -1) {
                    return -1;
                }
                throw new IOException(TOO_LARGE);
            }
            int value = super.read();
            if (value != -1) {
                consumed++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) {
                return 0;
            }
            if (consumed >= limit) {
                if (super.read() == -1) {
                    return -1;
                }
                throw new IOException(TOO_LARGE);
            }
            int allowed = (int) Math.min((long) length, limit - consumed);
            int count = super.read(buffer, offset, allowed);
            if (count > 0) {
                consumed += count;
            }
            return count;
        }
    }
}
