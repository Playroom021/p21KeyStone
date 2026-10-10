package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.*;
import com.keyStone.Playroom021.entity.*;
import com.keyStone.Playroom021.repository.SiteRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.repository.WorkOrderStatusHistoryRepository;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Every method here takes the acting user's Customer id from the authenticated
 * principal (never from a request parameter) and every repository call filters
 * by it. That's what makes "customer A can't see customer B's data" true by
 * construction rather than by a permission check someone could forget to add.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalService {

    private final SiteRepository siteRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderStatusHistoryRepository historyRepository;

    private Customer requireCustomer(CustomUserDetails principal) {
        Customer customer = principal.getUser().getCustomer();
        if (customer == null) {
            // Defensive: shouldn't happen for a LOCAL_CUSTOMER account, since
            // signup always creates one, but fail loudly rather than leak data.
            throw new IllegalStateException("This account has no linked customer profile");
        }
        return customer;
    }

    // ---- Sites ----

    public List<SiteResponse> listSites(CustomUserDetails principal) {
        Customer customer = requireCustomer(principal);
        return siteRepository.findByCustomerIdOrderByNameAsc(customer.getId()).stream()
                .map(this::toSiteResponse)
                .toList();
    }

    @Transactional
    public SiteResponse createSite(CustomUserDetails principal, SiteRequest request) {
        Customer customer = requireCustomer(principal);
        Site site = Site.builder()
                .customer(customer)
                .name(request.getName().trim())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .build();
        return toSiteResponse(siteRepository.save(site));
    }

    private SiteResponse toSiteResponse(Site site) {
        return SiteResponse.builder()
                .id(site.getId())
                .name(site.getName())
                .addressLine(site.getAddressLine())
                .city(site.getCity())
                .build();
    }

    // ---- Work orders ----

    public List<WorkOrderResponse> listWorkOrders(CustomUserDetails principal, WorkOrderStatus statusFilter) {
        Customer customer = requireCustomer(principal);
        List<WorkOrder> orders = statusFilter == null
                ? workOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId())
                : workOrderRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customer.getId(), statusFilter);
        return orders.stream().map(this::toWorkOrderResponse).toList();
    }

    public WorkOrderDetailResponse getWorkOrder(CustomUserDetails principal, Long workOrderId) {
        Customer customer = requireCustomer(principal);
        // findByIdAndCustomerId means a work order belonging to a different
        // customer simply doesn't match — it 404s exactly like a nonexistent id.
        WorkOrder wo = workOrderRepository.findByIdAndCustomerId(workOrderId, customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Work order not found"));

        List<StatusHistoryResponse> history = historyRepository.findByWorkOrderIdOrderByChangedAtAsc(wo.getId())
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .fromStatus(h.getFromStatus() != null ? h.getFromStatus().name() : null)
                        .toStatus(h.getToStatus().name())
                        .note(h.getNote())
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();

        return WorkOrderDetailResponse.builder()
                .id(wo.getId())
                .code(wo.getCode())
                .title(wo.getTitle())
                .description(wo.getDescription())
                .customerName(wo.getCustomer().getCompanyName())
                .siteName(wo.getSite().getName())
                .priority(wo.getPriority().name())
                .status(wo.getStatus().name())
                .assignedTechnician(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getFullName() : null)
                .slaDueAt(wo.getSlaDueAt())
                .createdAt(wo.getCreatedAt())
                .updatedAt(wo.getUpdatedAt())
                .history(history)
                .build();
    }

    @Transactional
    public WorkOrderResponse raiseServiceRequest(CustomUserDetails principal, WorkOrderRequest request) {
        Customer customer = requireCustomer(principal);

        // The site must belong to this same customer — prevents a customer
        // from filing a work order against another customer's site by guessing an id.
        Site site = siteRepository.findByIdAndCustomerId(request.getSiteId(), customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));

        WorkOrder wo = WorkOrder.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .customer(customer)
                .site(site)
                .priority(request.getPriority())
                .status(WorkOrderStatus.NEW)
                .slaDueAt(computeSlaDueAt(request.getPriority()))
                .build();

        wo = workOrderRepository.save(wo);
        // Code depends on the generated id, so it's assigned in a second write.
        wo.setCode("WO-" + (1000 + wo.getId()));
        wo = workOrderRepository.save(wo);

        historyRepository.save(WorkOrderStatusHistory.builder()
                .workOrder(wo)
                .fromStatus(null)
                .toStatus(WorkOrderStatus.NEW)
                .note("Service request raised by customer")
                .changedByEmail(principal.getUsername())
                .changedByRole(principal.getUser().getRole().name())
                .build());

        return toWorkOrderResponse(wo);
    }

    private Instant computeSlaDueAt(Priority priority) {
        Instant now = Instant.now();
        return switch (priority) {
            case CRITICAL -> now.plus(4, ChronoUnit.HOURS);
            case HIGH -> now.plus(24, ChronoUnit.HOURS);
            case MEDIUM -> now.plus(48, ChronoUnit.HOURS);
            case LOW -> now.plus(72, ChronoUnit.HOURS);
        };
    }

    private WorkOrderResponse toWorkOrderResponse(WorkOrder wo) {
        return WorkOrderResponse.builder()
                .id(wo.getId())
                .code(wo.getCode())
                .title(wo.getTitle())
                .customerName(wo.getCustomer().getCompanyName())
                .siteName(wo.getSite().getName())
                .priority(wo.getPriority().name())
                .status(wo.getStatus().name())
                .assignedTechnician(wo.getAssignedTechnician() != null ? wo.getAssignedTechnician().getFullName() : null)
                .slaDueAt(wo.getSlaDueAt())
                .createdAt(wo.getCreatedAt())
                .build();
    }
}
