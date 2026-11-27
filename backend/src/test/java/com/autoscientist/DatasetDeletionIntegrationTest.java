package com.autoscientist;

import com.autoscientist.entity.Dataset;
import com.autoscientist.entity.Experiment;
import com.autoscientist.entity.ExperimentVersion;
import com.autoscientist.entity.JobInfo;
import com.autoscientist.entity.ModelArtifact;
import com.autoscientist.entity.ModelRun;
import com.autoscientist.entity.User;
import com.autoscientist.exception.UnauthorizedException;
import com.autoscientist.repository.DatasetRepository;
import com.autoscientist.repository.ExperimentRepository;
import com.autoscientist.repository.ExperimentVersionRepository;
import com.autoscientist.repository.JobInfoRepository;
import com.autoscientist.repository.ModelArtifactRepository;
import com.autoscientist.repository.ModelRunRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class DatasetDeletionIntegrationTest {

    @Autowired
    private DatasetService datasetService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private ExperimentRepository experimentRepository;
    @Autowired
    private ExperimentVersionRepository experimentVersionRepository;
    @Autowired
    private ModelRunRepository modelRunRepository;
    @Autowired
    private ModelArtifactRepository modelArtifactRepository;
    @Autowired
    private JobInfoRepository jobInfoRepository;

    @MockitoSpyBean
    private ModelArtifactRepository modelArtifactRepositorySpy;

    @Test
    void deleteDatasetWithTrainedModelArtifactsSucceeds() {
        User owner = createUser("deletion-owner");
        Dataset dataset = createDataset(owner, "trained");
        ExperimentVersion version = createTrainedGraph(dataset, owner);

        Long artifactId = modelArtifactRepository.findAll().stream()
                .filter(a -> a.getExperimentVersion().getId().equals(version.getId()))
                .findFirst().orElseThrow().getId();
        Long runId = modelRunRepository.findAll().stream()
                .filter(r -> r.getExperimentVersion().getId().equals(version.getId()))
                .findFirst().orElseThrow().getId();

        datasetService.deleteDataset(dataset.getId(), owner);

        assertThat(datasetRepository.findById(dataset.getId())).isEmpty();
        assertThat(modelArtifactRepository.findById(artifactId)).isEmpty();
        assertThat(modelRunRepository.findById(runId)).isEmpty();
        assertThat(experimentVersionRepository.findById(version.getId())).isEmpty();
        assertThat(experimentRepository.findAll()).noneMatch(e -> e.getDataset().getId().equals(dataset.getId()));
    }

    @Test
    void deleteDatasetWithoutDependentsSucceeds() {
        User owner = createUser("deletion-plain");
        Dataset dataset = createDataset(owner, "plain");

        datasetService.deleteDataset(dataset.getId(), owner);

        assertThat(datasetRepository.findById(dataset.getId())).isEmpty();
    }

    @Test
    void nonOwnerCannotDeleteDatasetAndNothingIsRemoved() {
        User owner = createUser("deletion-owner2");
        User attacker = createUser("deletion-attacker");
        Dataset dataset = createDataset(owner, "protected");
        createTrainedGraph(dataset, owner);

        assertThatThrownBy(() -> datasetService.deleteDataset(dataset.getId(), attacker))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(datasetRepository.findById(dataset.getId())).isPresent();
        assertThat(modelArtifactRepository.count()).isPositive();
    }

    @Test
    void failedDeletionRollsBackEveryChange() {
        User owner = createUser("deletion-rollback");
        Dataset dataset = createDataset(owner, "rollback");
        ExperimentVersion version = createTrainedGraph(dataset, owner);
        long artifactsBefore = modelArtifactRepository.count();
        long runsBefore = modelRunRepository.count();

        doThrow(new IllegalStateException("simulated artifact delete failure"))
                .when(modelArtifactRepositorySpy).deleteAll(org.mockito.ArgumentMatchers.anyList());

        assertThatThrownBy(() -> datasetService.deleteDataset(dataset.getId(), owner))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated artifact delete failure");

        assertThat(datasetRepository.findById(dataset.getId())).isPresent();
        assertThat(modelArtifactRepository.count()).isEqualTo(artifactsBefore);
        assertThat(modelRunRepository.count()).isEqualTo(runsBefore);
        assertThat(experimentVersionRepository.findById(version.getId())).isPresent();
    }

    @Test
    void deleteDatasetRemovesJobsThatReferenceIt() {
        User owner = createUser("deletion-jobs");
        Dataset dataset = createDataset(owner, "with-jobs");
        JobInfo job = new JobInfo();
        job.setType(JobInfo.JobType.EDA);
        job.setOwner(owner);
        job.setDatasetId(dataset.getId());
        jobInfoRepository.save(job);
        UUID jobId = job.getId();

        User other = createUser("deletion-other");
        JobInfo unrelated = new JobInfo();
        unrelated.setType(JobInfo.JobType.OTHER);
        unrelated.setOwner(other);
        unrelated.setDatasetId(null);
        jobInfoRepository.save(unrelated);
        UUID unrelatedId = unrelated.getId();

        datasetService.deleteDataset(dataset.getId(), owner);

        assertThat(jobInfoRepository.findById(jobId)).isEmpty();
        assertThat(jobInfoRepository.findById(unrelatedId)).isPresent();
    }

    private User createUser(String prefix) {
        return userRepository.save(User.builder()
                .name(prefix)
                .email(prefix + "-" + UUID.randomUUID() + "@example.test")
                .passwordHash("$2a$10$test.placeholder.hash.not.used.for.login")
                .role("ROLE_USER")
                .build());
    }

    private Dataset createDataset(User owner, String name) {
        return datasetRepository.save(Dataset.builder()
                .owner(owner)
                .name(name)
                .originalFilename(name + ".csv")
                .storedFilename(name + "-" + UUID.randomUUID() + ".csv")
                .fileSize(16L)
                .build());
    }

    private ExperimentVersion createTrainedGraph(Dataset dataset, User owner) {
        Experiment experiment = experimentRepository.save(Experiment.builder()
                .dataset(dataset)
                .user(owner)
                .name("exp-" + UUID.randomUUID())
                .targetColumn("outcome")
                .problemType("REGRESSION")
                .build());
        ExperimentVersion version = experimentVersionRepository.save(ExperimentVersion.builder()
                .experiment(experiment)
                .versionNumber(1)
                .build());
        modelRunRepository.save(ModelRun.builder()
                .experimentVersion(version)
                .modelName("Logistic Regression")
                .algorithm("LOGISTIC_REGRESSION")
                .isBestModel(true)
                .build());
        modelArtifactRepository.save(ModelArtifact.builder()
                .experimentVersion(version)
                .modelName("Logistic Regression")
                .payload(new byte[]{1, 2, 3, 4})
                .build());
        return version;
    }
}
