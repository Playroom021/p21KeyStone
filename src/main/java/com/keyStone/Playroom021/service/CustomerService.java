package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.CustomerRequest;
import com.keyStone.Playroom021.dto.CustomerResponse;
import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.entity.Customer;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.repository.CustomerRepository;
import com.keyStone.Playroom021.repository.SiteRepository;
import com.keyStone.Playroom021.repository.UserRepository;
import com.keyStone.Playroom021.repository.WorkOrderRepository;
import com.keyStone.Playroom021.security.CallerScope;
import com.keyStone.Playroom021.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Customer CRUD. Role gating (who may call which endpoint) lives in SecurityConfig;
 * this service adds the row-level rule: a CUSTOMER caller can only ever see their own
 * Customer, and asking for anyone else's returns the same 404 as a nonexistent id.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Set<String> SORT_FIELDS = Set.of("id", "companyName", "createdAt");

    private final CustomerRepository customerRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final WorkOrderRepository workOrderRepository;

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = Customer.builder()
                .companyName(request.getCompanyName().trim())
                .contactEmail(blankToNull(request.getContactEmail()))
                .build();
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String search, int page, int size,
                                                 String sortBy, String direction) {
        Pageable pageable = PagingSupport.pageable(page, size, sortBy, direction, SORT_FIELDS, "companyName");
        String pattern = PagingSupport.likePattern(search);

        Specification<Customer> spec = (root, query, cb) -> {
            if (pattern == null) {
                return cb.conjunction();
            }
            return cb.or(
                    cb.like(cb.lower(root.<String>get("companyName")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("contactEmail")), pattern, '\\'));
        };

        Page<Customer> result = customerRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(CustomUserDetails principal, Long id) {
        Long scope = CallerScope.customerIdOrNull(principal);
        if (scope != null && !scope.equals(id)) {
            throw new EntityNotFoundException("Customer not found");
        }
        return toResponse(findOrThrow(id));
    }

    /** The calling CUSTOMER's own Customer record. */
    @Transactional(readOnly = true)
    public CustomerResponse getOwn(CustomUserDetails principal) {
        Long scope = CallerScope.customerIdOrNull(principal);
        if (scope == null) {
            throw new EntityNotFoundException("Customer not found");
        }
        return toResponse(findOrThrow(scope));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = findOrThrow(id);
        customer.setCompanyName(request.getCompanyName().trim());
        customer.setContactEmail(blankToNull(request.getContactEmail()));
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findOrThrow(id);
        // Refuse instead of cascading: silently deleting sites, portal logins or work
        // orders along with a customer would destroy history nobody asked to delete.
        if (siteRepository.existsByCustomerId(id)) {
            throw new ConflictException("Customer still has sites; delete the sites first");
        }
        if (workOrderRepository.existsByCustomerId(id)) {
            throw new ConflictException("Customer has work orders and cannot be deleted");
        }
        if (userRepository.existsByCustomerId(id)) {
            throw new ConflictException("Customer has user accounts linked to it and cannot be deleted");
        }
        customerRepository.delete(customer);
    }

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .companyName(c.getCompanyName())
                .contactEmail(c.getContactEmail())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
