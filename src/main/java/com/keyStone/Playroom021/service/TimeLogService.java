package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.TimeLogRequest;
import com.keyStone.Playroom021.dto.TimeLogResponse;
import com.keyStone.Playroom021.dto.TimeLogSummaryResponse;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.TimeLog;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.exception.InvalidRequestException;
import com.keyStone.Playroom021.repository.TimeLogRepository;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Time logs on work orders. Only a TECHNICIAN assigned to the work order can log time
 * (their own); MANAGER/DISPATCHER can read and delete. A technician can only delete their
 * own entries. Unassigned work orders 404 for a technician (WorkOrderService.findAccessible).
 */
@Service
@RequiredArgsConstructor
public class TimeLogService {

    /** Time can be logged while working, on hold, or as a late entry right after completion. */
    private static final Set<WorkOrderStatus> LOGGABLE =
            EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD, WorkOrderStatus.COMPLETED);

    private static final Duration MAX_ENTRY = Duration.ofHours(24);

    private final WorkOrderService workOrderService;
    private final TimeLogRepository timeLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public TimeLogResponse create(CustomUserDetails principal, Long workOrderId, TimeLogRequest request) {
        if (principal.getUser().getRole() != Role.TECHNICIAN) {
            throw new AccessDeniedException("Only a technician can log time");
        }
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        if (!LOGGABLE.contains(wo.getStatus())) {
            throw new ConflictException("Time can only be logged on a work order that is IN_PROGRESS, ON_HOLD "
                    + "or COMPLETED (current status: " + wo.getStatus() + ")");
        }

        Instant start = request.getStartedAt();
        Instant end = request.getEndedAt();
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("endedAt must be after startedAt");
        }
        if (end.isAfter(Instant.now().plusSeconds(60))) {
            throw new InvalidRequestException("endedAt cannot be in the future");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new InvalidRequestException("A time log must be at least 1 minute long");
        }
        if (duration.compareTo(MAX_ENTRY) > 0) {
            throw new InvalidRequestException("A single time log cannot exceed 24 hours");
        }

        TimeLog saved = timeLogRepository.save(TimeLog.builder()
                .workOrder(wo)
                .technician(userRepository.getReferenceById(principal.getUser().getId()))
                .startedAt(start)
                .endedAt(end)
                .minutes((int) duration.toMinutes())
                .note(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim())
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TimeLogSummaryResponse list(CustomUserDetails principal, Long workOrderId) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        List<TimeLogResponse> entries = timeLogRepository.findByWorkOrderIdOrderByStartedAtAscIdAsc(wo.getId())
                .stream().map(this::toResponse).toList();
        long total = entries.stream().mapToLong(TimeLogResponse::getMinutes).sum();
        return TimeLogSummaryResponse.builder().entries(entries).totalMinutes(total).build();
    }

    @Transactional
    public void delete(CustomUserDetails principal, Long workOrderId, Long logId) {
        WorkOrder wo = workOrderService.findAccessible(principal, workOrderId);
        TimeLog log = timeLogRepository.findByIdAndWorkOrderId(logId, wo.getId())
                .orElseThrow(() -> new EntityNotFoundException("Time log not found"));

        if (principal.getUser().getRole() == Role.TECHNICIAN) {
            // A technician only ever touches their own entries; someone else's is a 404.
            if (!log.getTechnician().getId().equals(principal.getUser().getId())) {
                throw new EntityNotFoundException("Time log not found");
            }
            if (!LOGGABLE.contains(wo.getStatus())) {
                throw new ConflictException("Time logs can no longer be changed on a work order that is "
                        + wo.getStatus());
            }
        }
        timeLogRepository.delete(log);
    }

    private TimeLogResponse toResponse(TimeLog t) {
        return TimeLogResponse.builder()
                .id(t.getId())
                .workOrderId(t.getWorkOrder().getId())
                .technicianId(t.getTechnician().getId())
                .technician(t.getTechnician().getFullName())
                .startedAt(t.getStartedAt())
                .endedAt(t.getEndedAt())
                .minutes(t.getMinutes())
                .note(t.getNote())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
