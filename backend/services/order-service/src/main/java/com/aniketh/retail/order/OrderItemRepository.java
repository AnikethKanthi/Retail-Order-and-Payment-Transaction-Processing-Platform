package com.aniketh.retail.order;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

interface OrderItemRepository extends Repository<OrderItem, UUID> {

    List<OrderItem> findByOrder_IdOrderByLineNumberAsc(UUID orderId);
}
