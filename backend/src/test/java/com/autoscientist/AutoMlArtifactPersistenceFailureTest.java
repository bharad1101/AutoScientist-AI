package com.autoscientist;

import com.autoscientist.dto.dataset.DatasetDetailDto;
import com.autoscientist.dto.importing.ImportOptionsDto;
import com.autoscientist.dto.ml.AutoMlTrainRequest;
import com.autoscientist.entity.ModelArtifact;
import com.autoscientist.entity.User;
import com.autoscientist.repository.ModelArtifactRepository;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.AutoMlService;
import com.autoscientist.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:artifact_failure_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
class AutoMlArtifactPersistenceFailureTest {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private AutoMlService autoMlService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private ModelArtifactRepository modelArtifactRepository;

    @Test
    void artifactPersistenceFailureFailsTrainingInsteadOfReturningAServableModel() {
        User owner = userRepository.save(User.builder()
                .name("artifact-failure-owner")
                .email("artifact-failure-" + UUID.randomUUID() + "@example.test")
                .passwordHash("$2a$10$test.placeholder.hash.not.used.for.login")
                .role("ROLE_USER")
                .build());
        DatasetDetailDto dataset = datasetService.uploadDataset(
                new MockMultipartFile("file", "artifact-failure.csv", "text/csv",
                        csv().getBytes(StandardCharsets.UTF_8)),
                "artifact-failure-dataset", "artifact persistence failure test",
                ImportOptionsDto.builder().build(), owner);

        doThrow(new RuntimeException("storage unavailable"))
                .when(modelArtifactRepository).save(any(ModelArtifact.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> autoMlService.trainAndBenchmark(dataset.getId(),
                        AutoMlTrainRequest.builder()
                                .targetColumn("outcome")
                                .problemType("REGRESSION")
                                .build(),
                        owner));

        assertTrue(failure.getMessage().contains("Failed to persist serving artifact"));
        verify(modelArtifactRepository).save(any(ModelArtifact.class));
    }

    private static String csv() {
        Random random = new Random(17);
        StringBuilder out = new StringBuilder("age,income,study_hours,visits,outcome\n");
        for (int i = 0; i < 60; i++) {
            int age = 20 + random.nextInt(45);
            int income = 30_000 + random.nextInt(80_000);
            double study = Math.round(random.nextDouble() * 100) / 10.0;
            int visits = random.nextInt(20);
            double outcome = study * 2.5 + age * 0.15 + income / 10_000.0 + visits * 0.3;
            out.append(age).append(',')
                    .append(income).append(',')
                    .append(study).append(',')
                    .append(visits).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", outcome)).append('\n');
        }
        return out.toString();
    }
}
