package dev.shipping.shipments.controller;

import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.ShipmentLines;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Warehouses;

@Controller
public class ShipmentsController {

	List<String> itemUomsList = Arrays.asList("EACH", "MTR", "CMTR", "PAIR");

	private final ShipmentsService shipmentsService;
	private final CustomersService customersService;
	private final WarehousesService warehousesService;

	public ShipmentsController(ShipmentsService shipmentsService, CustomersService customersService,
			WarehousesService warehousesService) {
		this.shipmentsService = shipmentsService;
		this.customersService = customersService;
		this.warehousesService = warehousesService;
	}

	// ─────────────────────────────────────────────
	// HOME / LIST
	// ─────────────────────────────────────────────

	@GetMapping("/")
	public String homeWithJustSlashinUrl() {
		return "index";
	}

	// SHOW ALL SHIPMENTS AS LIST
	@GetMapping("/shipments/")
	public String showAllShipments(Model model) {
		model.addAttribute("shipments", shipmentsService.getAllShipments());
		model.addAttribute("activePage", "allShipments"); // ← this shows which dropdown is active in the navbar
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("filterApplied", false);
		return "show-all-shipments";
	}

	// SHOW ALL SHIPMENTS AS LIST
	@GetMapping("/shipments/advancedShipmentFilters/")
	public String showAllShipmentsWithadvancedFilters(Model model) {
		model.addAttribute("shipments", shipmentsService.getAllShipments());
		model.addAttribute("activePage", "allShipments"); // ← this shows which dropdown is active in the navbar
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		model.addAttribute("filterApplied", false);
		return "show-all-shipments-with-advanced-filters";
	}

