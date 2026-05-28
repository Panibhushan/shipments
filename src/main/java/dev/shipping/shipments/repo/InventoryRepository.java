
package dev.shipping.shipments.repo;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

	@Query("SELECT itm FROM Items itm WHERE itm.customerId = :customerId")
	List<Items> findItemsByCustomer(@Param("customerId") String customerId);

	@Query("SELECT inv FROM Inventory inv WHERE inv.quantity > 0")
	List<Inventory> getValidInventory();

}
