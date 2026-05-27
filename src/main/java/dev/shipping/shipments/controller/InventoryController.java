package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.InventoryService;
import dev.shipping.shipments.service.ItemsService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;

@Controller
public class InventoryController {

	private final InventoryService inventoryService;

	private final ItemsService itemsService;
	private final ShipmentsService shipmentsService;
	private final CustomersService customersService;
	private final WarehousesService warehousesService;

	List<String> itemUomsList = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	public InventoryController(InventoryService inventoryService, ItemsService itemsService,
			ShipmentsService shipmentsService, CustomersService customersService, WarehousesService warehousesService) {
		this.inventoryService = inventoryService;
		this.itemsService = itemsService;
		this.shipmentsService = shipmentsService;
		this.customersService = customersService;
		this.warehousesService = warehousesService;
	}

	@GetMapping("/inventory/")
	public String showAllItems(Model model) {
		model.addAttribute("inventory", inventoryService.getAllInventory());
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("selectedWarehouse", "ALL");
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("activePage", "allInventory"); // ← this is show which dropdown is active in the navbar
		return "show-all-inventory";
	}

	@GetMapping("/inventory/goToAddOrUpdateInventoryPage")
	public String addItemsPage(Model model) {
		model.addAttribute("inventory", new Inventory());
		model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "addOrUpdateInventory"); // ← this is show which dropdown is active in the
																	// navbar
		model.addAttribute("itemUomsList", itemUomsList);
		return "add-or-update-inventory";
	}

	@PostMapping("/inventory/addOrUpdateInventory")
	public String saveInventory(@ModelAttribute Inventory inventory, @RequestParam String adjustmentType,
			RedirectAttributes redirectAttributes, Model model) {

		String itemId = inventory.getItemId();
		String customerId = inventory.getCustomerId();
		String itemUom = inventory.getItemUom();
		String warehouse_Id = inventory.getWarehouseId();
		String itemCustomerUomWarehouseId = itemId + "_" + customerId + "_" + itemUom + "_" + warehouse_Id;
		int quantity = inventory.getQuantity();
		System.out.println("itemCustomerUomWarehouseId: " + itemCustomerUomWarehouseId + " -- quantity: " + quantity
				+ " -- adjustmentType: " + adjustmentType);

		inventoryService.createOrUpdateInventory(inventory, itemCustomerUomWarehouseId, quantity, adjustmentType);

		redirectAttributes.addFlashAttribute("itemUomsList", itemUomsList);
		model.addAttribute("activePage", "addOrUpdateInventory");
		return "redirect:/inventory/goToAddOrUpdateInventoryPage";
	}

	@PostMapping("/inventory/showInventoryByCustomerAndItemAndWarehouse/{customerId}/{warehouseId}/{itemId}")
	public String showShipmentsByCustomerAndItemAndWarehouse(@PathVariable String customerId,
			@PathVariable String itemId, @PathVariable String warehouseId, RedirectAttributes redirectAttributes,
			Model model) {

		System.out.println("/inventory/showInventoryByCustomerAndItemAndWarehouse/{customerId}/{warehouseIdId}/{item}: "
				+ customerId + " / " + warehouseId + " / " + itemId);

		model.addAttribute("inventory", inventoryService.getInventoryDetails(customerId, warehouseId, itemId));
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);
		model.addAttribute("customers", customersService.getAllCustomers());

		if (customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		return "show-all-inventory";

	}

	@GetMapping("/inventory/showInventoryDetails/{itemCustomerUomWarehouseId}")
	public String showItemDetails(@PathVariable String itemCustomerUomWarehouseId) {
		System.out.println("itemCustomerUomWarehouseId: " + itemCustomerUomWarehouseId);
		return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
	}

	@GetMapping("/inventory/viewOrEditInventory/{itemCustomerUomWarehouseId}")
	public String viewOrEditInventory(@PathVariable String itemCustomerUomWarehouseId, Model model,
			RedirectAttributes redirectAttributes) {
		System.out.println("itemCustomerUomWarehouseId: " + itemCustomerUomWarehouseId);

		Optional<Inventory> inventory = inventoryService
				.getInventoryByItemCustomerUomWarehouseId(itemCustomerUomWarehouseId);
		/*
		 * System.out.println("inventory: " + inventory + "\ninventory.get(): " +
		 * inventory.get());
		 */
		
		model.addAttribute("inventory", inventory.get());
		return "edit-inventory";
	}

	@PostMapping("/inventory/updateInventory/{itemCustomerUomWarehouseId}")
	public String updateInventory(@PathVariable String itemCustomerUomWarehouseId, @RequestParam String adjustmentType,
			@RequestParam int quantity, RedirectAttributes redirectAttributes, Model model) {
		System.out.println("/inventory/updateInventory: " + itemCustomerUomWarehouseId + " -- adjustmentType: "
				+ adjustmentType + " -- Qty: " + quantity);

		inventoryService.createOrUpdateInventory(new Inventory(), itemCustomerUomWarehouseId, quantity, adjustmentType);

		Optional<Inventory> inventory = inventoryService
				.getInventoryByItemCustomerUomWarehouseId(itemCustomerUomWarehouseId);
		model.addAttribute("inventory", inventory.get());
		redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
		redirectAttributes.addFlashAttribute("textColor", "#45484d");
		redirectAttributes.addFlashAttribute("msg", "Inventory has been updated !!");
		return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
	}

}
