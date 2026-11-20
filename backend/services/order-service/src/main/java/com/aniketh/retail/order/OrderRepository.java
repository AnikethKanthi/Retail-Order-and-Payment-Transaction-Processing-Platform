package com.aniketh.retail.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findForStatusUpdate(UUID id);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM order_service.customers
                WHERE id = :customerId AND status = 'ACTIVE'
                FOR SHARE
            )
            """, nativeQuery = true)
    boolean hasActiveCustomer(UUID customerId);
}
