package dev.shipping.shipments.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Warehouses;
import jakarta.transaction.Transactional;

public interface CustomerWarehousesRepository extends JpaRepository<CustomerWarehouses, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase

	@Query("SELECT w FROM Warehouses w WHERE w.warehouseId IN "
			+ "(SELECT cw.warehouseId FROM CustomerWarehouses cw WHERE cw.customerId = :customerId)")	
	List<Warehouses> findAllocatedWarehousesByCustomerId(@Param("customerId") String customerId);
	
	@Query("SELECT cw.warehouseId FROM CustomerWarehouses cw WHERE cw.customerId = :customerId")	
	List<String> findAllWarehousesByCustomerId(@Param("customerId") String customerId);

	@Modifying // ← tells JPA this is not a SELECT
	@Transactional // ← required for any write operation
	@Query("DELETE FROM CustomerWarehouses cw WHERE cw.customerId = :customerId")
	void deleteAllWarehousesByCustomerId(@Param("customerId") String customerId);

	@Query("SELECT w FROM Warehouses w WHERE w.warehouseId NOT IN "
			+ "(SELECT cw.warehouseId FROM CustomerWarehouses cw WHERE cw.customerId = :customerId)")
	List<Warehouses> findWarehousesNotAllocatedToCustomer(@Param("customerId") String customerId);

	@Modifying
    @Transactional
    @Query("DELETE FROM CustomerWarehouses cw WHERE cw.customerId = :customerId AND cw.warehouseId = :warehouseId")
    void deleteByCustomerIdAndWarehouseId(@Param("customerId") String customerId, 
                                            @Param("warehouseId") String warehouseId);
}
