package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Shipments;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentsRepository extends JpaRepository<Shipments, String> {

 	@Query("SELECT s FROM Shipments s WHERE s.customerId = :customerId")	
	List<Shipments> findShipmentsByCustomer(@Param("customerId") String customerId);

 	@Query("SELECT s FROM Shipments s WHERE s.customerId = :customerId AND s.warehouseId= :warehouseId")
	List<Shipments> findShipmentsByCustomerAndWarehouse(String customerId, String warehouseId);
	
}
