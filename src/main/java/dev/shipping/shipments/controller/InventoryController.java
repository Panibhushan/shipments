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
import dev.shipping.shipments.model.Warehouses;
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
		model.addAttribute("itemUomsList", itemUomsList);
		model.addAttribute("filterApplied", false);
		return "show-all-inventory";
	}

	// Adding this addittional method, just incase I missed to update the URL from
	// goToAddOrUpdateInventoryPage to addOrUpdateInventoryPage in any pages, this
	// will route
	// correctly instead of giving error
	@GetMapping("/inventory/goToAddOrUpdateInventoryPage")
	public String goToAddOrUpdateInventoryPage(Model model) {
		return "redirect:/inventory/addOrUpdateInventoryPage";
	}

	@GetMapping("/inventory/addOrUpdateInventoryPage")
	public String addOrUpdateInventoryPage(Model model) {
		model.addAttribute("inventory", new Inventory());
		model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "addOrUpdateInventory"); // this is show which dropdown is active in the navbar
		model.addAttribute("itemUomsList", itemUomsList);
		return "create-or-update-inventory";
	}

	@PostMapping("/inventory/addOrUpdateInventory")
	public String saveInventory(@ModelAttribute Inventory inventory, @RequestParam String adjustmentType,
			@RequestParam String inventoryCreationPageType, RedirectAttributes redirectAttributes, Model model) {

		String itemId = inventory.getItemId();
		String customerId = inventory.getCustomerId();
		String itemUom = inventory.getItemUom();
		String warehouse_Id = inventory.getWarehouseId();
		String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;
		String itemCustomerUomWarehouseId = itemId + "_" + customerId + "_" + itemUom + "_" + warehouse_Id;
		int quantity = inventory.getQuantity();
		System.out.println("/inventory/addOrUpdateInventory::saveInventory():: itemCustomerUomWarehouseId: "
				+ itemCustomerUomWarehouseId + " -- quantity: " + quantity + " -- adjustmentType: " + adjustmentType);

		String result = inventoryService.createOrUpdateInventory(inventory, itemCustomerUomId,
				itemCustomerUomWarehouseId, quantity, adjustmentType);

		if (result.equals("ITEM_NOT_FOUND")) {
			redirectAttributes.addFlashAttribute("msg", "This combination doesnt exist!!\nItem: " + itemId
					+ ", Customer: " + customerId + ", UOM: " + itemUom);
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
		} else {
			redirectAttributes.addFlashAttribute("msg",
					"Inventory has been updated !! &nbsp;&nbsp;&nbsp;&nbsp;<a style='color: #3474eb;' href='/inventory/showInventoryDetails/"
							+ itemCustomerUomWarehouseId + "'>View Details</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
		}

		redirectAttributes.addFlashAttribute("itemUomsList", itemUomsList);
		model.addAttribute("activePage", "addOrUpdateInventory");

		if (inventoryCreationPageType.equals("CREATE_INVENTORY_FROM_CREATE_OR_UPDATE_INVENTORY_PAGE")) {
			return "redirect:/inventory/addOrUpdateInventoryPage";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Inventory has been updated !! ");
			return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
		}

	}

	@GetMapping("/inventory/showInventoryByFilters")
	public String showInventoryByFilters(@RequestParam(required = false, defaultValue = "ALL") String itemId,
			@RequestParam(required = false) String itemUom, @RequestParam(required = false) String customerId,
			@RequestParam(value = "warehouseSelect", required = false) String warehouseId,
			RedirectAttributes redirectAttributes, Model model) {

		System.out.println("/inventory/showInventoryByFilters/{itemId}/{itemUom}/{customerId}/{warehouseId}: " + itemId
				+ " / " + itemUom + " / " + customerId + " / " + warehouseId);

		return populateInventoryModel(itemId, itemUom, customerId, warehouseId, model);

	}

	// Helper method thatpopulates the model, returns the view name for showing inventory by using filters
	// using the same functionality to view the inventory from viewOrEditItem page
	private String populateInventoryModel(String itemId, String itemUom, String customerId, String warehouseId,
			Model model) {
		model.addAttribute("inventory", inventoryService.getInventoryDetails(customerId, warehouseId, itemId, itemUom));
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedItemUom", itemUom);
		model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("itemUomsList", itemUomsList);

		if (customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		model.addAttribute("filterApplied", true);

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

		if (inventory.isEmpty()) {
			redirectAttributes.addFlashAttribute("msg",
					"Requested inventory combination doesnt exist or cannot be created!!");
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
			return "redirect:/inventory/";
		}

		model.addAttribute("inventory", inventory.get());
		return "edit-inventory";
	}

	@PostMapping("/inventory/updateInventory/{itemCustomerUomWarehouseId}")
	public String updateInventory(@PathVariable String itemCustomerUomWarehouseId, @RequestParam String adjustmentType,
			@RequestParam int quantity, @RequestParam String itemId, @RequestParam String customerId,
			@RequestParam String warehouseId, @RequestParam String itemUom, RedirectAttributes redirectAttributes,
			Model model) {
		System.out.println("/inventory/updateInventory: " + itemCustomerUomWarehouseId + " -- adjustmentType: "
				+ adjustmentType + " -- Qty: " + quantity + " -- WarehouseId: " + warehouseId);

		String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;

		inventoryService.createOrUpdateInventory(new Inventory(), itemCustomerUomId, itemCustomerUomWarehouseId,
				quantity, adjustmentType);

		Optional<Inventory> inventory = inventoryService
				.getInventoryByItemCustomerUomWarehouseId(itemCustomerUomWarehouseId);
		model.addAttribute("inventory", inventory.get());
		redirectAttributes.addFlashAttribute("bgColor", "#d1fae5;");
		redirectAttributes.addFlashAttribute("textColor", "#45484d");
		redirectAttributes.addFlashAttribute("msg", "Inventory has been updated !!");
		return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
	}

	// This is to add inventory from viewOrEditItem (yes, viewOrEditItem not viewOrEditInventory)
	// page
	@GetMapping("/inventory/addInventory/{itemCustomerUomId}")
	public String addInventoryFromItemsPage(@PathVariable String itemCustomerUomId,
			RedirectAttributes redirectAttributes, Model model) {
		System.out.println("/inventory/addInventoryFromItemsPage():: itemCustomerUomId: " + itemCustomerUomId);

		String[] parts = itemCustomerUomId.split("_");

		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		Optional<Items> item = itemsService.getItemById(itemCustomerUomId);

		if (item.isEmpty()) {

			model.addAttribute("msg", "Inventory could not be added because below combination doesnt exist!!\n\nItem: "
					+ itemId + "\nCustomer: " + customerId + "\nUOM: " + itemUom);
			model.addAttribute("bgColor", "#ffffff");
			model.addAttribute("textColor", "#d95f6c");
			return "custom-error";
		}

		model.addAttribute("item", item.get());

		List<Warehouses> warehouses = shipmentsService.getWarehousesByCustomer(customerId);
		model.addAttribute("warehouses", warehouses);

		return "add-inventory";

	}

	// This is to view inventory from viewOrEditItem (yes, viewOrEditItem not viewOrEditInventory) page
	@GetMapping("/inventory/viewInventory/{itemCustomerUomId}")
	public String viewInventoryFromItemsPage(@PathVariable String itemCustomerUomId,
			RedirectAttributes redirectAttributes, Model model) {
		System.out.println("/inventory/viewInventory():: itemCustomerUomId: " + itemCustomerUomId);

		String[] parts = itemCustomerUomId.split("_");

		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		// Calling the method with item & customer details, and setting warehouses as
		// "ALL" to get inventory from all warehouses
		return populateInventoryModel(itemId, itemUom, customerId, "ALL", model);
	}

}
