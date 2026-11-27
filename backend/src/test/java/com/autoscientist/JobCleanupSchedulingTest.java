package com.autoscientist;

import com.autoscientist.entity.JobInfo;
import com.autoscientist.entity.User;
import com.autoscientist.repository.JobInfoRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.StorageProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job_cleanup_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jobs.cleanup-cron=*/1 * * * * *",
        "spring.task.scheduling.pool.size=1"
})
@ActiveProfiles("test")
class JobCleanupSchedulingTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JobInfoRepository jobInfoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StorageProvider storageProvider;

    private final List<UUID> createdJobIds = new ArrayList<>();
    private final List<UUID> createdFileIds = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        jobInfoRepository.deleteAllById(createdJobIds);
        for (UUID fileId : createdFileIds) {
            storageProvider.deleteFile(fileId);
        }
    }

    @Test
    void springSchedulingIsEnabled() {
        assertFalse(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
    }

    @Test
    void scheduledCleanupRemovesOnlyExpiredTerminalJobs() throws Exception {
        User owner = userRepository.save(User.builder()
                .name("cleanup-owner")
                .email("cleanup-" + UUID.randomUUID() + "@example.test")
                .passwordHash("$2a$10$test.placeholder.hash.not.used.for.login")
                .role("ROLE_USER")
                .build());

        UUID expiredFileId = saveTempFile("expired");
        UUID activeFileId = saveTempFile("active");
        OffsetDateTime now = OffsetDateTime.now();

        JobInfo expired = saveJob(owner, JobInfo.JobStatus.COMPLETED,
                now.minusHours(25), expiredFileId.toString());
        JobInfo recent = saveJob(owner, JobInfo.JobStatus.COMPLETED,
                now.minusHours(1), null);
        JobInfo oldRunning = saveJob(owner, JobInfo.JobStatus.RUNNING,
                now.minusHours(72), activeFileId.toString());
        JobInfo oldPending = saveJob(owner, JobInfo.JobStatus.PENDING,
                now.minusHours(72), null);

        long deadline = System.currentTimeMillis() + 10_000L;
        while (jobInfoRepository.findById(expired.getId()).isPresent()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
        }

        assertTrue(jobInfoRepository.findById(expired.getId()).isEmpty(),
                "Scheduled cleanup should have deleted the expired terminal job");
        assertTrue(jobInfoRepository.findById(recent.getId()).isPresent(),
                "Recent terminal jobs must not be deleted");
        assertTrue(jobInfoRepository.findById(oldRunning.getId()).isPresent(),
                "Active RUNNING jobs must not be deleted");
        assertTrue(jobInfoRepository.findById(oldPending.getId()).isPresent(),
                "Active PENDING jobs must not be deleted");
        assertTrue(Files.notExists(storageProvider.getFile(expiredFileId)),
                "Expired jobs' transient files should be deleted");
        assertTrue(Files.exists(storageProvider.getFile(activeFileId)),
                "Transient files belonging to active jobs must be retained");
    }

    private UUID saveTempFile(String content) throws Exception {
        UUID id = storageProvider.saveTempFile(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        createdFileIds.add(id);
        return id;
    }

    private JobInfo saveJob(User owner, JobInfo.JobStatus status,
                            OffsetDateTime completedAt, String transientFilePath) {
        JobInfo job = new JobInfo();
        job.setType(JobInfo.JobType.OTHER);
        job.setOwner(owner);
        job.setStatus(status);
        job.setCreatedAt(OffsetDateTime.now().minusHours(72));
        job.setCompletedAt(completedAt);
        job.setTransientFilePath(transientFilePath);
        JobInfo saved = jobInfoRepository.save(job);
        createdJobIds.add(saved.getId());
        return saved;
    }
}
