package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.StatusHistoryResponse;
import com.keyStone.Playroom021.dto.WorkOrderDetailResponse;
import com.keyStone.Playroom021.dto.WorkOrderRequest;
import com.keyStone.Playroom021.dto.WorkOrderResponse;
import com.keyStone.Playroom021.dto.WorkOrderStatusUpdateRequest;
import com.keyStone.Playroom021.entity.Priority;
import com.keyStone.Playroom021.entity.Role;
import com.keyStone.Playroom021.entity.Site;
import com.keyStone.Playroom021.entity.User;
import com.keyStone.Playroom021.entity.WorkOrder;
import com.keyStone.Playroom021.entity.WorkOrderStatus;
import com.keyStone.Playroom021.entity.WorkOrderStatusHistory;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.exception.InvalidRequestException;
import com.keyStone.Playroom021.repository.SiteRepository;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.repository.WorkOrderStatusHistoryRepository;
import com.keyStone.Playroom021.security.CallerScope;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Work Order CRUD + status lifecycle.
 *
 * Row-level access (mirrors CustomerService/SiteService, extended with a technician scope):
 * <ul>
 *   <li>MANAGER, DISPATCHER — unrestricted.</li>
 *   <li>TECHNICIAN — can only see/act on a work order currently assigned to them;
 *       anything else 404s exactly like a nonexistent id (see {@link #findAccessible}).</li>
 *   <li>CUSTOMER — can only see their own work orders (own customer id); same 404 rule.
 *       A CUSTOMER cannot create, update, delete, or transition status here — creating a
 *       work order as a customer is the existing {@code POST /api/customer/work-orders}
 *       (CustomerPortalService), which is unchanged.</li>
 * </ul>
 *
 * Status transitions are validated against {@link #VALID_TRANSITIONS} and, for each
 * target status, against {@link #allowedRoles}; every successful transition writes one
 * {@link WorkOrderStatusHistory} row. Nothing else in this class writes history — a plain
 * field update (title/description/priority) or a rejected transition never does.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final Set<String> SORT_FIELDS =
            Set.of("id", "code", "title", "priority", "status", "createdAt", "updatedAt", "slaDueAt");

    /** Allowed target statuses from each current status. Empty set = terminal state. */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> VALID_TRANSITIONS = new EnumMap<>(WorkOrderStatus.class);
    static {
        VALID_TRANSITIONS.put(WorkOrderStatus.NEW, EnumSet.of(WorkOrderStatus.ASSIGNED, WorkOrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(WorkOrderStatus.ASSIGNED, EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD, WorkOrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(WorkOrderStatus.IN_PROGRESS, EnumSet.of(WorkOrderStatus.ON_HOLD, WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(WorkOrderStatus.ON_HOLD, EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(WorkOrderStatus.COMPLETED, EnumSet.of(WorkOrderStatus.CLOSED));
        VALID_TRANSITIONS.put(WorkOrderStatus.CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
        VALID_TRANSITIONS.put(WorkOrderStatus.CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderStatusHistoryRepository historyRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final SlaService slaService;

    // ---- Create ----

    @Transactional
    public WorkOrderResponse create(CustomUserDetails principal, WorkOrderRequest request) {
        Site site = siteRepository.findById(request.getSiteId())
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));

        WorkOrder wo = WorkOrder.builder()
                .title(request.getTitle().trim())
                .description(blankToNull(request.getDescription()))
                .customer(site.getCustomer())
                .site(site)
                .priority(request.getPriority())
                .status(WorkOrderStatus.NEW)
                .build();
        slaService.initialize(wo);

        wo = workOrderRepository.save(wo);
        // Code depends on the generated id, so it's assigned in a second write —
        // same pattern as CustomerPortalService.raiseServiceRequest.
        wo.setCode("WO-" + (1000 + wo.getId()));
        wo = workOrderRepository.save(wo);

        historyRepository.save(WorkOrderStatusHistory.builder()
                .workOrder(wo)
                .fromStatus(null)
                .toStatus(WorkOrderStatus.NEW)
                .note("Work order created")
                .changedByEmail(principal.getUsername())
                .changedByRole(principal.getUser().getRole().name())
                .build());

        return toResponse(wo);
    }

    // ---- Read ----

    @Transactional(readOnly = true)
    public PageResponse<WorkOrderResponse> search(CustomUserDetails principal, Long customerId, Long siteId,
                                                   Long technicianId, WorkOrderStatus status, Priority priority,
                                                   String search, int page, int size, String sortBy, String direction) {
        Pageable pageable = PagingSupport.pageable(page, size, sortBy, direction, SORT_FIELDS, "createdAt");

        Long customerScope = CallerScope.customerIdOrNull(principal);
        Long technicianScope = CallerScope.technicianIdOrNull(principal);

        Long effectiveCustomerId = customerId;
        if (customerScope != null) {
            if (customerId != null && !customerScope.equals(customerId)) {
                // Asking about another customer's work orders — same 404 as SiteService.search,
                // existence of the other customer's data is never revealed.
                throw new EntityNotFoundException("Customer not found");
            }
            effectiveCustomerId = customerScope;
        }

        // A TECHNICIAN only ever sees work orders assigned to them; any technicianId
        // filter they pass is ignored in favor of their own id, not honored as a query.
        Long effectiveTechnicianId = technicianScope != null ? technicianScope : technicianId;

        final Long filterCustomerId = effectiveCustomerId;
        final Long filterSiteId = siteId;
        final Long filterTechnicianId = effectiveTechnicianId;
        final String pattern = PagingSupport.likePattern(search);

        Specification<WorkOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filterCustomerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), filterCustomerId));
            }
            if (filterSiteId != null) {
                predicates.add(cb.equal(root.get("site").get("id"), filterSiteId));
            }
            if (filterTechnicianId != null) {
                predicates.add(cb.equal(root.get("assignedTechnician").get("id"), filterTechnicianId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("code")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<WorkOrder> result = workOrderRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public WorkOrderDetailResponse get(CustomUserDetails principal, Long id) {
        WorkOrder wo = findAccessible(principal, id);
        List<StatusHistoryResponse> history = historyRepository.findByWorkOrderIdOrderByChangedAtAsc(wo.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
        return toDetailResponse(wo, history);
    }

    // ---- Update (fields only — no status change here) ----

    @Transactional
    public WorkOrderResponse update(CustomUserDetails principal, Long id, WorkOrderRequest request) {
        // Staff-only by SecurityConfig; findAccessible still applies caller scoping
        // defensively (a TECHNICIAN/CUSTOMER can never reach this method via the API,
        // but the service does not rely solely on the controller-level gate).
        WorkOrder wo = findAccessible(principal, id);
        if (wo.getStatus() == WorkOrderStatus.CLOSED || wo.getStatus() == WorkOrderStatus.CANCELLED) {
            throw new ConflictException("Work order is " + wo.getStatus() + " and can no longer be edited");
        }
        // Deliberately does not touch wo.site / wo.customer: fixed at creation, like Site's customer.
        wo.setTitle(request.getTitle().trim());
        wo.setDescription(blankToNull(request.getDescription()));
        Priority previousPriority = wo.getPriority();
        wo.setPriority(request.getPriority());
        if (previousPriority != request.getPriority()) {
            slaService.onPriorityChange(wo, slaService.now());
        }
        return toResponse(workOrderRepository.save(wo));
    }

    // ---- Delete ----

    @Transactional
    public void delete(CustomUserDetails principal, Long id) {
        WorkOrder wo = findAccessible(principal, id);
        if (wo.getStatus() != WorkOrderStatus.NEW) {
            throw new ConflictException("Only a work order still in NEW status can be deleted");
        }
        historyRepository.deleteByWorkOrderId(wo.getId());
        workOrderRepository.delete(wo);
    }

    // ---- Status transition ----

    @Transactional
    public WorkOrderDetailResponse transitionStatus(CustomUserDetails principal, Long id,
                                                      WorkOrderStatusUpdateRequest request) {
        WorkOrder wo = findAccessible(principal, id);
        WorkOrderStatus from = wo.getStatus();
        WorkOrderStatus to = request.getStatus();

        Set<WorkOrderStatus> allowedTargets = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new ConflictException("Cannot transition from " + from + " to " + to);
        }

        Role callerRole = principal.getUser().getRole();
        if (!allowedRoles(to).contains(callerRole)) {
            throw new AccessDeniedException("Role " + callerRole + " may not set status " + to);
        }

        if (to == WorkOrderStatus.ASSIGNED) {
            if (request.getTechnicianId() == null) {
                throw new InvalidRequestException("technicianId is required when transitioning to ASSIGNED");
            }
            User technician = userRepository.findById(request.getTechnicianId())
                    .orElseThrow(() -> new EntityNotFoundException("Technician not found"));
            if (technician.getRole() != Role.TECHNICIAN) {
                throw new InvalidRequestException("User " + request.getTechnicianId() + " is not a TECHNICIAN");
            }
            wo.setAssignedTechnician(technician);
        }

        wo.setStatus(to);
        Instant now = slaService.now();
        if (to == WorkOrderStatus.COMPLETED) {
            wo.setCompletedAt(now);
        }
        slaService.applyTo(wo, now);
        wo = workOrderRepository.save(wo);

        historyRepository.save(WorkOrderStatusHistory.builder()
                .workOrder(wo)
                .fromStatus(from)
                .toStatus(to)
                .note(blankToNull(request.getNote()))
                .changedByEmail(principal.getUsername())
                .changedByRole(callerRole.name())
                .build());

        List<StatusHistoryResponse> history = historyRepository.findByWorkOrderIdOrderByChangedAtAsc(wo.getId())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
        return toDetailResponse(wo, history);
    }

    /** Which roles may move a work order TO the given status (regardless of the from-status). */
    private static Set<Role> allowedRoles(WorkOrderStatus target) {
        return switch (target) {
            case ASSIGNED, CLOSED, CANCELLED -> EnumSet.of(Role.MANAGER, Role.DISPATCHER);
            case IN_PROGRESS, ON_HOLD, COMPLETED -> EnumSet.of(Role.MANAGER, Role.DISPATCHER, Role.TECHNICIAN);
            case NEW -> EnumSet.noneOf(Role.class); // never a transition target
        };
    }

    // ---- Shared scoping / mapping ----

    /**
     * 404 (not 403) when a CUSTOMER asks about another customer's work order, or a
     * TECHNICIAN asks about one not assigned to them — existence is never revealed,
     * matching SiteService's findAccessible.
     */
    WorkOrder findAccessible(CustomUserDetails principal, Long id) {
        WorkOrder wo = workOrderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Work order not found"));

        Long customerScope = CallerScope.customerIdOrNull(principal);
        if (customerScope != null && !customerScope.equals(wo.getCustomer().getId())) {
            throw new EntityNotFoundException("Work order not found");
        }

        Long technicianScope = CallerScope.technicianIdOrNull(principal);
        if (technicianScope != null) {
            Long assignedId = wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getId() : null;
            if (!technicianScope.equals(assignedId)) {
                throw new EntityNotFoundException("Work order not found");
            }
        }
        return wo;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private StatusHistoryResponse toHistoryResponse(WorkOrderStatusHistory h) {
        return StatusHistoryResponse.builder()
                .fromStatus(h.getFromStatus() != null ? h.getFromStatus().name() : null)
                .toStatus(h.getToStatus().name())
                .note(h.getNote())
                .changedByEmail(h.getChangedByEmail())
                .changedByRole(h.getChangedByRole())
                .changedAt(h.getChangedAt())
                .build();
    }

    private WorkOrderResponse toResponse(WorkOrder wo) {
        return WorkOrderResponse.builder()
                .id(wo.getId())
                .code(wo.getCode())
                .title(wo.getTitle())
                .customerId(wo.getCustomer().getId())
                .customerName(wo.getCustomer().getCompanyName())
                .siteId(wo.getSite().getId())
                .siteName(wo.getSite().getName())
                .priority(wo.getPriority().name())
                .status(wo.getStatus().name())
                .assignedTechnicianId(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getId() : null)
                .assignedTechnician(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getFullName() : null)
                .slaDueAt(wo.getSlaDueAt())
                .slaStatus(slaService.currentStatusName(wo))
                .createdAt(wo.getCreatedAt())
                .build();
    }

    private WorkOrderDetailResponse toDetailResponse(WorkOrder wo, List<StatusHistoryResponse> history) {
        return WorkOrderDetailResponse.builder()
                .id(wo.getId())
                .code(wo.getCode())
                .title(wo.getTitle())
                .description(wo.getDescription())
                .customerId(wo.getCustomer().getId())
                .customerName(wo.getCustomer().getCompanyName())
                .siteId(wo.getSite().getId())
                .siteName(wo.getSite().getName())
                .priority(wo.getPriority().name())
                .status(wo.getStatus().name())
                .assignedTechnicianId(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getId() : null)
                .assignedTechnician(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getFullName() : null)
                .slaDueAt(wo.getSlaDueAt())
                .slaStatus(slaService.currentStatusName(wo))
                .createdAt(wo.getCreatedAt())
                .updatedAt(wo.getUpdatedAt())
                .history(history)
                .build();
    }
}
