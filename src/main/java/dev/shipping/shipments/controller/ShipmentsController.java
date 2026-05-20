package dev.shipping.shipments.controller;

import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.service.ShipmentsService;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Warehouses;

@Controller
public class ShipmentsController {

	private final ShipmentsService shipmentsService;

	public ShipmentsController(ShipmentsService shipmentsService) {
		this.shipmentsService = shipmentsService;
	}

	// ─────────────────────────────────────────────
	// HOME / LIST
	// ─────────────────────────────────────────────

	@GetMapping("/")
	public String homeWithJustSlashinUrl() {
		return "index";
	}

	@GetMapping("/shipments/")
	public String home(Model model) {
		model.addAttribute("shipments", shipmentsService.getAllShipments());
	    model.addAttribute("activePage", "allShipments");  // ←  this is show which dropdown is active in the navbar
		return "show-all-shipments";
	}

	// ─────────────────────────────────────────────
	// CREATE SHIPMENT
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/goToCreateShipmentPage")
	public String addShipmentsPage(Model model) {
		model.addAttribute("shipment", new Shipments());
		model.addAttribute("customers", shipmentsService.getActiveAndValidCustomers());
	    model.addAttribute("activePage", "createShipment");  // ←  this is show which dropdown is active in the navbar
		return "create-shipment";
	}

	@GetMapping("/shipments/getWarehousesByCustomer")
	public ResponseEntity<List<Warehouses>> getByCustomer(@RequestParam String customerId) {
		return ResponseEntity.ok(shipmentsService.getWarehousesByCustomer(customerId));
	}

	@PostMapping("/shipments/createShipment")
	public String saveShipments(@ModelAttribute Shipments shipment, @RequestParam String customerId,
			@RequestParam String warehouseId, RedirectAttributes redirectAttributes) {

		ShipmentsService.CreateShipmentResult result = shipmentsService.createShipment(shipment, customerId, warehouseId);

		if (result.isSuccess()) {
			redirectAttributes.addFlashAttribute("msg", "New shipment created with ID: ");
			redirectAttributes.addFlashAttribute("shipmentId", result.getShipmentId());
			redirectAttributes.addFlashAttribute("bgColor", "#d4edda");
			redirectAttributes.addFlashAttribute("textColor", "#155724");
		} else {
			redirectAttributes.addFlashAttribute("msg", result.getErrorMessage());
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

		Shipments shipment = shipmentsService.getShipmentById(shipmentId);

		if (shipment != null) {
			model.addAttribute("shipment", shipment);
			return "show-shipment-details";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesnt exist !!!");
			return "redirect:/shipments/";
		}
	}

	// ─────────────────────────────────────────────
	// UPDATE SHIPMENT STATUS — 3 entry points, same service call
	// ─────────────────────────────────────────────

	@PostMapping("/shipments/cancelShipmentFromAllShipmentsPage")
	public String cancelShipmentFromAllShipmentsPage(@RequestParam String shipmentId,
			@RequestParam Integer shipStatus, @RequestParam String action,
			@RequestParam String customerId, @RequestParam String warehouseId,
			RedirectAttributes redirectAttributes) {

		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId);

		if (!response.equals("SHIPMENT_STATUS_UPDATED_SUCCESSFULLY")) {
			redirectAttributes.addFlashAttribute("msg", response);
			redirectAttributes.addFlashAttribute("customerId", customerId);
			redirectAttributes.addFlashAttribute("warehouseId", warehouseId);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		}

		return "redirect:/shipments/";
	}

	@PostMapping("/shipments/updateShipmentStatusFromInsideShipmentDetailsPage")
	public String updateShipmentStatusFromInsideShipmentDetailsPage(@RequestParam String shipmentId,
			@RequestParam Integer shipStatus, @RequestParam String action,
			@RequestParam String customerId, @RequestParam String warehouseId,
			RedirectAttributes redirectAttributes) {

		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId);

		if (!response.equals("SHIPMENT_STATUS_UPDATED_SUCCESSFULLY")) {
			redirectAttributes.addFlashAttribute("msg", response);
			redirectAttributes.addFlashAttribute("customerId", customerId);
			redirectAttributes.addFlashAttribute("warehouseId", warehouseId);
			redirectAttributes.addFlashAttribute("disableButtonActions", true);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		}

		return "redirect:/shipments/showShipmentDetails/" + shipmentId;
	}

	@PostMapping("/shipments/updateShipmentStatusFromInsideShipmentAuditDetailsPage")
	public String updateShipmentStatusFromInsideShipmentAuditDetailsPage(@RequestParam String shipmentId,
			@RequestParam Integer shipStatus, @RequestParam String action,
			@RequestParam String customerId, @RequestParam String warehouseId,
			RedirectAttributes redirectAttributes) {

		// ✅ Fixed: was calling the helper TWICE in the original — now called once
		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId);

		if (!response.equals("SHIPMENT_STATUS_UPDATED_SUCCESSFULLY")) {
			redirectAttributes.addFlashAttribute("msg", response);
			redirectAttributes.addFlashAttribute("customerId", customerId);
			redirectAttributes.addFlashAttribute("warehouseId", warehouseId);
			redirectAttributes.addFlashAttribute("disableButtonActions", true);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
		}

		return "redirect:/shipments/showShipmentAuditDetails/" + shipmentId;
	}

	// ─────────────────────────────────────────────
	// SHIPMENT AUDIT
	// ─────────────────────────────────────────────

	@GetMapping("/shipments/showShipmentAuditDetails/{shipmentId}")
	public String showShipmentAuditDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		Shipments shipment = shipmentsService.getShipmentById(shipmentId);

		if (shipment != null) {
			model.addAttribute("shipment", shipment);
			model.addAttribute("shipmentAudit", shipmentsService.getShipmentAudit(shipmentId));
			return "show-shipment-audit";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesnt exist !!!");
			return "redirect:/shipments/";
		}
	}

}