package com.aniketh.retail.inventory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class InventoryRepository {
    private final JdbcClient jdbc;

    InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Stock> findStock(String sku, String warehouseCode) {
        return jdbc.sql("""
                SELECT id, sku, warehouse_code, on_hand_quantity, reserved_quantity, available_quantity
                FROM inventory_service.inventory WHERE sku = :sku AND warehouse_code = :warehouse
                """).param("sku", sku).param("warehouse", warehouseCode).query(Stock.class).optional();
    }

    boolean reserve(UUID inventoryId, long quantity) {
        return jdbc.sql("""
                UPDATE inventory_service.inventory
                SET reserved_quantity = reserved_quantity + :quantity,
                    version = version + 1, updated_at = GREATEST(updated_at, statement_timestamp())
                WHERE id = :id AND available_quantity >= :quantity
                """).param("id", inventoryId).param("quantity", quantity).update() == 1;
    }

    boolean finish(UUID inventoryId, long quantity, Reservation.Status status) {
        long deducted = status == Reservation.Status.DEDUCTED ? quantity : 0;
        return jdbc.sql("""
                UPDATE inventory_service.inventory
                SET reserved_quantity = reserved_quantity - :quantity,
                    on_hand_quantity = on_hand_quantity - :deducted,
                    version = version + 1, updated_at = GREATEST(updated_at, statement_timestamp())
                WHERE id = :id AND reserved_quantity >= :quantity
                """).param("id", inventoryId).param("quantity", quantity).param("deducted", deducted)
                .update() == 1;
    }

    void lockReservation(UUID id) {
        // Serialize attempts even before the reservation row exists.
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(80703, hashtext(:id))")
                .param("id", id.toString()).query(Integer.class).single();
    }

    Optional<Reservation> findReservation(UUID id) {
        return jdbc.sql("""
                SELECT r.id, r.inventory_id, i.sku, i.warehouse_code, r.quantity, r.status
                FROM inventory_service.reservations r
                JOIN inventory_service.inventory i ON i.id = r.inventory_id
                WHERE r.id = :id
                """).param("id", id).query(Reservation.class).optional();
    }

    void insert(Reservation reservation) {
        jdbc.sql("""
                INSERT INTO inventory_service.reservations (id, inventory_id, quantity)
                VALUES (:id, :inventoryId, :quantity)
                """).param("id", reservation.id()).param("inventoryId", reservation.inventoryId())
                .param("quantity", reservation.quantity()).update();
    }

    void finishReservation(UUID id, Reservation.Status status) {
        jdbc.sql("""
                UPDATE inventory_service.reservations
                SET status = :status, updated_at = GREATEST(updated_at, statement_timestamp())
                WHERE id = :id
                """).param("id", id).param("status", status.name()).update();
    }
}
