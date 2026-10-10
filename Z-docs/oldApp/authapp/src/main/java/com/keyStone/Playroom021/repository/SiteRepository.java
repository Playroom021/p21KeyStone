package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByCustomerIdOrderByNameAsc(Long customerId);

    // Ownership-scoped lookup: a site is only ever findable by a customer who owns it.
    Optional<Site> findByIdAndCustomerId(Long id, Long customerId);
}
