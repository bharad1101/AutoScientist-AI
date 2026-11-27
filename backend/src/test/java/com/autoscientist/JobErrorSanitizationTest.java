package com.autoscientist;

import com.autoscientist.config.ResourceBudgetProperties;
import com.autoscientist.entity.JobInfo;
import com.autoscientist.exception.SafeErrorMessages;
import com.autoscientist.repository.JobInfoRepository;
import com.autoscientist.service.JobExecutorService;
import com.autoscientist.service.StorageProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobErrorSanitizationTest {

    @Test
    void asyncJobFailureIsNotStoredOrReturnedWithSensitiveDetails() throws Exception {
        JobInfo job = new JobInfo();
        job.setId(UUID.randomUUID());
        job.setType(JobInfo.JobType.EDA);
        job.setStatus(JobInfo.JobStatus.PENDING);

        JobInfoRepository repository = mock(JobInfoRepository.class);
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(repository.save(any(JobInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutorService service = new JobExecutorService(
                new ResourceBudgetProperties(), repository, mock(StorageProvider.class), new SimpleMeterRegistry());

        Method runJob = JobExecutorService.class.getDeclaredMethod("runJob", UUID.class, Runnable.class);
        runJob.setAccessible(true);
        runJob.invoke(service, job.getId(),
                (Runnable) () -> {
                    throw new IllegalStateException("SQLSTATE 28000 from the database driver");
                });

        assertThat(job.getStatus()).isEqualTo(JobInfo.JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo(SafeErrorMessages.GENERIC);
        assertThat(job.getErrorMessage()).doesNotContain("SQLSTATE");
    }
}
