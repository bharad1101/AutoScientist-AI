package com.autoscientist.controller;

import com.autoscientist.dto.ml.AutoMlTrainRequest;
import com.autoscientist.entity.JobInfo;
import com.autoscientist.entity.User;
import com.autoscientist.exception.BadRequestException;
import com.autoscientist.service.AssociationService;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.AutoMlService;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.EdaService;
import com.autoscientist.service.JobExecutorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobExecutorService executorService;
    private final AuthService authService;
    private final DatasetService datasetService;
    private final EdaService edaService;
    private final AssociationService associationService;
    private final AutoMlService autoMlService;

    public JobController(JobExecutorService executorService,
                         AuthService authService,
                         DatasetService datasetService,
                         EdaService edaService,
                         AssociationService associationService,
                         AutoMlService autoMlService) {
        this.executorService = executorService;
        this.authService = authService;
        this.datasetService = datasetService;
        this.edaService = edaService;
        this.associationService = associationService;
        this.autoMlService = autoMlService;
    }

    /**
     * Submit a real analysis job. Dataset jobs are validated synchronously so
     * ownership/eligibility errors are returned immediately rather than being
     * discovered by a background thread.
     */
    @PostMapping
    public ResponseEntity<JobInfo> submitJob(
            @RequestParam JobInfo.JobType type,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) String targetColumn,
            @RequestParam(required = false) String problemType,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        Runnable work;
        switch (type) {
            case EDA -> {
                requireDataset(datasetId, user);
                Long id = datasetId;
                work = () -> {
                    edaService.getCorrelationMatrix(id, user);
                    edaService.getBoxPlots(id, user);
                    edaService.getHistograms(id, user);
                    edaService.getMissingness(id, user);
                };
            }
            case ASSOCIATION -> {
                requireDataset(datasetId, user);
                Long id = datasetId;
                work = () -> associationService.computeAndSaveAssociations(id, user);
            }
            case AUTOML -> {
                requireDataset(datasetId, user);
                if (targetColumn == null || targetColumn.isBlank()) {
                    throw new BadRequestException("targetColumn is required for AUTOML jobs.");
                }
                Long id = datasetId;
                String target = targetColumn;
                String problem = problemType == null || problemType.isBlank() ? "AUTO" : problemType;
                work = () -> autoMlService.trainAndBenchmark(id,
                        AutoMlTrainRequest.builder()
                                .targetColumn(target)
                                .problemType(problem)
                                .experimentName("Async AutoML - " + target)
                                .build(), user);
            }
            case INGESTION -> throw new BadRequestException(
                    "Asynchronous ingestion is not supported. Use POST /api/datasets/upload.");
            default -> throw new BadRequestException("Unsupported job type: " + type);
        }

        JobInfo job = executorService.submitJob(type, user, datasetId, targetColumn, problemType, work, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping
    public ResponseEntity<List<JobInfo>> listJobs(
            @RequestParam(required = false) JobInfo.JobStatus[] status,
            @RequestParam(required = false) JobInfo.JobType[] type,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(executorService.listJobs(user, status, type));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobInfo> getJob(@PathVariable UUID id,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return executorService.getJob(id, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<JobInfo> cancelJob(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(executorService.cancelJob(id, user));
    }

    private void requireDataset(Long datasetId, User user) {
        if (datasetId == null) {
            throw new BadRequestException("datasetId is required for this job type.");
        }
        datasetService.getDatasetDetail(datasetId, user);
    }
}
