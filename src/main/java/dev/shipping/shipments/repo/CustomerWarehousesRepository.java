package dev.shipping.shipments.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Warehouses;

public interface CustomerWarehousesRepository extends JpaRepository<CustomerWarehouses, String> {

	  @Query("SELECT cw FROM CustomerWarehouses cw WHERE cw.customerId = :customerId")
	  List<CustomerWarehouses> findWarehousesByCustomerId(@Param("customerId") String
			  customerId);
}
