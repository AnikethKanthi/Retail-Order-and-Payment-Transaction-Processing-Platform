package com.aniketh.retail.inventory;

import java.util.UUID;

record Stock(UUID id, String sku, String warehouseCode, long onHandQuantity,
             long reservedQuantity, long availableQuantity) {
}
