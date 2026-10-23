package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.research.ExperimentDto;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExperimentDto>>> listExperiments(
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        List<ExperimentDto> list = experimentService.listUserExperiments(currentUser);

        return ResponseEntity.ok(ApiResponse.success("Experiments retrieved successfully", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExperimentDto>> getExperiment(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        ExperimentDto exp = experimentService.getExperiment(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Experiment details retrieved successfully", exp));
    }

    @GetMapping("/{id}/comparison")
    public ResponseEntity<ApiResponse<com.autoscientist.dto.research.ExperimentComparisonDto>> compareExperiment(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        com.autoscientist.dto.research.ExperimentComparisonDto comparison =
                experimentService.compareExperiment(id, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Experiment comparison retrieved successfully", comparison));
    }
}
