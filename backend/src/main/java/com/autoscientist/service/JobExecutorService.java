package com.autoscientist.service;

import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.entity.JobInfo;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.exception.ResourceLimitExceededException;
import com.autoscientist.exception.SafeErrorMessages;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.JobInfoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded asynchronous job executor. Work is supplied by the controller as a
 * concrete task (EDA, associations or AutoML); there is no silent no-op job.
 *
 * <p>Cancellation is best-effort: queued jobs are prevented from starting and
 * running jobs receive an interrupt, but a task that ignores interrupts may
 * still finish. Its result is never recorded as COMPLETED after cancellation.</p>
 */
@Service
@Slf4j
public class JobExecutorService {

    private final ResourceBudgetProperties budgetProperties;
    private final JobInfoRepository jobInfoRepository;
    private final StorageProvider storageProvider;
    private final MeterRegistry meterRegistry;
    private final Map<UUID, Future<?>> running = new ConcurrentHashMap<>();
    private ThreadPoolExecutor executor;

    @Autowired
    public JobExecutorService(ResourceBudgetProperties budgetProperties,
                              JobInfoRepository jobInfoRepository,
                              StorageProvider storageProvider,
                              MeterRegistry meterRegistry) {
        this.budgetProperties = budgetProperties;
        this.jobInfoRepository = jobInfoRepository;
        this.storageProvider = storageProvider;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    private void initExecutor() {
        int concurrency = budgetProperties.getJobConcurrency();
        this.executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(concurrency * 2),
                new ThreadPoolExecutor.AbortPolicy()
        );
        meterRegistry.gauge("autoscientist.jobs.tracked", running, Map::size);
        meterRegistry.gauge("autoscientist.jobs.queued", executor, e -> e.getQueue().size());
        meterRegistry.gauge("autoscientist.jobs.active", executor, ThreadPoolExecutor::getActiveCount);
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Submit a job for asynchronous execution. */
    public JobInfo submitJob(JobInfo.JobType type, User owner, Long datasetId,
                             String targetColumn, String problemType,
                             Runnable work, UUID transientFileId) {
        JobInfo job = new JobInfo();
        job.setType(type);
        job.setOwner(owner);
        job.setDatasetId(datasetId);
        job.setTargetColumn(targetColumn);
        job.setProblemType(problemType);
        job.setStatus(JobInfo.JobStatus.PENDING);
        job.setTransientFilePath(transientFileId != null ? transientFileId.toString() : null);
        jobInfoRepository.save(job);

        UUID jobId = job.getId();
        FutureTask<Void> task = new FutureTask<>(() -> {
            runJob(jobId, work);
            return null;
        }) {
            @Override
            protected void done() {
                running.remove(jobId);
            }
        };
        running.put(jobId, task);
        try {
            executor.execute(task);
            meterRegistry.counter("autoscientist.jobs.submitted", "type", type.name()).increment();
            log.info("Job {} submitted (type={}, datasetId={})", jobId, type, datasetId);
        } catch (RejectedExecutionException e) {
            running.remove(jobId);
            job.setStatus(JobInfo.JobStatus.FAILED);
            job.setErrorMessage("Job queue full – try again later");
            job.setCompletedAt(OffsetDateTime.now());
            jobInfoRepository.save(job);
            meterRegistry.counter("autoscientist.jobs.rejected").increment();
            log.warn("Job {} rejected: bounded executor queue is full", jobId);
            throw new ResourceLimitExceededException("Job concurrency limit exceeded");
        }
        return job;
    }

    private void runJob(UUID jobId, Runnable work) {
        Optional<JobInfo> opt = jobInfoRepository.findById(jobId);
        if (opt.isEmpty()) {
            return;
        }
        JobInfo job = opt.get();
        if (job.getStatus() == JobInfo.JobStatus.CANCELED) {
            return;
        }
        job.setStatus(JobInfo.JobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        jobInfoRepository.save(job);
        log.info("Job {} running (type={})", jobId, job.getType());

        String failure = null;
        try {
            work.run();
        } catch (RuntimeException ex) {
            failure = SafeErrorMessages.forClient(
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            if (isInterrupted(ex)) {
                Thread.currentThread().interrupt();
            }
        }

        JobInfo latest = jobInfoRepository.findById(jobId).orElse(job);
        if (latest.getStatus() == JobInfo.JobStatus.CANCELED) {
            return;
        }
        if (failure == null) {
            latest.setStatus(JobInfo.JobStatus.COMPLETED);
            latest.setProgress(100);
            meterRegistry.counter("autoscientist.jobs.completed", "type", latest.getType().name()).increment();
            log.info("Job {} completed", jobId);
        } else {
            latest.setStatus(JobInfo.JobStatus.FAILED);
            latest.setErrorMessage(failure);
            meterRegistry.counter("autoscientist.jobs.failed", "type", latest.getType().name()).increment();
            log.warn("Job {} failed: {}", jobId, SafeErrorMessages.forLog(failure));
        }
        latest.setCompletedAt(OffsetDateTime.now());
        jobInfoRepository.save(latest);
    }

    private static boolean isInterrupted(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** List the caller's jobs, optionally filtered by status and type. */
    public List<JobInfo> listJobs(User owner, JobInfo.JobStatus[] statuses, JobInfo.JobType[] types) {
        boolean hasStatuses = statuses != null && statuses.length > 0;
        boolean hasTypes = types != null && types.length > 0;
        if (hasStatuses && hasTypes) {
            return jobInfoRepository.findByOwnerAndStatusInAndTypeInOrderByCreatedAtDesc(
                    owner, Arrays.asList(statuses), Arrays.asList(types));
        }
        if (hasStatuses) {
            return jobInfoRepository.findByOwnerAndStatusInOrderByCreatedAtDesc(owner, Arrays.asList(statuses));
        }
        if (hasTypes) {
            return jobInfoRepository.findByOwnerAndTypeInOrderByCreatedAtDesc(owner, Arrays.asList(types));
        }
        return jobInfoRepository.findByOwnerOrderByCreatedAtDesc(owner);
    }

    /** Retrieve one of the caller's jobs. */
    public Optional<JobInfo> getJob(UUID id, User owner) {
        return jobInfoRepository.findByIdAndOwner(id, owner);
    }

    /** Cancel a queued or running job owned by the caller. */
    public JobInfo cancelJob(UUID id, User owner) {
        JobInfo job = jobInfoRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
        if (job.getStatus() == JobInfo.JobStatus.COMPLETED
                || job.getStatus() == JobInfo.JobStatus.FAILED
                || job.getStatus() == JobInfo.JobStatus.CANCELED) {
            throw new BadRequestException("Job is already finished and cannot be canceled.");
        }
        job.setStatus(JobInfo.JobStatus.CANCELED);
        job.setErrorMessage("Canceled by user.");
        job.setCompletedAt(OffsetDateTime.now());
        JobInfo saved = jobInfoRepository.save(job);
        Future<?> future = running.get(id);
        if (future != null) {
            future.cancel(true);
        }
        meterRegistry.counter("autoscientist.jobs.canceled").increment();
        log.info("Job {} canceled by owner", id);
        return saved;
    }

    /** Hourly cleanup of terminal jobs older than the configured TTL. */
    @Scheduled(cron = "${app.jobs.cleanup-cron:0 0 * * * *}")
    public void ttlCleanup() {
        int ttlHours = budgetProperties.getTempFileTtlHours();
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(ttlHours);
        for (JobInfo job : jobInfoRepository.findAll()) {
            boolean terminal = job.getStatus() == JobInfo.JobStatus.COMPLETED
                    || job.getStatus() == JobInfo.JobStatus.FAILED
                    || job.getStatus() == JobInfo.JobStatus.CANCELED;
            if (!terminal || job.getCompletedAt() == null || !job.getCompletedAt().isBefore(cutoff)) {
                continue;
            }
            if (job.getTransientFilePath() != null) {
                try {
                    UUID fileId = UUID.fromString(job.getTransientFilePath());
                    boolean stillReferenced = jobInfoRepository.findAll().stream()
                            .anyMatch(other -> other != job && other.getTransientFilePath() != null
                                    && other.getTransientFilePath().equals(fileId.toString())
                                    && (other.getStatus() == JobInfo.JobStatus.PENDING
                                    || other.getStatus() == JobInfo.JobStatus.RUNNING));
                    if (!stillReferenced) {
                        storageProvider.deleteFile(fileId);
                    }
                } catch (Exception ignored) {
                    // Cleanup must never break the scheduled task.
                }
            }
            jobInfoRepository.delete(job);
        }
    }

    /**
     * Jobs are in-memory tasks, so their closures cannot survive a restart.
     * Mark interrupted jobs honestly instead of re-running an empty placeholder.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void recoverJobs() {
        for (JobInfo job : jobInfoRepository.findAll()) {
            if (job.getStatus() == JobInfo.JobStatus.PENDING || job.getStatus() == JobInfo.JobStatus.RUNNING) {
                job.setStatus(JobInfo.JobStatus.FAILED);
                job.setErrorMessage("Job was interrupted by a service restart; please resubmit.");
                job.setCompletedAt(OffsetDateTime.now());
                jobInfoRepository.save(job);
            }
        }
    }
}
