
package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

	// JPA is case-sesitive so use the reserved words like SELECT, FROM, WHERE, AND, OR, ORDER BY, GROUP BY in uppercase

	@Query("SELECT itm FROM Items itm WHERE itm.customerId = :customerId")
	List<Items> findItemsByCustomer(@Param("customerId") String customerId);

	@Query("SELECT inv FROM Inventory inv WHERE inv.quantity > 0")
	List<Inventory> getValidInventory();

	@Query("SELECT inv FROM Inventory inv WHERE inv.customerId = :customerId AND inv.itemId = :itemId AND inv.itemUom = :itemUom"
			+ " AND inv.warehouseId IN (SELECT cw.warehouseId FROM CustomerWarehouses cw WHERE cw.customerId = :customerId )")
	List<Inventory> getInventoryDetailsToVerifyAvailability(String customerId, String itemId, String itemUom);

	@Modifying // ← tells JPA this is not a SELECT
	@Transactional // ← required for any write operation
	@Query("UPDATE Inventory inv SET inv.allocatedQuantity = inv.allocatedQuantity + :quantity WHERE inv.itemCustomerUomWarehouseId = :itemCustomerUomWarehouseId")
	void updateInventoryAllocatedQuantity(String itemCustomerUomWarehouseId, int quantity);

	List<Inventory> findByCustomerIdAndItemIdInAndItemUomIn(String customerId, List<String> itemIds,
			List<String> itemUoms);

	// COALESCE(arg1, arg2, ... argn) returns the first non-null value from the given arguments
	// COALESCE(inv.quantity,NULL,  0, 2) -- this will check for value of inv.quantity if its non-null then returns that, else it will go to next arg i.e., NULL, so it goes to next arg i.e. 0 and returns that.
	@Query("SELECT COALESCE(inv.quantity, 0) from Inventory inv WHERE inv.customerId= :customerId AND inv.itemId= :itemId AND inv.itemUom= :itemUom AND inv.warehouseId= :warehouseId")
	Optional<Integer> getInventoryDetailsToVerifyAvailabilityBeforeUpdatingShipmentStatus(String customerId, String itemId,
			String itemUom, String warehouseId);



}