	@GetMapping("/shipments/showShipmentsByFilters")
	public String showShipmentsByBasicFilters(@RequestParam(required = false) String shipmentId,
			@RequestParam(required = false) String customerId, @RequestParam(required = false) String shipmentStatus,
			@RequestParam(required = false) String warehouseId, Model model) {

		System.out.println(shipmentId);
		System.out.println(customerId);
		System.out.println(warehouseId);
		System.out.println(shipmentStatus);
		System.out.println(
				"/shipments/showShipmentsByFilters::Get()/{shipmentId}/{customerId}/{warehouseId}/{shipmentStatus}: "
						+ shipmentId + " / " + customerId + " / " + warehouseId + " / " + shipmentStatus);

		if (shipmentId.trim() == "") {
			shipmentId = "ALL";
		}

		List<Shipments> shipmentsList = shipmentsService.getShipmentList(shipmentId, customerId, warehouseId,
				shipmentStatus);

		model.addAttribute("shipments", shipmentsList);
		model.addAttribute("enteredShipmentId", shipmentId.equals("ALL") ? "" : shipmentId);
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedStatus", shipmentStatus.equals("ALL") ? "" : shipmentStatus);
		model.addAttribute("customers", customersService.getAllCustomers());

		if (customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		model.addAttribute("filterApplied", true);
		return "show-all-shipments";
	}

	// SHOW SHIPMENTS LIST BASED ON INPUT ADVANCED FILTERS
	@GetMapping("/shipments/showShipmentsByAdvancedFilters")
	public String showShipmentsByAdvancedFilters(
			@RequestParam(required = false, defaultValue = "ALL") String shipmentId,
			@RequestParam(required = false) String customerId, @RequestParam(required = false) String warehouseId,
			@RequestParam(required = false) String statusFrom, @RequestParam(required = false) String statusTo,
			@RequestParam(required = false, defaultValue = "ALL") String createdFrom,
			@RequestParam(required = false, defaultValue = "ALL") String createdTo,
			@RequestParam(required = false) String itemId, Model model) {

		System.out.println(
				"/shipments/showShipmentsByAdvancedFilters::GET()/{shipmentId}/{customerId}/{warehouseId}/{statusFrom}/{statusTo}/{createdFrom}/{createdTo}/{itemId}: "
						+ shipmentId + " / " + customerId + " / " + warehouseId + " / " + statusFrom + " / " + statusTo
						+ " / " + createdFrom + " / " + createdTo + " / " + itemId);

		List<Shipments> shipmentsList = shipmentsService.getShipmentListByAdvancedFilters(shipmentId, customerId,
				warehouseId, statusFrom, statusTo, createdFrom, createdTo, itemId);

		model.addAttribute("shipments", shipmentsList);
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedStatusFrom", statusFrom);
		model.addAttribute("selectedStatusTo", statusTo);
		model.addAttribute("enteredShipmentId", shipmentId.equals("ALL") ? "" : shipmentId);
		model.addAttribute("selectedCreatedFrom", createdFrom.equals("ALL") ? "" : createdFrom);
		model.addAttribute("selectedCreatedTo", createdTo.equals("ALL") ? "" : createdTo);
		model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);

		model.addAttribute("customers", customersService.getAllCustomers());

		if (customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		model.addAttribute("filterApplied", true);
		return "show-all-shipments-with-advanced-filters";

	}

	// ─────────────────────────────────────────────
	// CREATE SHIPMENT
	// ─────────────────────────────────────────────

	// Adding this addittional method, just incase I missed to update the URL from
	// goToCreateShipmentPage to createShipmentPage in any pages, this will route
	// correctly instead of giving error
	@GetMapping("/shipments/goToCreateShipmentPage")
	public String goToCreateShipmentPage(Model model) {
		return "redirect:/shipments/createShipmentPage";
	}

	@GetMapping("/shipments/createShipmentPage")
	public String addShipmentsPage(Model model) {
		model.addAttribute("shipment", new Shipments());
		model.addAttribute("customers", shipmentsService.getActiveAndValidCustomers());
		// model.addAttribute("itemUomsList", itemUomsList);
		model.addAttribute("activePage", "createShipment"); // ← this is show which dropdown is active in the navbar
		return "create-shipment-with-lines";
	}

	@GetMapping("/shipments/getWarehousesByCustomer")
	public ResponseEntity<List<Warehouses>> getByCustomer(@RequestParam String customerId) {
		return ResponseEntity.ok(shipmentsService.getWarehousesByCustomer(customerId));
	}

	@PostMapping("/shipments/createShipment")
	public String saveShipments(@ModelAttribute Shipments shipment, @RequestParam String customerId,
			@RequestParam String warehouseId, RedirectAttributes redirectAttributes) {

		/*
		 * resultArray[0]: SUCCESS or FAILED resultArray[1]: Newly created shipment-id,
		 * if success, and ErrorMessage if failed
		 */

		String[] resultArray = shipmentsService.createShipment(shipment, customerId, warehouseId);

		if (resultArray[0].equals("SUCCESS")) {
			redirectAttributes.addFlashAttribute("msg",
					"New shipment created with ID: " + resultArray[1] + ""
							+ "&nbsp;&nbsp;&nbsp;&nbsp;<a href='/shipments/showShipmentDetails/" + resultArray[1]
							+ "'>View Shipment Details</a>");
			redirectAttributes.addFlashAttribute("shipmentId", resultArray[1]);
			redirectAttributes.addFlashAttribute("bgColor", "#d4edda");
			redirectAttributes.addFlashAttribute("textColor", "#155724");
		} else {
			redirectAttributes.addFlashAttribute("msg", resultArray[1]);
			redirectAttributes.addFlashAttribute("bgColor", "#f8d7da");
			redirectAttributes.addFlashAttribute("textColor", "#721c24");
		}

		return "redirect:/shipments/createShipmentPage";
	}

	// ─────────────────────────────────────────────
	// VIEW SHIPMENT DETAILS
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/showShipmentDetails/{shipmentId}")
	public String showShipmentDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		Optional<Shipments> shipment = shipmentsService.getShipmentById(shipmentId);

		if (shipment.isPresent()) {
			model.addAttribute("shipment", shipment.get());
			return "show-shipment-details";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesnt exist !!!");
			return "redirect:/shipments/";
		}
	}

	// ─────────────────────────────────────────────
	// UPDATE SHIPMENT STATUS
	// ─────────────────────────────────────────────

	@PostMapping("/shipments/updateShipmentStatus")
	public String updateShipmentStatus(@RequestParam String shipmentId, @RequestParam Integer shipStatus,
			@RequestParam String action, @RequestParam String customerId, @RequestParam String warehouseId,
			@RequestParam(required = false, defaultValue = "") String cancellationReason,
			RedirectAttributes redirectAttributes) {

		System.out.println("/shipments/updateShipmentStatus:: " + shipmentId + " --- " + shipStatus + " --- " + action
				+ " --- " + customerId + " --- " + warehouseId + " --- " + cancellationReason);

		// pass cancellationReason into your service as needed
		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId,
				cancellationReason);

