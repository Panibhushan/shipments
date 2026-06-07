package dev.shipping.shipments.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.InventoryCheckResult;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.ShipmentLines;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.InventoryService;
import dev.shipping.shipments.service.ItemsService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;

/**
 * Handles all HTTP requests related to inventory.
 *
 * Responsibilities: - Parse and validate incoming HTTP parameters - Delegate
 * all business logic to InventoryService - Populate the view model and set
 * flash attributes for redirects
 *
 * This controller intentionally contains NO business logic. Validation,
 * persistence, and domain rules all live in InventoryService.
 */
@Controller
public class InventoryController {

	private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

	/**
	 * Valid units of measure for inventory items. Used to populate the UOM dropdown
	 * across inventory views.
	 */
	private static final List<String> ITEM_UOMS_LIST = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	private final InventoryService inventoryService;
	private final ItemsService itemsService;
	private final ShipmentsService shipmentsService;
	private final CustomersService customersService;
	private final WarehousesService warehousesService;

	public InventoryController(InventoryService inventoryService, ItemsService itemsService,
			ShipmentsService shipmentsService, CustomersService customersService, WarehousesService warehousesService) {
		this.inventoryService = inventoryService;
		this.itemsService = itemsService;
		this.shipmentsService = shipmentsService;
		this.customersService = customersService;
		this.warehousesService = warehousesService;
	}

	// ─────────────────────────────────────────────
	// LIST / FILTER
	// ─────────────────────────────────────────────

	/** Renders the full inventory list with no filter applied. */
	@GetMapping("/inventory/")
	public String showAllInventory(Model model) {
		log.info("GET /inventory/ → loading all inventory (no filter)");
		model.addAttribute("inventory", inventoryService.getAllInventory());
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("selectedWarehouse", "ALL");
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("activePage", "allInventory");
		model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
		model.addAttribute("filterApplied", false);
		return "show-all-inventory";
	}

	/**
	 * Filters the inventory list using the submitted form values. "ALL" is the
	 * sentinel meaning "no filter on this field". Delegates model population to the
	 * shared helper below.
	 */
	@GetMapping("/inventory/showInventoryByFilters")
	public String showInventoryByFilters(@RequestParam(required = false, defaultValue = "ALL") String itemId,
			@RequestParam(required = false, defaultValue = "ALL") String itemUom,
			@RequestParam(required = false, defaultValue = "ALL") String customerId,
			@RequestParam(value = "warehouseSelect", required = false, defaultValue = "ALL") String warehouseId,
			Model model) {

		log.info("GET /inventory/showInventoryByFilters → itemId={}, itemUom={}, customerId={}, warehouseId={}", itemId,
				itemUom, customerId, warehouseId);

		return populateInventoryModel(itemId, itemUom, customerId, warehouseId, model);
	}

	// ─────────────────────────────────────────────
	// ADD / UPDATE INVENTORY PAGE
	// ─────────────────────────────────────────────

	/**
	 * Backward-compatibility redirect for any page still using the old URL.
	 */
	@GetMapping("/inventory/goToAddOrUpdateInventoryPage")
	public String goToAddOrUpdateInventoryPage() {
		log.info("GET /inventory/goToAddOrUpdateInventoryPage → redirecting to /inventory/addOrUpdateInventoryPage");
		return "redirect:/inventory/addOrUpdateInventoryPage";
	}

	/**
	 * Renders the add/update inventory form with active customers and UOM options
	 * pre-loaded.
	 */
	@GetMapping("/inventory/addOrUpdateInventoryPage")
	public String addOrUpdateInventoryPage(Model model) {
		log.info("GET /inventory/addOrUpdateInventoryPage → rendering add/update inventory form");
		model.addAttribute("inventory", new Inventory());
		model.addAttribute("customers", itemsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "addOrUpdateInventory");
		model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
		return "create-or-update-inventory";
	}

