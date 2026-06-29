package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemsRepository extends JpaRepository<Items, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase
 
	@Query("SELECT i FROM Items i WHERE i.customerId = :customerId AND i.itemStatus = 'Active'")	
	List<Items> findItemsByCustomer(@Param("customerId") String customerId);

	@Query("SELECT i FROM Items i WHERE i.itemCustomerUomId = :itemCustomerUomId AND i.itemStatus = 'Active'") 
	Optional<Items> findIfItemExistsAndActive(String itemCustomerUomId);
		
}
