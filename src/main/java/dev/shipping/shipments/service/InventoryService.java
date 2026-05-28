package dev.shipping.shipments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
 
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.InventoryRepository;
import dev.shipping.shipments.repo.ItemsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class InventoryService {

	
	@Autowired
    private EntityManager entityManager;
	
	private final InventoryRepository inventoryRepo;

	private final ItemsRepository itemsRepo;
	private final CustomersRepository customersRepo;

	// List<String> itemUomsList = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	public InventoryService(InventoryRepository inventoryRepo, ItemsRepository itemsRepo, CustomersRepository customersRepo) {
			this.inventoryRepo = inventoryRepo;
			this.itemsRepo = itemsRepo;
			this.customersRepo = customersRepo;
		}
	
	public List<Inventory> getAllInventory() {
		return inventoryRepo.getValidInventory();
	}
	
	public Optional<Inventory> getInventoryByItemCustomerUomWarehouseId(String itemCustomerUomWarehouseId) {
		return inventoryRepo.findById(itemCustomerUomWarehouseId);
	}
	
	@Transactional
	public String createOrUpdateInventory(Inventory inventory, String itemCustomerUomId, String itemCustomerUomWarehouseId, int quantity, String adjustmentType) {

		if(itemsRepo.findById(itemCustomerUomId).isEmpty()) {
			return "ITEM_NOT_FOUND";
		}
		
	    Optional<Inventory> existing = inventoryRepo.findById(itemCustomerUomWarehouseId);

	    if (existing.isPresent()) {
	        // Update the FETCHED entity, not the passed-in one
	        Inventory toUpdate = existing.get();
	     // if the adjustmentType is increaseBy then add inventory else multiply it by -1 so that it becomes negative value and decrease the qty
	        quantity = adjustmentType.equals("increaseBy") ? quantity : quantity * -1;
	        toUpdate.setQuantity(toUpdate.getQuantity() + quantity); // only update quantity to existing qty
	        inventoryRepo.save(toUpdate);
	    } else {
	        // New record — save the passed-in entity
	        inventoryRepo.save(inventory);
	    }
	    
	    return "INVENTORY_UPDATED";
 
	}
	
	//Dynamically setting the conditions and running a custom query in service instead of calling individual methods in Repo
	@Transactional
	public List<Inventory> getInventoryDetails(String customerId, String warehouseId, String itemId) {

	    StringBuilder query = new StringBuilder("SELECT i FROM Inventory i");

	    // Dynamically build WHERE clause
	    List<String> conditions = new ArrayList<>();

	    if (!customerId.equals("ALL")) conditions.add("i.customerId = :customerId");
	    if (!warehouseId.equals("ALL")) conditions.add("i.warehouseId = :warehouseId");
	    if (!itemId.equals("ALL")) conditions.add("i.itemId = :itemId");

	    // Append WHERE + AND automatically
	    if (!conditions.isEmpty()) {
	        query.append(" WHERE ").append(String.join(" AND ", conditions));
	    }

	    // Create query
	    TypedQuery<Inventory> typedQuery = entityManager.createQuery(query.toString(), Inventory.class);

	    // Bind only non-null parameters
	    if (!customerId.equals("ALL")) typedQuery.setParameter("customerId", customerId);
	    if (!warehouseId.equals("ALL")) typedQuery.setParameter("warehouseId", warehouseId);
	    if (!itemId.equals("ALL")) typedQuery.setParameter("itemId", itemId);

	    List<Inventory> resultList =  typedQuery.getResultList();
	    
	    System.out.println("final Query: "+query.toString()+"\nresultList: "+resultList.toString());
	    
	    return resultList;
	}
	
}