	/**
	 * Handles the add/update inventory form POST (submitted from the
	 * addOrUpdateInventoryPage or the addInventory item sub-page).
	 *
	 * The composite keys are assembled here from the individual form fields before
	 * being passed to the service, which keeps the service signature clean.
	 *
	 * inventoryCreationPageType controls the redirect destination after success: -
	 * "CREATE_INVENTORY_FROM_CREATE_OR_UPDATE_INVENTORY_PAGE" → back to the
	 * add/update page - "CREATE_INVENTORY_FROM_EDIT_WAREHOUSE_PAGE" → back to the
	 * add-inventory-for-warehouse page anything else → to the viewOrEditInventory
	 * detail page (or add-inventory-for-item on error)
	 */
	@PostMapping("/inventory/addOrUpdateInventory")
	public String saveInventory(@ModelAttribute Inventory inventory, @RequestParam String adjustmentType,
			@RequestParam String warehouseId, @RequestParam String inventoryCreationPageType,
			RedirectAttributes redirectAttributes, Model model) {

		String itemId = inventory.getItemId();
		String customerId = inventory.getCustomerId();
		String itemUom = inventory.getItemUom();
		// String warehouseId = inventory.getWarehouseId();

		// Composite keys used as PKs in the items and inventory tables
		String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;
		String itemCustomerUomWarehouseId = itemCustomerUomId + "_" + warehouseId;

		log.info(
				"POST /inventory/addOrUpdateInventory → itemCustomerUomWarehouseId={}, qty={}, adjustmentType={}, pageType={}",
				itemCustomerUomWarehouseId, inventory.getQuantity(), adjustmentType, inventoryCreationPageType);

		String result = inventoryService.createOrUpdateInventory(inventory, itemCustomerUomId,
				itemCustomerUomWarehouseId, inventory.getQuantity(), adjustmentType);

		log.info("/inventory/addOrUpdateInventory → inventoryService.createOrUpdateInventory() : result →" + result);
		;
		// Always carry the UOM list through flash so the form repopulates correctly
		redirectAttributes.addFlashAttribute("itemUomsList", ITEM_UOMS_LIST);

		boolean hasError = setInventoryResultFlashAttributes(result, itemId, customerId, itemUom, itemCustomerUomId,
				itemCustomerUomWarehouseId, inventory.getQuantity(), adjustmentType, redirectAttributes);

		List<Customers> customers = null;

		// ── Redirect routing ──────────────────────────────────────────────────
		/*
		 * if (inventoryCreationPageType.equals(
		 * "CREATE_INVENTORY_FROM_CREATE_OR_UPDATE_INVENTORY_PAGE")) { // Always go back
		 * to the create/update form regardless of success or failure return
		 * "redirect:/inventory/addOrUpdateInventoryPage"; } else if
		 * (inventoryCreationPageType.equals(
		 * "CREATE_INVENTORY_FROM_EDIT_WAREHOUSE_PAGE") && hasError) { //
		 * redirectAttributes.addFlashAttribute("msg", "Failed to update inventory!");
		 * // This will be set in setInventoryResultFlashAttributes() so no need to pass
		 * it again model.addAttribute("itemUomsList", ITEM_UOMS_LIST); return
		 * "redirect:/inventory/addInventoryForWarehouse/" + warehouseId; } else if
		 * (inventoryCreationPageType.equals("CREATE_INVENTORY_FROM_EDIT_ITEM_PAGE") &&
		 * hasError) { return "redirect:/inventory/addInventoryForItem/" +
		 * itemCustomerUomId; } else { model.addAttribute("msg",
		 * "Inventory has been updated!"); model.addAttribute("bgColor", "#d4edda");
		 * model.addAttribute("textColor", "#155724");
		 * 
		 * if
		 * (inventoryCreationPageType.equals("CREATE_INVENTORY_FROM_EDIT_WAREHOUSE_PAGE"
		 * )) {
		 * 
		 * System.out.println(
		 * "inside if (inventoryCreationPageType.equals(\"CREATE_INVENTORY_FROM_EDIT_WAREHOUSE_PAGE\"))"
		 * ); return "redirect:/inventory/addInventoryForWarehouse/" + warehouseId;
		 * 
		 * } else { return "redirect:/inventory/viewOrEditInventory/" +
		 * itemCustomerUomWarehouseId; } }
		 */

		// Determine redirect destination based on which page triggered the inventory action.
		// hasError only changes the destination and return to the add-inventory-for-(item/warehouse) 
		// failure or success flash attributes are already set by setInventoryResultFlashAttributes() 
		switch (inventoryCreationPageType) {

		case "CREATE_INVENTORY_FROM_CREATE_OR_UPDATE_INVENTORY_PAGE":
			// Always return to the add/update form, success or failure
			return "redirect:/inventory/addOrUpdateInventoryPage";

		case "CREATE_INVENTORY_FROM_EDIT_WAREHOUSE_PAGE":
			// On error, stay on the warehouse inventory form so the user can correct the
			// input.
			// On success, go to the inventory detail page.
			return hasError ? "redirect:/inventory/addInventoryForWarehouse/" + warehouseId
					: "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;

		case "CREATE_INVENTORY_FROM_EDIT_ITEM_PAGE":
			// On error, stay on the item inventory form so the user can correct the input.
			// On success, go to the inventory detail page.
			return hasError ? "redirect:/inventory/addInventoryForItem/" + itemCustomerUomId
					: "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;

		default:
			return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
		}

	}

