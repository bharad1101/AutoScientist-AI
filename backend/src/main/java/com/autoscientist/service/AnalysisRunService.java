package com.autoscientist.service;

import com.autoscientist.entity.AnalysisRun;
import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.User;
import com.autoscientist.repository.AnalysisRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Records which analyses were actually executed against a dataset.
 * All writes are idempotent upserts keyed on (dataset, operation, params hash).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisRunService {

    private final AnalysisRunRepository analysisRunRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<String, Object> NO_PARAMS = Collections.emptyMap();

    /**
     * Record (or refresh) an executed analysis.
     *
     * <p>Writes are deferred until the caller's transaction commits. That matters for
     * provenance recorded against a dataset created in the same transaction: a separate
     * (REQUIRES_NEW) transaction cannot see the uncommitted dataset row, which used to
     * fail the foreign key and mark the caller's transaction rollback-only. Deferring
     * also keeps a provenance failure from ever poisoning the analysis transaction, and
     * a rolled-back analysis leaves no provenance behind (nothing observable happened).
     */
    public void record(Dataset dataset, String operation, Map<String, Object> params,
                       String summary, Object payload, Long durationMs, User user) {
        try {
            final Dataset target = dataset;
            final Long datasetId = dataset.getId();
            final String paramsHash = hashParams(operation, params);
            final String summaryText = truncate(summary, 4000);
            final String payloadJson = serializePayload(payload);
            final Long duration = durationMs;
            final User runUser = user;

            Runnable write = () -> runInNewTransaction(
                    () -> upsert(target, operation, paramsHash, summaryText, payloadJson, duration, runUser));

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // Swallow everything: the analysis already committed successfully.
                        try {
                            write.run();
                        } catch (Exception e) {
                            log.warn("Failed to record analysis run {} for dataset {}: {}",
                                    operation, datasetId, e.getMessage());
                        }
                    }
                });
            } else {
                write.run();
            }
        } catch (Exception e) {
            // Provenance must never break the analysis itself.
            log.warn("Failed to record analysis run {} for dataset {}: {}", operation, dataset.getId(), e.getMessage());
        }
    }

    private void runInNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            try {
                work.run();
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    private void upsert(Dataset dataset, String operation, String paramsHash, String summary,
                        String payloadJson, Long durationMs, User user) {
        if (dataset == null || dataset.getId() == null) return;
        AnalysisRun run = analysisRunRepository
                .findByDatasetIdAndOperationAndParamsHash(dataset.getId(), operation, paramsHash)
                .orElse(null);

        if (run == null) {
            run = AnalysisRun.builder()
                    .dataset(dataset)
                    .user(user)
                    .operation(operation)
                    .paramsHash(paramsHash)
                    .summary(summary)
                    .payloadJson(payloadJson)
                    .status("COMPLETED")
                    .durationMs(durationMs)
                    .runCount(1L)
                    .build();
        } else {
            run.setSummary(summary);
            if (payloadJson != null) run.setPayloadJson(payloadJson);
            run.setDurationMs(durationMs);
            run.setStatus("COMPLETED");
            run.setRunCount((run.getRunCount() == null ? 0L : run.getRunCount()) + 1);
            if (run.getUser() == null && user != null) run.setUser(user);
        }
        analysisRunRepository.save(run);
    }

    private String serializePayload(Object payload) {
        if (payload == null) return null;
        try {
            String json = objectMapper.writeValueAsString(payload);
            // Bound the stored payload: provenance needs a summary, not an unbounded table dump.
            return json.length() > 200_000 ? json.substring(0, 200_000) : json;
        } catch (Exception e) {
            return null;
        }
    }

    public List<AnalysisRun> runsFor(Dataset dataset) {
        return analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(dataset.getId());
    }

    public boolean hasRun(Long datasetId, String operation) {
        return analysisRunRepository.existsByDatasetIdAndOperation(datasetId, operation);
    }

    public Optional<AnalysisRun> latest(Dataset dataset, String operation) {
        return analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(dataset.getId()).stream()
                .filter(r -> operation.equals(r.getOperation()))
                .findFirst();
    }

    public Map<String, AnalysisRun> latestByOperation(Dataset dataset) {
        Map<String, AnalysisRun> map = new HashMap<>();
        for (AnalysisRun run : analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(dataset.getId())) {
            map.putIfAbsent(run.getOperation(), run);
        }
        return map;
    }

    public List<Map<String, Object>> history(Dataset dataset) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AnalysisRun run : analysisRunRepository.findByDatasetIdOrderByUpdatedAtDesc(dataset.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", run.getId());
            m.put("operation", run.getOperation());
            m.put("summary", run.getSummary());
            m.put("status", run.getStatus());
            m.put("durationMs", run.getDurationMs());
            m.put("runCount", run.getRunCount());
            m.put("createdAt", run.getCreatedAt());
            m.put("updatedAt", run.getUpdatedAt());
            out.add(m);
        }
        return out;
    }

    private String hashParams(String operation, Map<String, Object> params) {
        String canonical = operation + "|" + (params == null ? "" : new TreeMap<>(params).toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(canonical.hashCode());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
