package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.Part;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long>, JpaSpecificationExecutor<Part> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /** Row-locked read, used by PUT so an absolute quantity write cannot race a concurrent decrement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id = :id")
    Optional<Part> findByIdForUpdate(@Param("id") Long id);

    /**
     * Atomic conditional decrement. The {@code quantityOnHand >= :qty} guard lives in the
     * UPDATE itself, so two concurrent requests can never both succeed against the last
     * units: stock cannot go below zero. Returns the number of rows updated
     * (0 = insufficient stock or no such part).
     */
    @Modifying
    @Query("update Part p set p.quantityOnHand = p.quantityOnHand - :qty, p.updatedAt = :now "
            + "where p.id = :id and p.quantityOnHand >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty, @Param("now") Instant now);

    /** Atomic increment (used when a part usage is removed and its stock is returned). */
    @Modifying
    @Query("update Part p set p.quantityOnHand = p.quantityOnHand + :qty, p.updatedAt = :now where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty, @Param("now") Instant now);

    /** Fresh scalar read straight from the DB (bypasses the stale first-level cache after a bulk update). */
    @Query("select p.quantityOnHand from Part p where p.id = :id")
    Integer findQuantityOnHand(@Param("id") Long id);
}
