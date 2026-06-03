package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.ShipmentLines;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; 

public interface ShipmentLinesRepository extends JpaRepository<ShipmentLines, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase

 	@Query("SELECT sl FROM ShipmentLines sl WHERE sl.shipmentId = :shipmentId")	
	List<ShipmentLines> getShipmentLinesByShipmentId(String shipmentId);
 
}