	// ─────────────────────────────────────────────
	// VIEW / EDIT INVENTORY
	// ─────────────────────────────────────────────

	/**
	 * Redirects the legacy /showInventoryDetails/{id} URL to the canonical
	 * /viewOrEditInventory/{id} URL so old links keep working.
	 */
	@GetMapping("/inventory/showInventoryDetails/{itemCustomerUomWarehouseId}")
	public String showInventoryDetails(@PathVariable String itemCustomerUomWarehouseId) {
		log.info("GET /inventory/showInventoryDetails/{} → redirecting to viewOrEditInventory",
				itemCustomerUomWarehouseId);
		return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
	}

	/**
	 * Renders the view/edit page for a specific inventory record. Redirects to the
	 * inventory list with an error message if the composite key does not match any
	 * existing record.
	 */
	@GetMapping("/inventory/viewOrEditInventory/{itemCustomerUomWarehouseId}")
	public String viewOrEditInventory(@PathVariable String itemCustomerUomWarehouseId, Model model,
			RedirectAttributes redirectAttributes) {

		log.info("GET /inventory/viewOrEditInventory/{}", itemCustomerUomWarehouseId);

		Optional<Inventory> inventory = inventoryService
				.getInventoryByItemCustomerUomWarehouseId(itemCustomerUomWarehouseId);

		if (inventory.isEmpty()) {
			log.warn("viewOrEditInventory() → inventory not found: {}", itemCustomerUomWarehouseId);
			redirectAttributes.addFlashAttribute("msg",
					"Requested inventory combination doesn't exist or cannot be created!");
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
			return "redirect:/inventory/";
		}

		model.addAttribute("inventory", inventory.get());
		return "edit-inventory";
	}

	/**
	 * Handles an inline quantity update from the viewOrEditInventory page. After
	 * applying the adjustment, the page reloads with the latest inventory state.
	 *
	 * The composite keys are re-assembled from individual path/request params
	 * rather than relying on a hidden form field, which avoids tampering risks.
	 */
	@PostMapping("/inventory/updateInventory/{itemCustomerUomWarehouseId}")
	public String updateInventory(@PathVariable String itemCustomerUomWarehouseId, @RequestParam String adjustmentType,
			@RequestParam int quantity, @RequestParam String itemId, @RequestParam String customerId,
			@RequestParam String warehouseId, @RequestParam String itemUom, RedirectAttributes redirectAttributes) {

		log.info("POST /inventory/updateInventory/{} → adjustmentType={}, qty={}", itemCustomerUomWarehouseId,
				adjustmentType, quantity);

		String itemCustomerUomId = itemId + "_" + customerId + "_" + itemUom;

		String result = inventoryService.createOrUpdateInventory(new Inventory(), itemCustomerUomId,
				itemCustomerUomWarehouseId, quantity, adjustmentType);

		setInventoryResultFlashAttributes(result, itemId, customerId, itemUom, itemCustomerUomId,
				itemCustomerUomWarehouseId, quantity, adjustmentType, redirectAttributes);

		return "redirect:/inventory/viewOrEditInventory/" + itemCustomerUomWarehouseId;
	}

	// ─────────────────────────────────────────────
	// ADD / VIEW INVENTORY FROM ITEM PAGE
	// ─────────────────────────────────────────────

