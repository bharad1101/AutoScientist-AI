package com.autoscientist.controller;

import com.autoscientist.dto.analysis.BoxPlotDto;
import com.autoscientist.dto.analysis.CorrelationMatrixDto;
import com.autoscientist.dto.analysis.GroupedSummaryDto;
import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.EdaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets/{datasetId}/eda")
@RequiredArgsConstructor
public class EdaController {

    private final EdaService edaService;
    private final AuthService authService;

    @GetMapping("/correlations")
    public ResponseEntity<ApiResponse<CorrelationMatrixDto>> getCorrelationMatrix(
            @PathVariable("datasetId") Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        CorrelationMatrixDto matrix = edaService.getCorrelationMatrix(datasetId, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Correlation matrix computed successfully", matrix));
    }

    @GetMapping("/boxplots")
    public ResponseEntity<ApiResponse<List<BoxPlotDto>>> getBoxPlots(
            @PathVariable("datasetId") Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        List<BoxPlotDto> boxPlots = edaService.getBoxPlots(datasetId, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Box plot distributions computed successfully", boxPlots));
    }

    @GetMapping("/grouped")
    public ResponseEntity<ApiResponse<List<GroupedSummaryDto>>> getGroupedSummary(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("numericVar") String numericVar,
            @RequestParam("categoricalVar") String categoricalVar,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        List<GroupedSummaryDto> summaries = edaService.getGroupedSummary(datasetId, numericVar, categoricalVar, currentUser);

        return ResponseEntity.ok(ApiResponse.success("Grouped summary computed successfully", summaries));
    }

    @GetMapping("/histograms")
    public ResponseEntity<ApiResponse<List<com.autoscientist.dto.analysis.HistogramDto>>> getHistograms(
            @PathVariable("datasetId") Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Histograms computed successfully",
                edaService.getHistograms(datasetId, currentUser)));
    }

    @GetMapping("/scatter")
    public ResponseEntity<ApiResponse<com.autoscientist.dto.analysis.ScatterDto>> getScatter(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("xVar") String xVar,
            @RequestParam("yVar") String yVar,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Scatter data computed successfully",
                edaService.getScatter(datasetId, xVar, yVar, currentUser)));
    }

    @GetMapping("/missingness")
    public ResponseEntity<ApiResponse<List<com.autoscientist.dto.analysis.MissingnessDto>>> getMissingness(
            @PathVariable("datasetId") Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Missingness profile computed successfully",
                edaService.getMissingness(datasetId, currentUser)));
    }

    @GetMapping("/categorical")
    public ResponseEntity<ApiResponse<List<com.autoscientist.dto.analysis.CategoricalSummaryDto>>> getCategoricalSummaries(
            @PathVariable("datasetId") Long datasetId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Categorical summaries computed successfully",
                edaService.getCategoricalSummaries(datasetId, currentUser)));
    }

    @GetMapping("/timeseries")
    public ResponseEntity<ApiResponse<List<com.autoscientist.dto.analysis.TimeSeriesPointDto>>> getTimeSeries(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("timeVar") String timeVar,
            @RequestParam("valueVar") String valueVar,
            @RequestParam(value = "aggregation", defaultValue = "0") int aggregation,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Time series computed successfully",
                edaService.getTimeSeries(datasetId, timeVar, valueVar, aggregation, currentUser)));
    }

    @GetMapping("/group-distributions")
    public ResponseEntity<ApiResponse<List<com.autoscientist.dto.analysis.GroupDistributionDto>>> getGroupDistributions(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam("numericVar") String numericVar,
            @RequestParam("categoricalVar") String categoricalVar,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Cohort distributions computed successfully",
                edaService.getGroupDistributions(datasetId, numericVar, categoricalVar, currentUser)));
    }

    @GetMapping("/missingness-matrix")
    public ResponseEntity<ApiResponse<com.autoscientist.dto.analysis.MissingnessMatrixDto>> getMissingnessMatrix(
            @PathVariable("datasetId") Long datasetId,
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {

        User currentUser = authService.getCurrentUserEntity(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Missing-value matrix computed successfully",
                edaService.getMissingnessMatrix(datasetId, limit, currentUser)));
    }
}
