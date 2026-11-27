package com.autoscientist;

import com.autoscientist.dto.dataset.DatasetDetailDto;
import com.autoscientist.dto.importing.ImportOptionsDto;
import com.autoscientist.dto.ml.AutoMlResponseDto;
import com.autoscientist.dto.ml.AutoMlTrainRequest;
import com.autoscientist.dto.ml.ModelPredictionResponseDto;
import com.autoscientist.entity.User;
import com.autoscientist.exception.ResourceNotFoundException;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.AutoMlService;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.ModelServingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModelArtifactPersistenceAndServingTest {

    @Autowired
    private DatasetService datasetService;
    @Autowired
    private AutoMlService autoMlService;
    @Autowired
    private ModelServingService modelServingService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void trainingPersistsArtifactAndServingReplaysPreprocessing() {
        User owner = createUser("owner");
        DatasetDetailDto dataset = datasetService.uploadDataset(
                new MockMultipartFile("file", "serving.csv", "text/csv",
                        csv().getBytes(StandardCharsets.UTF_8)),
                "serving-dataset", "artifact persistence test",
                ImportOptionsDto.builder().build(), owner);

        AutoMlResponseDto trained = autoMlService.trainAndBenchmark(
                dataset.getId(),
                AutoMlTrainRequest.builder()
                        .targetColumn("outcome")
                        .problemType("REGRESSION")
                        .experimentName("serving-test")
                        .build(),
                owner);

        Long artifactId = trained.getBestModel().getArtifactId();
        assertNotNull(artifactId, "Best model must persist a serving artifact");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("age", 41);
        payload.put("income", 62_000);
        payload.put("study_hours", 4.5);
        payload.put("visits", 3);
        payload.put("region", "North");
        payload.put("group", "A");

        var response = modelServingService.predict(artifactId, payload, owner);
        assertEquals(200, response.getStatusCode().value());
        ModelPredictionResponseDto prediction = response.getBody().getData();
        assertNotNull(prediction);
        assertTrue(Double.isFinite(prediction.getPrediction()));
        assertEquals("REGRESSION", prediction.getProblemType());
        assertEquals("outcome", prediction.getTargetColumn());
        assertEquals(Boolean.FALSE, prediction.getProbabilityAvailable());

        User stranger = createUser("stranger");
        assertThrows(ResourceNotFoundException.class,
                () -> modelServingService.predict(artifactId, payload, stranger),
                "Artifacts must not be readable across users");
    }

    @Test
    void binaryClassificationServingExposesPositiveClassLabel() {
        User owner = createUser("classification-owner");
        DatasetDetailDto dataset = datasetService.uploadDataset(
                new MockMultipartFile("file", "classification-serving.csv", "text/csv",
                        classificationCsv().getBytes(StandardCharsets.UTF_8)),
                "classification-serving-dataset", "binary classification serving test",
                ImportOptionsDto.builder().build(), owner);

        AutoMlResponseDto trained = autoMlService.trainAndBenchmark(
                dataset.getId(),
                AutoMlTrainRequest.builder()
                        .targetColumn("outcome")
                        .problemType("CLASSIFICATION")
                        .experimentName("classification-serving-test")
                        .build(),
                owner);

        Long artifactId = trained.getBestModel().getArtifactId();
        assertNotNull(artifactId);
        assertEquals("Yes", trained.getPositiveClassLabel());
        assertEquals(1, trained.getPositiveClassIndex());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("age", 45);
        payload.put("visits", 4);

        var response = modelServingService.predict(artifactId, payload, owner);
        assertEquals(200, response.getStatusCode().value());
        ModelPredictionResponseDto prediction = response.getBody().getData();
        assertNotNull(prediction);
        assertEquals("CLASSIFICATION", prediction.getProblemType());
        assertNotNull(prediction.getPredictedClass());
        assertEquals(1, prediction.getPositiveClassIndex());
        assertEquals("Yes", prediction.getPositiveClassLabel());
        assertEquals(1, prediction.getProbabilityClassIndex());
        assertEquals("Yes", prediction.getProbabilityClassLabel());
        assertEquals("AVAILABLE", prediction.getProbabilityStatus());
        assertEquals(0.5, prediction.getDecisionThreshold(), 1e-9);
        assertEquals(Boolean.TRUE, prediction.getProbabilityAvailable());
        assertEquals(prediction.getProbability(), prediction.getPositiveClassProbability());
    }

    private User createUser(String prefix) {
        return userRepository.save(User.builder()
                .name(prefix)
                .email(prefix + "-" + UUID.randomUUID() + "@example.test")
                .passwordHash("$2a$10$test.placeholder.hash.not.used.for.login")
                .role("ROLE_USER")
                .build());
    }

    private String classificationCsv() {
        StringBuilder out = new StringBuilder("age,visits,outcome\n");
        for (int i = 0; i < 80; i++) {
            int age = 20 + (i % 40);
            int visits = (i % 7) + 1;
            String outcome = (age + visits * 3) % 2 == 0 ? "No" : "Yes";
            out.append(age).append(',').append(visits).append(',').append(outcome).append('\n');
        }
        return out.toString();
    }

    private String csv() {
        Random random = new Random(7);
        String[] regions = {"North", "South", "East", "West"};
        String[] groups = {"A", "B", "C"};
        StringBuilder out = new StringBuilder("age,income,study_hours,visits,region,group,outcome\n");
        for (int i = 0; i < 80; i++) {
            int age = 20 + random.nextInt(50);
            int income = 25_000 + random.nextInt(90_000);
            double study = Math.round(random.nextDouble() * 100) / 10.0;
            int visits = random.nextInt(20);
            double outcome = study * 3.0 + age * 0.2 + income / 8000.0 + visits * 0.4
                    + random.nextGaussian() * 1.5;
            out.append(age).append(',')
                    .append(income).append(',')
                    .append(study).append(',')
                    .append(visits).append(',')
                    .append(regions[i % regions.length]).append(',')
                    .append(groups[i % groups.length]).append(',')
                    .append(String.format(java.util.Locale.ROOT, "%.4f", outcome)).append('\n');
        }
        return out.toString();
    }
}
