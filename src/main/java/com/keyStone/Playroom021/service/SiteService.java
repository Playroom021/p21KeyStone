package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.SiteDetailResponse;
import com.keyStone.Playroom021.dto.SiteRequest;
import com.keyStone.Playroom021.entity.Customer;
import com.keyStone.Playroom021.entity.Site;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.repository.CustomerRepository;
import com.keyStone.Playroom021.repository.SiteRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.security.CallerScope;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Site CRUD. A Site belongs to exactly one Customer, fixed at creation: the customer
 * comes from the URL path (or, for a CUSTOMER caller, is checked against their own
 * customer id) and can never be changed by an update. Row-level rule: a CUSTOMER
 * caller can only see or touch sites of their own customer; anything else is a 404,
 * identical to a nonexistent id.
 */
@Service
@RequiredArgsConstructor
public class SiteService {

    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "city", "createdAt");

    private final SiteRepository siteRepository;
    private final CustomerRepository customerRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional
    public SiteDetailResponse create(CustomUserDetails principal, Long customerId, SiteRequest request) {
        requireCustomerAccess(principal, customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        Site site = Site.builder()
                .customer(customer)
                .name(request.getName().trim())
                .addressLine(blankToNull(request.getAddressLine()))
                .city(blankToNull(request.getCity()))
                .build();
        return toResponse(siteRepository.save(site));
    }

    /**
     * Lists sites, optionally filtered to one customer and/or a search term (matches
     * name, address or city). For a CUSTOMER caller the result is always restricted to
     * their own customer.
     */
    @Transactional(readOnly = true)
    public PageResponse<SiteDetailResponse> search(CustomUserDetails principal, Long customerId, String search,
                                                   int page, int size, String sortBy, String direction) {
        Pageable pageable = PagingSupport.pageable(page, size, sortBy, direction, SORT_FIELDS, "name");

        Long scope = CallerScope.customerIdOrNull(principal);
        Long effectiveCustomerId = customerId;
        if (scope != null) {
            if (customerId != null && !scope.equals(customerId)) {
                throw new EntityNotFoundException("Customer not found");
            }
            effectiveCustomerId = scope;
        } else if (customerId != null && !customerRepository.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found");
        }

        final Long filterCustomerId = effectiveCustomerId;
        final String pattern = PagingSupport.likePattern(search);

        Specification<Site> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filterCustomerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), filterCustomerId));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("addressLine")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("city")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Site> result = siteRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SiteDetailResponse get(CustomUserDetails principal, Long id) {
        return toResponse(findAccessible(principal, id));
    }

    @Transactional
    public SiteDetailResponse update(CustomUserDetails principal, Long id, SiteRequest request) {
        Site site = findAccessible(principal, id);
        // Deliberately does not touch site.customer: a site cannot be moved to another customer.
        site.setName(request.getName().trim());
        site.setAddressLine(blankToNull(request.getAddressLine()));
        site.setCity(blankToNull(request.getCity()));
        return toResponse(siteRepository.save(site));
    }

    @Transactional
    public void delete(CustomUserDetails principal, Long id) {
        Site site = findAccessible(principal, id);
        if (workOrderRepository.existsBySiteId(id)) {
            throw new ConflictException("Site has work orders and cannot be deleted");
        }
        siteRepository.delete(site);
    }

    /** 404 (not 403) when a CUSTOMER asks about another customer, so existence is never revealed. */
    private void requireCustomerAccess(CustomUserDetails principal, Long customerId) {
        Long scope = CallerScope.customerIdOrNull(principal);
        if (scope != null && !scope.equals(customerId)) {
            throw new EntityNotFoundException("Customer not found");
        }
    }

    private Site findAccessible(CustomUserDetails principal, Long id) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Site not found"));
        Long scope = CallerScope.customerIdOrNull(principal);
        if (scope != null && !scope.equals(site.getCustomer().getId())) {
            throw new EntityNotFoundException("Site not found");
        }
        return site;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SiteDetailResponse toResponse(Site s) {
        return SiteDetailResponse.builder()
                .id(s.getId())
                .customerId(s.getCustomer().getId())
                .customerName(s.getCustomer().getCompanyName())
                .name(s.getName())
                .addressLine(s.getAddressLine())
                .city(s.getCity())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
