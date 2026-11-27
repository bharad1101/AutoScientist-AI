package com.autoscientist;

import com.autoscientist.dto.dataset.DatasetDetailDto;
import com.autoscientist.dto.importing.ImportOptionsDto;
import com.autoscientist.dto.ml.AutoMlTrainRequest;
import com.autoscientist.entity.User;
import com.autoscientist.repository.UserRepository;
import com.autoscientist.service.AssociationService;
import com.autoscientist.service.AutoMlService;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.EdaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Opt-in workflow benchmark. Run with:
 *
 * <pre>./mvnw -Dtest=WorkflowPerformanceBenchmarkTest -Dbenchmark=true test</pre>
 *
 * It prints one machine-readable line per stage. The same generated files are
 * used before and after optimisation so the numbers are directly comparable.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class WorkflowPerformanceBenchmarkTest {

    private static final long SEED = 20260101L;

    @Autowired
    private DatasetService datasetService;
    @Autowired
    private EdaService edaService;
    @Autowired
    private AssociationService associationService;
    @Autowired
    private AutoMlService autoMlService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void benchmarkRepresentativeWorkflow() throws Exception {
        User user = createUser();
        benchmarkDataset(user, "small", 2_000);
        benchmarkDataset(user, "medium", 20_000);
        benchmarkUploadOnly(user, "large", 100_000);
    }

    private void benchmarkDataset(User user, String label, int rows) {
        String csv = generateCsv(rows);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", label + ".csv", "text/csv", bytes);

        try {
            long uploadStart = System.nanoTime();
            DatasetDetailDto uploaded = datasetService.uploadDataset(
                    file, label + "-dataset", "benchmark", ImportOptionsDto.builder().build(), user);
            Long datasetId = uploaded.getId();
            report(label, rows, "upload+profile+quality", uploadStart);

            long correlationsStart = System.nanoTime();
            edaService.getCorrelationMatrix(datasetId, user);
            report(label, rows, "eda-correlations", correlationsStart);

            long boxStart = System.nanoTime();
            edaService.getBoxPlots(datasetId, user);
            report(label, rows, "eda-boxplots", boxStart);

            long histStart = System.nanoTime();
            edaService.getHistograms(datasetId, user);
            report(label, rows, "eda-histograms", histStart);

            long missStart = System.nanoTime();
            edaService.getMissingness(datasetId, user);
            report(label, rows, "eda-missingness", missStart);

            long assocStart = System.nanoTime();
            associationService.computeAndSaveAssociations(datasetId, user);
            report(label, rows, "associations", assocStart);

            // AutoML tree training grows super-linearly; keep the benchmark bounded.
            if (rows <= 2_000) {
                long autoMlStart = System.nanoTime();
                autoMlService.trainAndBenchmark(datasetId,
                        AutoMlTrainRequest.builder()
                                .targetColumn("outcome")
                                .problemType("REGRESSION")
                                .experimentName("benchmark")
                                .build(), user);
                report(label, rows, "automl", autoMlStart);
            }
        } finally {
            cleanup(user, label + "-dataset");
        }
    }

    private void benchmarkUploadOnly(User user, String label, int rows) {
        byte[] bytes = generateCsv(rows).getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", label + ".csv", "text/csv", bytes);
        try {
            long start = System.nanoTime();
            datasetService.uploadDataset(file, label + "-dataset", "benchmark",
                    ImportOptionsDto.builder().build(), user);
            report(label, rows, "upload+profile+quality", start);
        } finally {
            cleanup(user, label + "-dataset");
        }
    }

    private void cleanup(User user, String name) {
        datasetService.listUserDatasets(user).stream()
                .filter(d -> name.equals(d.getName()))
                .findFirst()
                .ifPresent(d -> datasetService.deleteDataset(d.getId(), user));
    }

    private User createUser() {
        String email = "benchmark-" + UUID.randomUUID() + "@example.test";
        return userRepository.save(User.builder()
                .name("Benchmark Runner")
                .email(email)
                .passwordHash("$2a$10$benchmark.placeholder.hash.not.used.for.login")
                .role("ROLE_USER")
                .build());
    }

    private String generateCsv(int rows) {
        Random random = new Random(SEED);
        String[] regions = {"North", "South", "East", "West"};
        String[] groups = {"A", "B", "C"};
        StringBuilder out = new StringBuilder(rows * 96);
        out.append("record_id,age,income,study_hours,visits,score,region,group,outcome\n");
        for (int i = 0; i < rows; i++) {
            double age = 18 + random.nextInt(60);
            double income = 20_000 + random.nextDouble() * 120_000;
            double study = Math.round(random.nextDouble() * 100) / 10.0;
            int visits = random.nextInt(30);
            double score = 30 + study * 3.2 + age * 0.15 + random.nextGaussian() * 4;
            double outcome = score * 1.7 + income / 5000.0 + visits * 0.2 + random.nextGaussian() * 2;
            if (i % 97 == 0) {
                out.append(i).append(',').append((int) age).append(',').append(Math.round(income))
                        .append(',').append(study).append(',').append(visits).append(',').append(',')
                        .append(regions[i % regions.length]).append(',').append(groups[i % groups.length])
                        .append(',').append(String.format(Locale.ROOT, "%.3f", outcome)).append('\n');
            } else {
                out.append(i).append(',').append((int) age).append(',').append(Math.round(income))
                        .append(',').append(study).append(',').append(visits).append(',')
                        .append(String.format(Locale.ROOT, "%.3f", score)).append(',')
                        .append(regions[i % regions.length]).append(',').append(groups[i % groups.length])
                        .append(',').append(String.format(Locale.ROOT, "%.3f", outcome)).append('\n');
            }
        }
        return out.toString();
    }

    private static void report(String label, int rows, String stage, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        System.out.printf(Locale.ROOT, "BENCH dataset=%s rows=%d stage=%s ms=%d%n", label, rows, stage, elapsedMs);
    }
}
