package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.DashboardSummaryResponse;
import com.keyStone.Playroom021.dto.SlaReportResponse;
import com.keyStone.Playroom021.dto.TechnicianWorkloadResponse;
import com.keyStone.Playroom021.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Step 7 dashboard/report APIs (MANAGER and DISPATCHER only; gated in SecurityConfig). Logic is in DashboardService. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardReportController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok(dashboardService.summary());
    }

    @GetMapping("/sla")
    public ResponseEntity<SlaReportResponse> sla() {
        return ResponseEntity.ok(dashboardService.slaReport());
    }

    @GetMapping("/technician-workload")
    public ResponseEntity<TechnicianWorkloadResponse> technicianWorkload() {
        return ResponseEntity.ok(dashboardService.technicianWorkload());
    }
}
