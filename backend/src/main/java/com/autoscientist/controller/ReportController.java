package com.autoscientist.controller;

import com.autoscientist.dto.common.ApiResponse;
import com.autoscientist.dto.report.GenerateReportRequest;
import com.autoscientist.dto.report.ReportDto;
import com.autoscientist.entity.User;
import com.autoscientist.service.AuthService;
import com.autoscientist.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AuthService authService;

    @PostMapping("/datasets/{id}/reports")
    public ResponseEntity<ApiResponse<ReportDto>> generateReport(
            @PathVariable Long id,
            @RequestBody(required = false) GenerateReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        ReportDto report = reportService.generateReport(id, request, user);
        return ResponseEntity.ok(ApiResponse.success("Scientific report generated successfully.", report));
    }

    @GetMapping("/datasets/{id}/reports")
    public ResponseEntity<ApiResponse<List<ReportDto>>> listDatasetReports(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        List<ReportDto> reports = reportService.listDatasetReports(id, user);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportDto>>> listReports(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        List<ReportDto> reports = reportService.listUserReports(user);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<ApiResponse<ReportDto>> getReport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        ReportDto report = reportService.getReport(id, user);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/reports/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        byte[] pdfBytes = reportService.downloadPdf(id, user);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AutoScientist_Report_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/reports/{id}/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        byte[] csv = reportService.exportCsv(id, user).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AutoScientist_Report_" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/reports/{id}/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        byte[] xlsx = reportService.exportExcel(id, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AutoScientist_Report_" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    @GetMapping("/reports/{id}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = authService.getCurrentUserEntity(userDetails.getUsername());
        byte[] md = reportService.exportMarkdown(id, user).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AutoScientist_Report_" + id + ".md\"")
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(md);
    }
}
