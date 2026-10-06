package com.keyStone.Playroom021.service;

import com.keyStone.Playroom021.dto.PageResponse;
import com.keyStone.Playroom021.dto.PartRequest;
import com.keyStone.Playroom021.dto.PartResponse;
import com.keyStone.Playroom021.entity.Part;
import com.keyStone.Playroom021.exception.ConflictException;
import com.keyStone.Playroom021.repository.PartRepository;
import com.keyStone.Playroom021.repository.PartUsageRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parts inventory CRUD. Role gating is in SecurityConfig (MANAGER/DISPATCHER write,
 * MANAGER delete, MANAGER/DISPATCHER/TECHNICIAN read). Stock changes caused by work
 * order usage never go through this class's update path - see PartUsageService.
 */
@Service
@RequiredArgsConstructor
public class PartService {

    private static final Set<String> SORT_FIELDS =
            Set.of("id", "sku", "name", "quantityOnHand", "unitCost", "createdAt", "updatedAt");

    private final PartRepository partRepository;
    private final PartUsageRepository partUsageRepository;

    @Transactional
    public PartResponse create(PartRequest request) {
        String sku = normalizeSku(request.getSku());
        if (partRepository.existsBySku(sku)) {
            throw new ConflictException("A part with SKU " + sku + " already exists");
        }
        Part part = Part.builder()
                .sku(sku)
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .unit(request.getUnit().trim())
                .quantityOnHand(request.getQuantityOnHand())
                .reorderLevel(request.getReorderLevel())
                .unitCost(request.getUnitCost())
                .build();
        try {
            return toResponse(partRepository.saveAndFlush(part));
        } catch (DataIntegrityViolationException e) {
            // Lost a race against another create with the same SKU (unique constraint).
            throw new ConflictException("A part with SKU " + sku + " already exists");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PartResponse> search(String search, Boolean lowStock, int page, int size,
                                              String sortBy, String direction) {
        Pageable pageable = PagingSupport.pageable(page, size, sortBy, direction, SORT_FIELDS, "name");
        final String pattern = PagingSupport.likePattern(search);

        Specification<Part> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("sku")), pattern, '\\')));
            }
            if (Boolean.TRUE.equals(lowStock)) {
                predicates.add(cb.le(root.<Integer>get("quantityOnHand"), root.<Integer>get("reorderLevel")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Part> result = partRepository.findAll(spec, pageable);
        return PageResponse.of(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PartResponse get(Long id) {
        return toResponse(partRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Part not found")));
    }

    @Transactional
    public PartResponse update(Long id, PartRequest request) {
        // Row-locked so an absolute quantity write cannot interleave with a usage decrement.
        Part part = partRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Part not found"));
        String sku = normalizeSku(request.getSku());
        if (partRepository.existsBySkuAndIdNot(sku, id)) {
            throw new ConflictException("A part with SKU " + sku + " already exists");
        }
        part.setSku(sku);
        part.setName(request.getName().trim());
        part.setDescription(blankToNull(request.getDescription()));
        part.setUnit(request.getUnit().trim());
        part.setQuantityOnHand(request.getQuantityOnHand());
        part.setReorderLevel(request.getReorderLevel());
        part.setUnitCost(request.getUnitCost());
        try {
            return toResponse(partRepository.saveAndFlush(part));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A part with SKU " + sku + " already exists");
        }
    }

    @Transactional
    public void delete(Long id) {
        Part part = partRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Part not found"));
        if (partUsageRepository.existsByPartId(id)) {
            throw new ConflictException("Part has been used on work orders and cannot be deleted");
        }
        partRepository.delete(part);
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    PartResponse toResponse(Part p) {
        return PartResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .unit(p.getUnit())
                .quantityOnHand(p.getQuantityOnHand())
                .reorderLevel(p.getReorderLevel())
                .lowStock(p.getQuantityOnHand() <= p.getReorderLevel())
                .unitCost(p.getUnitCost())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
