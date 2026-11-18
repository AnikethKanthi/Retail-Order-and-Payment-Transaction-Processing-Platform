package com.aniketh.retail.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM order_service.customers
                WHERE id = :customerId AND status = 'ACTIVE'
            )
            """, nativeQuery = true)
    boolean hasActiveCustomer(UUID customerId);
}
