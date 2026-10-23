package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.dataset.DataQualityReportDto;
import com.autoscientist.dto.dataset.DatasetDetailDto;
import com.autoscientist.dto.dataset.DatasetDto;
import com.autoscientist.dto.dataset.DatasetExclusionRequestDto;
import com.autoscientist.dto.dataset.ReprofileRequestDto;
import com.autoscientist.dto.dataset.WorkflowStatusDto;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.DatasetService;
import com.autoscientist.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final AuthService authService;
    private final WorkflowService workflowService;
    private final com.autoscientist.service.ImportPreviewService importPreviewService;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<com.autoscientist.dto.importing.ImportPreviewDto>> previewImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "options", required = false) String optionsJson,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Auth still required: previews may contain user data — never allow anonymous uploads.
        authService.getCurrentUserEntity(userDetails.getUsername());

        com.autoscientist.dto.importing.ImportOptionsDto options = null;
        if (optionsJson != null && !optionsJson.isBlank()) {
            try {
                options = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(optionsJson, com.autoscientist.dto.importing.ImportOptionsDto.class);
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid import options: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                "File preview generated", importPreviewService.preview(file, options)));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<DatasetDetailDto>> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "options", required = false) String optionsJson,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());

        com.autoscientist.dto.importing.ImportOptionsDto options = null;
        if (optionsJson != null && !optionsJson.isBlank()) {
            try {
                options = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(optionsJson, com.autoscientist.dto.importing.ImportOptionsDto.class);
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid import options: " + e.getMessage()));
            }
        }

        DatasetDetailDto result = datasetService.uploadDataset(file, name, description, options, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dataset imported and profiled successfully", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DatasetDto>>> listDatasets(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        List<DatasetDto> datasets = datasetService.listUserDatasets(currentUser);

        return ResponseEntity.ok(ApiResponse.success("Datasets retrieved successfully", datasets));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DatasetDetailDto>> getDatasetDetail(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        DatasetDetailDto dataset = datasetService.getDatasetDetail(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Dataset details retrieved successfully", dataset));
    }

    @GetMapping("/{id}/quality")
    public ResponseEntity<ApiResponse<DataQualityReportDto>> getDatasetQuality(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        DatasetDetailDto dataset = datasetService.getDatasetDetail(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Quality report retrieved successfully", dataset.getQualityReport()));
    }

    @GetMapping("/{id}/workflow-status")
    public ResponseEntity<ApiResponse<WorkflowStatusDto>> getWorkflowStatus(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        WorkflowStatusDto workflow = workflowService.getWorkflowStatus(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Workflow status retrieved successfully", workflow));
    }

    @PostMapping("/{id}/reprofile")
    public ResponseEntity<ApiResponse<DatasetDetailDto>> reprofileDataset(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ReprofileRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        DatasetDetailDto result = datasetService.reprofile(id, request, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Dataset re-profiled successfully", result));
    }

    @PutMapping("/{id}/exclusions")
    public ResponseEntity<ApiResponse<DatasetDetailDto>> updateExclusions(
            @PathVariable("id") Long id,
            @RequestBody(required = false) DatasetExclusionRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        DatasetDetailDto result = datasetService.updateExclusions(id, request, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Dataset exclusions updated successfully", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDataset(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        datasetService.deleteDataset(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Dataset deleted successfully", null));
    }
}
