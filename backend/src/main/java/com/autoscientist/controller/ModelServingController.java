package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.ml.ModelPredictionResponseDto;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.ModelServingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/models")
public class ModelServingController {

    private final ModelServingService modelServingService;
    private final AuthService authService;

    public ModelServingController(ModelServingService modelServingService, AuthService authService) {
        this.modelServingService = modelServingService;
        this.authService = authService;
    }

    @PostMapping("/{modelId}/predict")
    public ResponseEntity<ApiResponse<ModelPredictionResponseDto>> predict(
            @PathVariable Long modelId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) Map<String, Object> payload) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        return modelServingService.predict(modelId, payload, user);
    }
}
