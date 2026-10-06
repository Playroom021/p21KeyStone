package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long>, JpaSpecificationExecutor<Site> {

    List<Site> findByCustomerIdOrderByNameAsc(Long customerId);

    // Ownership-scoped lookup: a site is only ever findable by a customer who owns it.
    Optional<Site> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByCustomerId(Long customerId);

    // Step 4: fetch each site's customer in the same query so the paged Site API
    // does not issue one extra query per row when it renders customer names.
    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Site> findAll(Specification<Site> spec, Pageable pageable);
}