	/**
	 * Renders the "add inventory" form scoped to a specific item-customer-UOM
	 * combination. Accessed from the viewOrEditItem page, not the main inventory
	 * pages.
	 *
	 * The itemCustomerUomId is parsed into its components to verify the item
	 * exists, and to load the warehouses available for that customer.
	 *
	 * Renders a custom-error page (rather than redirecting) if the item combination
	 * doesn't exist, because there is no sensible list page to redirect back to.
	 */
	@GetMapping("/inventory/addInventoryForItem/{itemCustomerUomId}")
	public String addInventoryFromItemsPage(@PathVariable String itemCustomerUomId, Model model) {

		log.info("GET /inventory/addInventoryForItem/{}", itemCustomerUomId);

		String[] parts = itemCustomerUomId.split("_");
		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		Optional<Items> item = itemsService.getItemById(itemCustomerUomId);

		if (item.isEmpty()) {
			log.warn("addInventoryFromItemsPage() → item not found: itemCustomerUomId={}", itemCustomerUomId);
			model.addAttribute("msg", "Inventory could not be added because the below combination doesn't exist:\n\n"
					+ "Item: " + itemId + "\nCustomer: " + customerId + "\nUOM: " + itemUom);
			model.addAttribute("bgColor", "#ffffff");
			model.addAttribute("textColor", "#d95f6c");
			return "custom-error";
		}

		List<Warehouses> warehouses = shipmentsService.getWarehousesByCustomer(customerId);
		log.debug("addInventoryFromItemsPage() → found {} warehouse(s) for customerId={}", warehouses.size(),
				customerId);

		model.addAttribute("item", item.get());
		model.addAttribute("warehouses", warehouses);
		return "add-inventory-for-item";
	}

	/**
	 * Renders the "add inventory" form scoped to a specific item-customer-UOM
	 * combination. Accessed from the viewOrEditItem page, not the main inventory
	 * pages.
	 *
	 * The itemCustomerUomId is parsed into its components to verify the item
	 * exists, and to load the warehouses available for that customer.
	 *
	 * Renders a custom-error page (rather than redirecting) if the item combination
	 * doesn't exist, because there is no sensible list page to redirect back to.
	 */
	@GetMapping("/inventory/addInventoryForWarehouse/{warehouseId}")
	public String addInventoryFromWarehousePage(@PathVariable String warehouseId, Model model) {

		log.info("GET /inventory/addInventoryForWarehouse/{}", warehouseId);

		Optional<Warehouses> warehouse = warehousesService.getWarehouseById(warehouseId);

		if (warehouse.isEmpty()) {
			log.warn("addInventoryFromWarehousePage() → warehouse not found: warehouseId={}", warehouseId);
			model.addAttribute("msg",
					"Inventory could not be added because Warehouse: " + warehouseId + "  doesn't exist!!");
			model.addAttribute("bgColor", "#ffffff");
			model.addAttribute("textColor", "#d95f6c");
			return "custom-error";
		}

		List<Customers> customers = warehousesService.getCustomersByWarehouseId(warehouseId);
		log.debug("addInventoryFromWarehousePage() → found {} customers(s) for warehouseId={}", customers.size(),
				warehouseId);
		model.addAttribute("warehouseId", warehouseId);
		model.addAttribute("customers", customers);
		model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
		return "add-inventory-for-warehouse";
	}

	/**
	 * Renders a filtered inventory view for a specific item-customer-UOM
	 * combination. Accessed from the viewOrEditItem page to see all warehouse stock
	 * for that item.
	 *
	 * Passes warehouseId="ALL" to show inventory across all warehouses for the
	 * item.
	 */
	@GetMapping("/inventory/viewInventoryForItem/{itemCustomerUomId}")
	public String viewInventoryFromItemsPage(@PathVariable String itemCustomerUomId, Model model) {

		log.info("GET /inventory/viewInventoryForItem/{}", itemCustomerUomId);

		String[] parts = itemCustomerUomId.split("_");
		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		// "ALL" for warehouseId to show stock across every configured warehouse
		return populateInventoryModel(itemId, itemUom, customerId, "ALL", model);
	}

	/**
	 * Renders a filtered inventory view for a specific warehouse. Accessed from the
	 * viewOrEditWarehouse page to see all the stock for that warehouse.
	 *
	 * Passes "ALL" for customer, item, uom to show all inventory for that
	 * warehouses
	 */

	@GetMapping("/inventory/viewInventoryForWarehouse/{warehouseId}")
	public String viewInventoryFromWarehousePage(@PathVariable String warehouseId, Model model) {

		log.info("GET /inventory/viewInventoryForWarehouse/{}", warehouseId);

		// "ALL" for other attributes except the warehouseId to show stock across every
		// item, uom, customer for the given warehouse
		return populateInventoryModel("ALL", "ALL", "ALL", warehouseId, model);
	}

	// ─────────────────────────────────────────────
	// INVENTORY CHECK FOR SHIPMENT LINES
	// ─────────────────────────────────────────────

