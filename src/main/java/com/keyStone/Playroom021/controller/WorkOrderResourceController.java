package com.keyStone.Playroom021.controller;

import com.keyStone.Playroom021.dto.JobActionRequest;
import com.keyStone.Playroom021.dto.PartUsageRequest;
import com.keyStone.Playroom021.dto.PartUsageResponse;
import com.keyStone.Playroom021.dto.TimeLogRequest;
import com.keyStone.Playroom021.dto.TimeLogResponse;
import com.keyStone.Playroom021.dto.TimeLogSummaryResponse;
import com.keyStone.Playroom021.dto.WorkOrderDetailResponse;
import com.keyStone.Playroom021.security.CustomUserDetails;
import com.keyStone.Playroom021.service.PartUsageService;
import com.keyStone.Playroom021.service.TechnicianJobService;
import com.keyStone.Playroom021.service.TechnicianJobService.JobAction;
import com.keyStone.Playroom021.service.TimeLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Step 6: technician job actions, part usage and time logs, all nested under a work order.
 * Role gating is in SecurityConfig; row-level rules (technician = assigned work orders only)
 * are enforced in the services.
 */
@RestController
@RequestMapping("/api/work-orders/{workOrderId}")
@RequiredArgsConstructor
public class WorkOrderResourceController {

    private final TechnicianJobService technicianJobService;
    private final PartUsageService partUsageService;
    private final TimeLogService timeLogService;

    // ---- Technician job actions ----

    @PostMapping("/start")
    public ResponseEntity<WorkOrderDetailResponse> start(@AuthenticationPrincipal CustomUserDetails principal,
                                                          @PathVariable Long workOrderId,
                                                          @Valid @RequestBody(required = false) JobActionRequest body) {
        return ResponseEntity.ok(technicianJobService.perform(principal, workOrderId, JobAction.START, noteOf(body)));
    }

    @PostMapping("/hold")
    public ResponseEntity<WorkOrderDetailResponse> hold(@AuthenticationPrincipal CustomUserDetails principal,
                                                         @PathVariable Long workOrderId,
                                                         @Valid @RequestBody(required = false) JobActionRequest body) {
        return ResponseEntity.ok(technicianJobService.perform(principal, workOrderId, JobAction.HOLD, noteOf(body)));
    }

    @PostMapping("/resume")
    public ResponseEntity<WorkOrderDetailResponse> resume(@AuthenticationPrincipal CustomUserDetails principal,
                                                           @PathVariable Long workOrderId,
                                                           @Valid @RequestBody(required = false) JobActionRequest body) {
        return ResponseEntity.ok(technicianJobService.perform(principal, workOrderId, JobAction.RESUME, noteOf(body)));
    }

    @PostMapping("/complete")
    public ResponseEntity<WorkOrderDetailResponse> complete(@AuthenticationPrincipal CustomUserDetails principal,
                                                             @PathVariable Long workOrderId,
                                                             @Valid @RequestBody(required = false) JobActionRequest body) {
        return ResponseEntity.ok(technicianJobService.perform(principal, workOrderId, JobAction.COMPLETE, noteOf(body)));
    }

    private static String noteOf(JobActionRequest body) {
        return body == null ? null : body.getNote();
    }

    // ---- Part usage ----

    @PostMapping("/part-usage")
    public ResponseEntity<List<PartUsageResponse>> recordPartUsage(@AuthenticationPrincipal CustomUserDetails principal,
                                                                    @PathVariable Long workOrderId,
                                                                    @Valid @RequestBody PartUsageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partUsageService.record(principal, workOrderId, request));
    }

    @GetMapping("/part-usage")
    public ResponseEntity<List<PartUsageResponse>> listPartUsage(@AuthenticationPrincipal CustomUserDetails principal,
                                                                  @PathVariable Long workOrderId) {
        return ResponseEntity.ok(partUsageService.list(principal, workOrderId));
    }

    @DeleteMapping("/part-usage/{usageId}")
    public ResponseEntity<Void> removePartUsage(@AuthenticationPrincipal CustomUserDetails principal,
                                                 @PathVariable Long workOrderId, @PathVariable Long usageId) {
        partUsageService.remove(principal, workOrderId, usageId);
        return ResponseEntity.noContent().build();
    }

    // ---- Time logs ----

    @PostMapping("/time-logs")
    public ResponseEntity<TimeLogResponse> createTimeLog(@AuthenticationPrincipal CustomUserDetails principal,
                                                          @PathVariable Long workOrderId,
                                                          @Valid @RequestBody TimeLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timeLogService.create(principal, workOrderId, request));
    }

    @GetMapping("/time-logs")
    public ResponseEntity<TimeLogSummaryResponse> listTimeLogs(@AuthenticationPrincipal CustomUserDetails principal,
                                                                @PathVariable Long workOrderId) {
        return ResponseEntity.ok(timeLogService.list(principal, workOrderId));
    }

    @DeleteMapping("/time-logs/{logId}")
    public ResponseEntity<Void> deleteTimeLog(@AuthenticationPrincipal CustomUserDetails principal,
                                               @PathVariable Long workOrderId, @PathVariable Long logId) {
        timeLogService.delete(principal, workOrderId, logId);
        return ResponseEntity.noContent().build();
    }
}
