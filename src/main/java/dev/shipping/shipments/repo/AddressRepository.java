package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Warehouses;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, String> {
	
	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase
	/*
	 * @Query("SELECT w FROM Warehouses w WHERE w.warehouseStatus = :status")
	 * List<Warehouses> findByWarehousesByStatusActive(@Param("status") String
	 * status);
	 */
	 
}