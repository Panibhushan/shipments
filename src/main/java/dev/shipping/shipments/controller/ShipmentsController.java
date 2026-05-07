package dev.shipping.shipments.controller;

import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ShipmentsRepository;
import dev.shipping.shipments.service.DynamoDbService;
import dev.shipping.shipments.service.SnsPublisherService;
import dev.shipping.shipments.service.SqsSenderService;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ShipmentsController {

	private final ShipmentsRepository shipmentsRepo;
	private final CustomersRepository customersRepo;

	private SnsPublisherService snsService;
	private DynamoDbService dynamoDbService;
	private SqsSenderService sqsService;
	

	public ShipmentsController(ShipmentsRepository shipmentsRepo, CustomersRepository customersRepo,
			DynamoDbService dynamoDbService, SnsPublisherService snsService, SqsSenderService sqsService) {
		this.shipmentsRepo = shipmentsRepo;
		this.customersRepo = customersRepo;
		this.dynamoDbService = dynamoDbService;
		this.snsService = snsService;
		this.sqsService = sqsService;
	}

	@GetMapping("/")
	public String homeWithJustSlashinUrl(Model model) {
		return "index";
	}

	@GetMapping("/shipments/")
	public String home(Model model) {
		model.addAttribute("shipments", shipmentsRepo.findAll());
		List<Shipments> shipments = shipmentsRepo.findAll();
		System.out.println("shipments in home: ");
		for (int i = 0; i < shipments.size(); i++) {
			System.out.println(shipments.get(i));
		}
		return "show-all-shipments";
	}

	@GetMapping("/shipments/goToCreateShipmentPage")
	public String addShipmentsPage(Model model) {
		List<Customers> customers = customersRepo.findAll();

		System.out.println("customers: " + customers.toString());

		model.addAttribute("shipment", new Shipments());
		model.addAttribute("customers", customersRepo.findAll());
		return "create-shipment";
	}

	@PostMapping("/shipments/createShipment")
	public String saveShipments(@ModelAttribute Shipments shipment, @RequestParam String customerId,
			RedirectAttributes redirectAttributes) {

		Customers customer = customersRepo.findById(customerId)
				.orElseThrow(() -> new RuntimeException("Customer not found"));
		shipment.setCustomerId(customerId);
		Shipments savedShipment = shipmentsRepo.save(shipment);
		snsService.publishShipmentStatus(savedShipment.getShipmentId(), "1100 - Created");

		// Message with shipment id
		redirectAttributes.addFlashAttribute("msg", "New shipment created with ID: ");
		redirectAttributes.addFlashAttribute("shipmentId", savedShipment.getShipmentId());

		return "redirect:/shipments/goToCreateShipmentPage";
	}

	@GetMapping("/shipments/showShipmentDetails/{shipmentId}")
	public String showShipmentDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		System.out.println(" inside /shipments/showShipmentDetails/{shipmentId} with shipmentId: " + shipmentId);
		Optional<Shipments> singleShipment = shipmentsRepo.findById(shipmentId);

		if (singleShipment.isPresent()) {
			System.out.println("inside IF block");
			System.out.println(" singleShipment: " + singleShipment.toString());
			model.addAttribute("shipment", singleShipment.get());
			return "show-shipment-details";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesnt exist !!!");
			return "redirect:/shipments/";
		}
	}

	@PostMapping("/shipments/updateShipmentStatus")
	public String updateShipmentStatus(@RequestParam String shipmentId, @RequestParam Integer shipStatus,
			@RequestParam String action) {

		helperFunctionToUpdateShipmentStatus(shipmentId, shipStatus, action);

		return "redirect:/shipments/";
	}

	@PostMapping("/shipments/updateShipmentStatusFromInsideShipmentDetailsPage")
	public String updateShipmentStatusFromInsideShipmentDetailsPage(@RequestParam String shipmentId, @RequestParam Integer shipStatus, @RequestParam String action) {

		System.out.println("inside /shipments/updateShipmentStatusFromInsideShipmentDetailsPage: " + shipmentId + " - "
				+ shipStatus + " - " + action);

		helperFunctionToUpdateShipmentStatus(shipmentId, shipStatus, action);

		return "redirect:/shipments/showShipmentDetails/" + shipmentId;
	}
	
	@PostMapping("/shipments/updateShipmentStatusFromInsideShipmentAuditDetailsPage")
	public String updateShipmentStatusFromInsideShipmentAuditDetailsPage(@RequestParam String shipmentId, @RequestParam Integer shipStatus, @RequestParam String action) {

		System.out.println("inside /shipments/updateShipmentStatusFromInsideShipmentDetailsPage: " + shipmentId + " - "
				+ shipStatus + " - " + action);

		helperFunctionToUpdateShipmentStatus(shipmentId, shipStatus, action);

		return "redirect:/shipments/showShipmentAuditDetails/" + shipmentId;
	}

	public void helperFunctionToUpdateShipmentStatus(String shipmentId, Integer shipStatus, String action) {

		Shipments shipment = shipmentsRepo.findById(shipmentId)
				.orElseThrow(() -> new RuntimeException("Shipment not found"));

		System.out.println("Action clicked: " + action);
		System.out.println("Current status: " + shipStatus);

		String shipmentStatusAndDesc = "THIS IS shipmentStatusAndDesc";

		switch (action) {

		case "PICK":
			shipment.setShipStatus(1200);
			shipmentStatusAndDesc = "1200 - Picked";
			break;

		case "PACK":
			shipment.setShipStatus(1300);
			shipmentStatusAndDesc = "1300 - Packed";
			break;
		case "SHIP":
			shipment.setShipStatus(1400);
			shipmentStatusAndDesc = "1400 - Shipped";
			break;
		case "CANCEL":
			shipment.setShipStatus(9000);
			shipmentStatusAndDesc = "9000 - Cancelled";
			break;

		default:
			throw new RuntimeException("Invalid action");
		}

		// snsService.publishShipmentStatus(shipmentId, shipmentStatusAndDesc);

		sqsService.sendShipmentStatus(shipmentId, shipmentStatusAndDesc);
		
		shipmentsRepo.save(shipment);

	}

	@GetMapping("/shipments/showShipmentAuditDetails/{shipmentId}")
	public String showShipmentAuditDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		System.out.println(" inside /shipments/showShipmentAuditDetails/{shipmentId} with shipmentId: " + shipmentId);
		Optional<Shipments> singleShipment = shipmentsRepo.findById(shipmentId);

		if (singleShipment.isPresent()) {
			System.out.println("inside IF block");
			List<Map<String, String>> singleShipmentAuditDetails = dynamoDbService
					.getShipmentAudiyByShipmentIdAsPartitionKey("ShipmentStatusUpdateEvents", shipmentId);
			System.out.println(" singleShipmentAuditDetails inside Controller: " + singleShipmentAuditDetails);

			model.addAttribute("shipment", singleShipment.get());
			model.addAttribute("shipmentAudit", singleShipmentAuditDetails);
			return "show-shipment-audit";
		} else {
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesnt exist !!!");
			return "redirect:/shipments/";
		}
	}

}