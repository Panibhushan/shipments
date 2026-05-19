package dev.shipping.shipments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ItemsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;

@Service
public class ItemsService {

	private final ItemsRepository itemsRepo;
	private final CustomersRepository customersRepo;


	public ItemsService(ItemsRepository itemsRepo, CustomersRepository customersRepo) {
		this.itemsRepo = itemsRepo;
		this.customersRepo = customersRepo;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public boolean itemExists(String itemCustomerUomId) {
		System.out.println("ItemsService:: itemCustomerUomId: "+itemCustomerUomId);
		return itemsRepo.findById(itemCustomerUomId).isPresent();
	}
	
	public List<Items> getAllItems() {
		return itemsRepo.findAll();
	}

	@Transactional
	public void createItem(Items item) {
		itemsRepo.save(item);
	}
	
	public List<Customers> getActiveAndValidCustomers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return customersRepo.findByCustomerStatusAndValidUpto("Active", startOfToday);
	}

	public void populateEditItemModel(String itemId, Model model) {
		Items item= itemsRepo.findById(itemId)
				.orElseThrow(() -> new RuntimeException("Item not found: " + itemId));

		model.addAttribute("item", item);
		model.addAttribute("options", List.of("Active", "Disabled"));
 	}

	
	/*
	 * 
	 * public boolean warehouseExists(String warehouseId) { return
	 * warehousesRepo.findById(warehouseId).isPresent(); }
	 * 
	 *//**
		 * Populates all model attributes needed for the edit-warehouse page. Called
		 * only after confirming the warehouse exists.
		 */

	/*
	 * public void populateEditWarehouseModel(String warehouseId, Model model) {
	 * Warehouses warehouse = warehousesRepo.findById(warehouseId) .orElseThrow(()
	 * -> new RuntimeException("Warehouse not found: " + warehouseId));
	 * 
	 * model.addAttribute("warehouse", warehouse); model.addAttribute("options",
	 * List.of("Active", "Disabled")); model.addAttribute("selectedStatus",
	 * warehouse.getWarehouseStatus()); }
	 * 
	 * // ───────────────────────────────────────────── // VALIDATION //
	 * ─────────────────────────────────────────────
	 * 
	 *//**
		 * Validates fields when creating a new warehouse. Returns a list of error
		 * messages; empty list means no errors.
		 */

	public List<String> validateNewItem(Items item) {
		List<String> errors = new ArrayList<>();
		String itemId = item.getItemId();
		String itemUom = item.getItemUom();
		String itemDescription = item.getItemDescription();

		// Item ID checks
		if (itemId.matches(".*\\s.*")) {
			errors.add("Item ID cannot contain any whitespaces (spaces, tabs, next-line characters)!!");
		} else {
			if (itemId.length() < 3) {
				errors.add("Cannot use Item ID as  \"" + itemId + "\"\nItem ID must be atleast 3 characters long !!");
			} else if (itemId.length() > 5) {
				errors.add("Cannot use Item ID as \"" + itemId + "\"\nItem ID must be maximum 5 characters only !!");
			}
		}

		// Item UOM checks
		if (itemUom.trim().length() < 2) {
			errors.add("Cannot update Item UOM to \"" + itemUom + "\"\nItem UOM must be atleast 2 characters long !!");
		} else if (itemUom.trim().length() > 15) {
			errors.add("Cannot update Item UOM to \"" + itemUom + "\"\nItem UOM must be maximum 4 characters only !!");
		}

// Item Description checks 
		if (itemDescription.trim().length() < 10) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be atleast 10 characters long !!");
		} else if (itemDescription.trim().length() > 50) {
			errors.add("Cannot update Item Description to \"" + itemDescription
					+ "\"\nItem Description must be maximum 50 characters only !!");
		}

		return errors;
	}

	/**
	 * Validates fields when updating an existing warehouse. Returns a list of error
	 * messages; empty list means no errors.
	 *//*
		 * public List<String> validateWarehouseUpdate(String warehouseName, String
		 * warehouseAddress) { List<String> errors = new ArrayList<>();
		 * 
		 * // Warehouse Name checks if (warehouseName.trim().length() < 5) {
		 * errors.add("Cannot update Warehouse Name to \""
		 * +warehouseName+"\"\nWarehouse Name must be atleast 5 characters long !!"); }
		 * else if (warehouseName.trim().length() > 15) {
		 * errors.add("Cannot update Warehouse Name to \""
		 * +warehouseName+"\"\nWarehouse Name must be maximum 15 characters only !!"); }
		 * 
		 * // Warehouse Address checks if (warehouseAddress.trim().length() < 10) {
		 * errors.add("Cannot update Warehouse Address to \""
		 * +warehouseAddress+"\"\nWarehouse Address must be atleast 10 characters long !!"
		 * ); } else if (warehouseAddress.trim().length() > 50) {
		 * errors.add("Cannot update Warehouse Address to \""
		 * +warehouseAddress+"\"\nWarehouse Address must be maximum 50 characters only !!"
		 * ); }
		 * 
		 * return errors; }
		 * 
		 * // ───────────────────────────────────────────── // WRITE //
		 * ─────────────────────────────────────────────
		 * 
		 * @Transactional public void createWarehouse(Warehouses warehouse) {
		 * warehousesRepo.save(warehouse); }
		 * 
		 * @Transactional public void updateWarehouse(String warehouseId, String
		 * warehouseName, String warehouseStatus, String warehouseAddress) { Warehouses
		 * warehouse = warehousesRepo.findById(warehouseId) .orElseThrow(() -> new
		 * RuntimeException("Warehouse not found: " + warehouseId));
		 * 
		 * warehouse.setWarehouseName(warehouseName);
		 * warehouse.setWarehouseAddress(warehouseAddress);
		 * warehouse.setWarehouseStatus(warehouseStatus);
		 * 
		 * warehousesRepo.save(warehouse); }
		 * 
		 * @Transactional public void deleteWarehouse(String warehouseId) {
		 * warehousesRepo.deleteById(warehouseId); }
		 */

}