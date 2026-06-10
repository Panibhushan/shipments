package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Warehouses;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehousesRepository extends JpaRepository<Warehouses, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND,
	// OR, ORDER BY, GROUP BY in uppercase

	@Query("SELECT w FROM Warehouses w WHERE w.warehouseStatus = :status")
	List<Warehouses> findByWarehousesByStatusActive(@Param("status") String status);

	@Query("SELECT inv FROM Inventory inv WHERE inv.warehouseId = :warehouseId")
	List<Inventory> getInventoryByWarehouseId(@Param("warehouseId") String warehouseId);

	@Query("SELECT c FROM Customers c WHERE c.customerId IN ( SELECT cw.customerId FROM CustomerWarehouses cw WHERE cw.warehouseId = :warehouseId)")
	List<Customers> getCustomersByWarehouseId(@Param("warehouseId") String getCustomersByWarehouseId);

	@Query("SELECT w FROM Warehouses w WHERE w.warehouseId = :warehouseId AND w.warehouseStatus = 'Active'")
	Warehouses findIfWarehouseIsActive(String warehouseId);

}