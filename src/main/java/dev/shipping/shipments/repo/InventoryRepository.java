
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

		
	@Query("SELECT inv.quantity from Inventory inv WHERE inv.customerId= :customerId AND inv.itemId= :itemId AND inv.itemUom= :itemUom AND inv.warehouseId= :warehouseId")
	int getInventoryDetailsToVerifyAvailabilityBeforeUpdatingShipmentStatus(String customerId, String itemId,
			String itemUom, String warehouseId);



}
