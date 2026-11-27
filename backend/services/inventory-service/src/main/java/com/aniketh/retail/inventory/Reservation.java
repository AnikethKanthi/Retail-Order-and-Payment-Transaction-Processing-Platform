package com.aniketh.retail.inventory;

import java.util.UUID;

record Reservation(UUID id, UUID inventoryId, String sku, String warehouseCode, long quantity, Status status) {
    enum Status {
        RESERVED, RELEASED, DEDUCTED
    }
}