	/**
	 * Fetches inventory availability for a list of proposed shipment lines and
	 * renders the shortage inventory view.
	 *
	 * Used by the shipment creation flow to show which lines have sufficient stock
	 * and which have a shortage or an unconfigured warehouse.
	 */
	@PostMapping("/inventory/fetchForLines")
	public String fetchInventoryForLines(@RequestParam String customerId, @RequestBody List<ShipmentLines> lines,
			Model model) {

		log.info("POST /inventory/fetchForLines → customerId={}, lineCount={}", customerId, lines.size());

		List<InventoryCheckResult> inventoryList = inventoryService.getInventoryForShipmentLines(customerId, lines);

		log.info("fetchInventoryForLines() → returned {} result(s) for customerId={}", inventoryList.size(),
				customerId);

		model.addAttribute("inventory", inventoryList);
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("selectedWarehouse", "ALL");
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("activePage", "allInventory");
		model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
		model.addAttribute("filterApplied", false);
		return "show-shortage-inventory";
	}

	// ─────────────────────────────────────────────
	// PRIVATE HELPERS
	// ─────────────────────────────────────────────

	/**
	 * Populates model attributes shared by all inventory list/filter views. Used by
	 * both the filter endpoint and the item-level view-inventory endpoint to avoid
	 * duplicating the same six model.addAttribute calls.
	 *
	 * The warehouse dropdown is scoped to the selected customer when one is chosen,
	 * or shows all warehouses when customerId is "ALL".
	 *
	 * @return the view name "show-all-inventory"
	 */
	private String populateInventoryModel(String itemId, String itemUom, String customerId, String warehouseId,
			Model model) {

		model.addAttribute("inventory", inventoryService.getInventoryDetails(customerId, warehouseId, itemId, itemUom));
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedItemUom", itemUom);
		model.addAttribute("selectedItemId", "ALL".equals(itemId) ? "" : itemId);
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("itemUomsList", ITEM_UOMS_LIST);
		model.addAttribute("filterApplied", true);

		// Scope the warehouse dropdown to the selected customer; show all if none
		// selected
		if ("ALL".equals(customerId)) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		return "show-all-inventory";
	}

	/**
	 * Translates the string result code from InventoryService into flash attributes
	 * for the redirect response, covering all four possible outcomes.
	 *
	 * Also logs at the appropriate level (WARN for business-rule failures, INFO for
	 * success).
	 *
	 * @return true if the result represents an error (so the caller can choose a
	 *         different redirect destination on failure), false on success.
	 */
	private boolean setInventoryResultFlashAttributes(String result, String itemId, String customerId, String itemUom,
			String itemCustomerUomId, String itemCustomerUomWarehouseId, int quantity, String adjustmentType,
			RedirectAttributes redirectAttributes) {

		switch (result) {

		case "ITEM_NOT_FOUND":
			log.warn("Inventory update failed: ITEM_NOT_FOUND → itemId={}, customerId={}, itemUom={}", itemId,
					customerId, itemUom);
			redirectAttributes.addFlashAttribute("msg", "This combination doesn't exist!\nItem: " + itemId
					+ ", Customer: " + customerId + ", UOM: " + itemUom);
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
			return true;

		case "ITEM_DISABLED":
			log.warn("Inventory update failed: ITEM_DISABLED → itemCustomerUomId={}", itemCustomerUomId);
			redirectAttributes.addFlashAttribute("msg",
					"Item is Disabled. Make it Active to update inventory!"
							+ " &nbsp;&nbsp;&nbsp;&nbsp;<a style='color: #3474eb;'" + " href='/items/viewOrEditItem/"
							+ itemCustomerUomId + "'>Update Item</a>");
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
			return true;

		case "CANNOT_MAKE_INVENTORY_NEGATIVE":
			log.warn(
					"Inventory update failed: CANNOT_MAKE_INVENTORY_NEGATIVE → "
							+ "itemCustomerUomWarehouseId={}, requestedDecrease={}",
					itemCustomerUomWarehouseId, quantity);
			redirectAttributes.addFlashAttribute("msg", "Cannot update inventory to a negative quantity.");
			// Re-populate the form fields so the user sees what they entered
			redirectAttributes.addFlashAttribute("quantityFromController", quantity);
			redirectAttributes.addFlashAttribute("selectedAdjustmentType", adjustmentType);
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
			return true;

		default:
			// "INVENTORY_UPDATED" — success
			log.info("Inventory updated successfully → itemCustomerUomWarehouseId={}", itemCustomerUomWarehouseId);
			redirectAttributes.addFlashAttribute("msg",
					"Inventory has been updated!" );
			redirectAttributes.addFlashAttribute("bgColor", "#d1fae5");
			redirectAttributes.addFlashAttribute("textColor", "#45484d");
			return false;
		}
	}
}