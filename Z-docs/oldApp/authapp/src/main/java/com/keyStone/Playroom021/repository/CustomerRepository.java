package com.keyStone.Playroom021.repository;

import com.keyStone.Playroom021.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