		if (!response.contains("SUCCESS")) {
			redirectAttributes.addFlashAttribute("disableButtonActions", true);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			redirectAttributes.addFlashAttribute("autoHide", false); // this will prevent the message in red ribbon to
																		// always show & not disapper after 10secs
		} else {
			redirectAttributes.addFlashAttribute("autoHide", true);
			redirectAttributes.addFlashAttribute("bgColor", "#d4edda");
			redirectAttributes.addFlashAttribute("textColor", "#155724");
		}

		redirectAttributes.addFlashAttribute("msg", response);
		redirectAttributes.addFlashAttribute("customerId", customerId);
		redirectAttributes.addFlashAttribute("warehouseId", warehouseId);

		return "redirect:/shipments/showShipmentDetails/" + shipmentId;
	}

	// ─────────────────────────────────────────────
	// SHIPMENT AUDIT
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/getShipmentAudit/{shipmentId}")
	@ResponseBody
	public List<Map<String, String>> getShipmentAudit(@PathVariable String shipmentId) {

		System.out.println("/shipments/getShipmentAudit/{shipmentId}: " + shipmentId);

		List<Map<String, String>> shipmentsAudit = shipmentsService.getShipmentAudit(shipmentId);

		System.out.println("/shipments/getShipmentAudit/{shipmentId}: shipmentsAudit: " + shipmentsAudit);
		return shipmentsAudit;
	}

	/*
	 * @PostMapping("/shipments/checkInventoryAvailability")
	 * 
	 * @ResponseBody public List<Map<String, String>>
	 * checkInventoryAvailability(@RequestParam String customerId,
	 * 
	 * @RequestBody List<ShipmentLines> lines) {
	 * 
	 * List<Inventory> listOfAvailableIventory = new ArrayList<>();
	 * 
	 * System.out.
	 * println("/shipments/checkInventoryAvailability::: checkInventory(): customerId: "
	 * + customerId);
	 * 
	 * System.out.
	 * println("/shipments/checkInventoryAvailability::: checkInventory(): lines: "
	 * + lines.toString());
	 * 
	 * // 1. Group items and sum their quantities Map<String, Integer>
	 * groupedResults = lines.stream().collect(Collectors.groupingBy(line -> {
	 * String itemId = (line.getItemId() != null) ? line.getItemId() : "UNKNOWN"; //
	 * Replace "EACH" with line.getItemUom() if your class has a UOM getter String
	 * itemUom = line.getItemUom();
	 * 
	 * // Create a compound key using a clear separator symbol like '::' return
	 * itemId + "::" + itemUom; },
	 * Collectors.summingInt(ShipmentLines::getQuantity)));
	 * 
	 * System.out.println("groupedResults: " + groupedResults.toString());
	 * 
	 * // 2. Loop through the map, extract parameters, and call your checking method
	 * for (Map.Entry<String, Integer> entry : groupedResults.entrySet()) { // Split
	 * the compound key back into individual components String[] keyParts =
	 * entry.getKey().split("::");
	 * 
	 * String itemId = keyParts[0]; String itemUom = keyParts[1]; int totalQty =
	 * entry.getValue();
	 * 
	 * // 3. Invoke your target method with individual parameters //
	 * checkInventoryAvailability(itemId, itemUom, totalQty);
	 * 
	 * System.out.println("calling checkInventoryAvailability(" + customerId + ", "
	 * + itemId + ", " + itemUom + ", " + totalQty + ")");
	 * 
	 * listOfAvailableIventory =
	 * shipmentsService.checkInventoryAvailability(customerId, itemId, itemUom,
	 * totalQty);
	 * 
	 * System.out.
	 * println("calling checkInventoryAvailability():: output:: listOfAvailableIventory: "
	 * + listOfAvailableIventory.toString());
	 * 
	 * }
	 * 
	 * List<Map<String, String>> listMapOfAvailableIventory = new ArrayList<>();
	 * 
	 * if (listOfAvailableIventory == null) return List.of();
	 * 
	 * String itemId = ""; String itemUom = ""; String requestedQty = "0"; String
	 * availableQty = "0"; String warehouseId = ""; String warehouseName = ""; int i
	 * = 0;
	 * 
	 * for (Inventory inv : listOfAvailableIventory) {
	 * 
	 * ++i; itemId = inv.getItemId() != null ? inv.getItemId() : ""; itemUom =
	 * inv.getItemUom() != null ? inv.getItemUom() : ""; requestedQty =
	 * Integer.toString(groupedResults.getOrDefault((itemId + "::" + itemUom), 0));
	 * availableQty = Integer.toString(inv.getAvailableQuantity()) != null ?
	 * String.valueOf(inv.getAvailableQuantity()) : "0"; warehouseId =
	 * inv.getWarehouseId() != null ? inv.getWarehouseId() : ""; warehouseName =
	 * warehousesService.getWarehouseNameById(warehouseId);
	 * 
	 * listMapOfAvailableIventory.add(Map.of("lineNumber", Integer.toString(i),
	 * "itemId", itemId, "itemUom", itemUom, "requestedQty", requestedQty,
	 * "availableQty", availableQty, "warehouseId", warehouseId, "warehouseName",
	 * warehouseName)); }
	 * 
	 * System.out.
	 * println("ShipmentsController: checkInventoryAvailability():: listMapOfAvailableIventory: "
	 * + listMapOfAvailableIventory);
	 * 
	 * return listMapOfAvailableIventory; }
	 */

	@PostMapping("/shipments/checkInventoryAvailability")
	@ResponseBody
	public List<Map<String, String>> checkInventoryAvailability(@RequestParam String customerId,
			@RequestBody List<ShipmentLines> lines) {

		List<List<Inventory>> listOfAvailableIventory = new ArrayList<>();

		System.out.println("/shipments/checkInventoryAvailability::: checkInventory(): customerId: " + customerId);

		System.out.println("/shipments/checkInventoryAvailability::: checkInventory(): lines: " + lines.toString());

		// 1. Group items and sum their quantities
		Map<String, Integer> groupedResults = lines.stream().collect(Collectors.groupingBy(line -> {
			String itemId = (line.getItemId() != null) ? line.getItemId() : "UNKNOWN";
			// Replace "EACH" with line.getItemUom() if your class has a UOM getter
			String itemUom = line.getItemUom();

			// Create a compound key using a clear separator symbol like '::'
			return itemId + "::" + itemUom;
		}, Collectors.summingInt(ShipmentLines::getQuantity)));

		System.out.println("groupedResults: " + groupedResults.toString());

		// 2. Loop through the map, extract parameters, and call your checking method
		for (Map.Entry<String, Integer> entry : groupedResults.entrySet()) {
			// Split the compound key back into individual components
			String[] keyParts = entry.getKey().split("::");

			String itemId = keyParts[0];
			String itemUom = keyParts[1];
			int totalQty = entry.getValue();

			// 3. Invoke your target method with individual parameters
			// checkInventoryAvailability(itemId, itemUom, totalQty);

			System.out.println("calling checkInventoryAvailability(" + customerId + ", " + itemId + ", " + itemUom
					+ ", " + totalQty + ")");

			List<Inventory> invList = shipmentsService.checkInventoryAvailability(customerId, itemId, itemUom,
					totalQty);
			;

			listOfAvailableIventory.add(invList);

			System.out.println("calling checkInventoryAvailability():: output:: listOfAvailableIventory: "
					+ listOfAvailableIventory.toString());

		}

		List<Map<String, String>> listMapOfAvailableIventory = new ArrayList<>();

		if (listOfAvailableIventory == null)
			return List.of();

		String itemId = "";
		String itemUom = "";
		String requestedQty = "0";
		String availableQty = "0";
		String warehouseId = "";
		String warehouseName = "";
		int i = 0;

		for (List<Inventory> invList : listOfAvailableIventory) {
			for (Inventory inv : invList) {

				++i;
				itemId = inv.getItemId() != null ? inv.getItemId() : "";
				itemUom = inv.getItemUom() != null ? inv.getItemUom() : "";
				requestedQty = Integer.toString(groupedResults.getOrDefault((itemId + "::" + itemUom), 0));
				availableQty = Integer.toString(inv.getAvailableQuantity()) != null
						? String.valueOf(inv.getAvailableQuantity())
						: "0";
				warehouseId = inv.getWarehouseId() != null ? inv.getWarehouseId() : "";
				warehouseName = warehousesService.getWarehouseNameById(warehouseId);

				listMapOfAvailableIventory.add(Map.of("lineNumber", Integer.toString(i), "itemId", itemId, "itemUom",
						itemUom, "requestedQty", requestedQty, "availableQty", availableQty, "warehouseId", warehouseId,
						"warehouseName", warehouseName));
			}
		}

		System.out.println("ShipmentsController: checkInventoryAvailability():: listMapOfAvailableIventory: "
				+ listMapOfAvailableIventory);

		return listMapOfAvailableIventory;
	}

}