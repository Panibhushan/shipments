package dev.shipping.shipments.controller;

import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Warehouses;

@Controller
public class ShipmentsController {

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
	public String home(Model model) {
		model.addAttribute("shipments", shipmentsService.getAllShipments());
		model.addAttribute("activePage", "allShipments"); // ← this shows which dropdown is active in the navbar
		model.addAttribute("selectedCustomer", "ALL");
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		return "show-all-shipments";
	}

	// SHOW SHIPMENTS LIST BASED ON INPUT FILTERS
	@PostMapping("/shipments/showShipmentsByFilters/{customerId}/{warehouseId}/{status}")
	public String showShipmentsByCustomerAndWarehouse(@PathVariable String customerId, @PathVariable String warehouseId,
			@PathVariable String status, Model model) {

		System.out.println("/shipments/showShipmentsByFilters/{customerId}/{warehouseId}: "
				+ customerId + " / " + warehouseId + " / " + status);

		model.addAttribute("shipments", shipmentsService.getShipmentDetails(customerId, warehouseId, status));
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("selectedStatus", status.equals("ALL") ? "" : status);
		model.addAttribute("customers", customersService.getAllCustomers());

		if (customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}

		return "show-all-shipments";

	}

	// ─────────────────────────────────────────────
	// CREATE SHIPMENT
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/goToCreateShipmentPage")
	public String addShipmentsPage(Model model) {
		model.addAttribute("shipment", new Shipments());
		model.addAttribute("customers", shipmentsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "createShipment"); // ← this is show which dropdown is active in the navbar
		return "create-shipment";
	}

	@GetMapping("/shipments/getWarehousesByCustomer")
	public ResponseEntity<List<Warehouses>> getByCustomer(@RequestParam String customerId) {
		return ResponseEntity.ok(shipmentsService.getWarehousesByCustomer(customerId));
	}

	@PostMapping("/shipments/createShipment")
	public String saveShipments(@ModelAttribute Shipments shipment, @RequestParam String customerId,
			@RequestParam String warehouseId, RedirectAttributes redirectAttributes) {
		
		 /* resultArray[0]: SUCCESS or FAILED 
		  * resultArray[1]: Newly created shipment-id, if success, 
		  * and ErrorMessage if failed*/		 		
		
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

		return "redirect:/shipments/goToCreateShipmentPage";
	}

	// ─────────────────────────────────────────────
	// VIEW SHIPMENT DETAILS
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/showShipmentDetails/{shipmentId}")
	public String showShipmentDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		Optional<Shipments> shipment = shipmentsService.getShipmentById(shipmentId);

		if (shipment.isPresent()) {
			model.addAttribute("shipment", shipment);
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
	public String updateShipmentStatus(@RequestParam String shipmentId,
			@RequestParam Integer shipStatus, @RequestParam String action, @RequestParam String customerId,
			@RequestParam String warehouseId,
			@RequestParam(required = false, defaultValue = "") String cancellationReason,
			RedirectAttributes redirectAttributes) {

		System.out.println("/shipments/updateShipmentStatus:: " + shipmentId + " --- "
				+ shipStatus + " --- " + action + " --- " + customerId + " --- " + warehouseId + " --- "
				+ cancellationReason);

		// pass cancellationReason into your service as needed
		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId,
				cancellationReason);

		if (!response.contains("SUCCESS")) {
			redirectAttributes.addFlashAttribute("disableButtonActions", true);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		} else {
			
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

}