package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.research.EvidenceGraphDto;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.EvidenceGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/datasets/{datasetId}")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceGraphService evidenceGraphService;
    private final AuthService authService;

    @GetMapping("/evidence")
    public ResponseEntity<ApiResponse<EvidenceGraphDto>> getEvidenceGraph(
            @PathVariable Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Evidence graph retrieved successfully",
                evidenceGraphService.buildGraph(datasetId, user)));
    }

    @GetMapping("/reliability")
    public ResponseEntity<ApiResponse<EvidenceGraphDto.ReliabilityScorecard>> getReliability(
            @PathVariable Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Reliability scorecard retrieved successfully",
                evidenceGraphService.buildGraph(datasetId, user).getReliability()));
    }
}
