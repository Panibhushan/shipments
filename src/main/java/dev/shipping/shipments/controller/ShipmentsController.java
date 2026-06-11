package dev.shipping.shipments.controller;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.CreateShipmentRequestWithLinesAndAddress;
import dev.shipping.shipments.model.ShipmentLines;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.service.CustomersService;
import dev.shipping.shipments.service.ShipmentLinesService;
import dev.shipping.shipments.service.ShipmentsService;
import dev.shipping.shipments.service.WarehousesService;
import dev.shipping.shipments.utils.MyCustomUtils;
import dev.shipping.shipments.repo.ShipmentsRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.shipping.shipments.model.Warehouses;

/**
 * Handles all HTTP requests related to shipments.
 *
 * Responsibilities: - Parse and validate incoming HTTP parameters - Delegate
 * all business logic to ShipmentsService - Populate the view model or build the
 * JSON response - Redirect with flash messages on form submissions
 *
 * This controller intentionally contains NO business logic. Any domain
 * decisions (validation, inventory checks, status rules) belong in
 * ShipmentsService.
 */
@Controller
public class ShipmentsController {

	private static final Logger log = LoggerFactory.getLogger(ShipmentsController.class);

	private final ShipmentsService shipmentsService;
	private final CustomersService customersService;
	private final WarehousesService warehousesService;
	private final ShipmentLinesService shipmentLinesService;

	private final ShipmentsRepository shipmentsRepo;

	public ShipmentsController(ShipmentsService shipmentsService, CustomersService customersService,
			WarehousesService warehousesService, ShipmentLinesService shipmentLinesService,
			ShipmentsRepository shipmentsRepo) {
		this.shipmentsService = shipmentsService;
		this.customersService = customersService;
		this.warehousesService = warehousesService;
		this.shipmentLinesService = shipmentLinesService;
		this.shipmentsRepo = shipmentsRepo;
	}

	// ─────────────────────────────────────────────
	// HOME / LIST
	// ─────────────────────────────────────────────

	/** Renders the application home page. */
	@GetMapping("/")
	public String homeWithJustSlashinUrl() {
		log.info("GET / → redirecting to index");
		return "index";
	}

	/**
	 * Renders all shipments ordered by creation time (newest first), with no filter
	 * applied.
	 */
	@GetMapping("/shipments/")
	public String showAllShipments(Model model) {
		log.info("GET /shipments/ → loading all shipments");
		populateShipmentListModel(model, shipmentsService.getAllShipmentsByCreatedTimeDesc(), "ALL", null, false);
		model.addAttribute("activePage", "allShipments");
		return "show-all-shipments";
	}

	/**
	 * Same as showAllShipments but renders the advanced-filter variant of the list
	 * view.
	 */
	@GetMapping("/shipments/advancedShipmentFilters/")
	public String showAllShipmentsWithAdvancedFilters(Model model) {
		log.info("GET /shipments/advancedShipmentFilters/ → loading all shipments (advanced filter view)");
		populateShipmentListModel(model, shipmentsService.getAllShipmentsByCreatedTimeDesc(), "ALL", null, false);
		model.addAttribute("activePage", "allShipments");
		return "show-all-shipments-with-advanced-filters";
	}

	/**
	 * Filters shipments using the basic filter form (shipmentId, customerId,
	 * warehouseId, status). A blank shipmentId is treated as "ALL" (no filter).
	 */
	@GetMapping("/shipments/showShipmentsByFilters")
	public String showShipmentsByBasicFilters(@RequestParam(required = false, defaultValue = "") String shipmentId,
			@RequestParam(required = false, defaultValue = "ALL") String customerId,
			@RequestParam(required = false, defaultValue = "ALL") String shipmentStatus,
			@RequestParam(required = false, defaultValue = "ALL") String warehouseId, Model model) {

		// Normalise: blank input from the form means "no filter on this field"
		String effectiveShipmentId = shipmentId.isBlank() ? "ALL" : shipmentId;

		log.info("GET /shipments/showShipmentsByFilters → shipmentId={}, customerId={}, warehouseId={}, status={}",
				effectiveShipmentId, customerId, warehouseId, shipmentStatus);

		List<Shipments> shipmentsList = shipmentsService.getShipmentList(effectiveShipmentId, customerId, warehouseId,
				shipmentStatus);

		log.info("Basic filter returned {} shipment(s)", shipmentsList.size());

		populateShipmentListModel(model, shipmentsList, customerId, warehouseId, true);
		model.addAttribute("enteredShipmentId", effectiveShipmentId.equals("ALL") ? "" : effectiveShipmentId);
		model.addAttribute("selectedStatus", shipmentStatus.equals("ALL") ? "" : shipmentStatus);
		return "show-all-shipments";
	}

	/**
	 * Filters shipments using the advanced filter form, which supports status
	 * ranges, date ranges, and item-level filtering in addition to the basic
	 * fields. "ALL" is the sentinel value meaning "no filter on this field".
	 */
	@GetMapping("/shipments/showShipmentsByAdvancedFilters")
	public String showShipmentsByAdvancedFilters(
			@RequestParam(required = false, defaultValue = "ALL") String shipmentId,
			@RequestParam(required = false, defaultValue = "ALL") String customerId,
			@RequestParam(required = false, defaultValue = "ALL") String warehouseId,
			@RequestParam(required = false, defaultValue = "ALL") String statusFrom,
			@RequestParam(required = false, defaultValue = "ALL") String statusTo,
			@RequestParam(required = false, defaultValue = "ALL") String createdFrom,
			@RequestParam(required = false, defaultValue = "ALL") String createdTo,
			@RequestParam(required = false, defaultValue = "ALL") String itemId, Model model) {

		log.info(
				"GET /shipments/showShipmentsByAdvancedFilters → shipmentId={}, customerId={}, warehouseId={}, "
						+ "statusFrom={}, statusTo={}, createdFrom={}, createdTo={}, itemId={}",
				shipmentId, customerId, warehouseId, statusFrom, statusTo, createdFrom, createdTo, itemId);

		List<Shipments> shipmentsList = shipmentsService.getShipmentListByAdvancedFilters(shipmentId, customerId,
				warehouseId, statusFrom, statusTo, createdFrom, createdTo, itemId);

		log.info("Advanced filter returned {} shipment(s)", shipmentsList.size());

		populateShipmentListModel(model, shipmentsList, customerId, warehouseId, true);
		model.addAttribute("selectedStatusFrom", statusFrom);
		model.addAttribute("selectedStatusTo", statusTo);
		model.addAttribute("enteredShipmentId", shipmentId.equals("ALL") ? "" : shipmentId);
		model.addAttribute("selectedCreatedFrom", createdFrom.equals("ALL") ? "" : createdFrom);
		model.addAttribute("selectedCreatedTo", createdTo.equals("ALL") ? "" : createdTo);
		model.addAttribute("selectedItemId", itemId.equals("ALL") ? "" : itemId);
		return "show-all-shipments-with-advanced-filters";
	}

	// ─────────────────────────────────────────────
	// CREATE SHIPMENT
	// ─────────────────────────────────────────────

	/**
	 * Backward-compatibility redirect: any page still linking to the old URL
	 * /goToCreateShipmentPage will be transparently sent to the current URL.
	 */
	@GetMapping("/shipments/goToCreateShipmentPage")
	public String goToCreateShipmentPage() {
		log.info("GET /shipments/goToCreateShipmentPage → redirecting to /shipments/createShipmentPage");
		return "redirect:/shipments/createShipmentPage";
	}

	/**
	 * Renders the create-shipment form, pre-loading the list of active, valid
	 * customers.
	 */
	@GetMapping("/shipments/createShipmentPage")
	public String addShipmentsPage(Model model) {
		log.info("GET /shipments/createShipmentPage → rendering create shipment form");
		model.addAttribute("shipment", new Shipments());
		model.addAttribute("customers", shipmentsService.getActiveAndValidCustomers());
		model.addAttribute("activePage", "createShipment");
		return "create-shipment-with-lines-and-address";
	}

	/**
	 * AJAX endpoint: returns warehouses available for a given customer. Called
	 * dynamically when the user selects a customer on the create-shipment form.
	 */
	@GetMapping("/shipments/getWarehousesByCustomer")
	public ResponseEntity<List<Warehouses>> getWarehousesByCustomer(@RequestParam String customerId) {
		log.info("GET /shipments/getWarehousesByCustomer → customerId={}", customerId);
		List<Warehouses> warehouses = shipmentsService.getWarehousesByCustomer(customerId);
		log.info("Returning {} warehouse(s) for customer {}", warehouses.size(), customerId);
		return ResponseEntity.ok(warehouses);
	}

	// ─────────────────────────────────────────────
	// VIEW SHIPMENT DETAILS
	// ─────────────────────────────────────────────

	/**
	 * Renders the shipment detail page, including its lines and formatted delivery
	 * address. Redirects to the list page with an error message if the shipment ID
	 * does not exist.
	 */
	@GetMapping("/shipments/showShipmentDetails/{shipmentId}")
	public String showShipmentDetails(@PathVariable String shipmentId, Model model,
			RedirectAttributes redirectAttributes) {

		log.info("GET /shipments/showShipmentDetails/{}", shipmentId);

		Optional<Shipments> shipment = shipmentsService.getShipmentById(shipmentId);

		if (shipment.isEmpty()) {
			log.warn("Shipment not found: {}", shipmentId);
			redirectAttributes.addFlashAttribute("bgColor", "#d95f6c");
			redirectAttributes.addFlashAttribute("textColor", "#ffffff");
			redirectAttributes.addFlashAttribute("msg", "Shipment " + shipmentId + " doesn't exist!");
			return "redirect:/shipments/";
		}

		Optional<Address> address = shipmentsService.getShipingAddressById(shipment.get().getAddressId());

		log.info("Rendering details for shipmentId={}, addressId={}", shipmentId, shipment.get().getAddressId());

		model.addAttribute("shipment", shipment.get());
		model.addAttribute("address", MyCustomUtils.getFormattedAddress(address.get()));
		model.addAttribute("shipmentLines", shipmentLinesService.getShipmentLinesByShipmentId(shipmentId));
		return "show-shipment-details-with-lines-and-address";
	}

	// ─────────────────────────────────────────────
	// UPDATE SHIPMENT STATUS
	// ─────────────────────────────────────────────

	/**
	 * Handles a status-change action (PICK / PACK / SHIP / CANCEL) on an existing
	 * shipment. The cancellationReason field is only relevant when action="CANCEL".
	 * Redirects back to the shipment detail page with a success or error banner.
	 */
	@PostMapping("/shipments/updateShipmentStatus")
	public String updateShipmentStatus(@RequestParam String shipmentId, @RequestParam Integer shipStatus,
			@RequestParam String action, @RequestParam String customerId, @RequestParam String warehouseId,
			@RequestParam(required = false, defaultValue = "") String cancellationReason,
			RedirectAttributes redirectAttributes) {

		log.info("POST /shipments/updateShipmentStatus → shipmentId={}, action={}, customerId={}, warehouseId={}",
				shipmentId, action, customerId, warehouseId);

		if ("CANCEL".equals(action)) {
			log.info("Cancellation reason for shipmentId={}: {}", shipmentId, cancellationReason);
		}

		String response = shipmentsService.updateShipmentStatus(shipmentId, shipStatus, action, customerId, warehouseId,
				cancellationReason);

		boolean isSuccess = response.contains("SUCCESS");

		if (isSuccess) {
			log.info("Status update succeeded for shipmentId={}, action={}: {}", shipmentId, action, response);
		} else {
			log.warn("Status update failed for shipmentId={}, action={}: {}", shipmentId, action, response);
		}

		redirectAttributes.addFlashAttribute("msg", response);
		redirectAttributes.addFlashAttribute("autoHide", isSuccess);
		redirectAttributes.addFlashAttribute("bgColor", isSuccess ? "#d4edda" : "#d95f6c");
		redirectAttributes.addFlashAttribute("textColor", isSuccess ? "#155724" : "#ffffff");
		if (!isSuccess) {
			// Disable action buttons on the detail page to prevent repeated failed attempts
			redirectAttributes.addFlashAttribute("disableButtonActions", true);
		}
		redirectAttributes.addFlashAttribute("customerId", customerId);
		redirectAttributes.addFlashAttribute("warehouseId", warehouseId);

		return "redirect:/shipments/showShipmentDetails/" + shipmentId;
	}

	// ─────────────────────────────────────────────
	// SHIPMENT AUDIT
	// ─────────────────────────────────────────────

	/**
	 * Returns the audit trail for a shipment from DynamoDB as a JSON array. Each
	 * entry represents a status-change event with its timestamp and metadata.
	 */
	@GetMapping("/shipments/getShipmentAudit/{shipmentId}")
	@ResponseBody
	public List<Map<String, String>> getShipmentAudit(@PathVariable String shipmentId) {
		log.info("GET /shipments/getShipmentAudit/{}", shipmentId);
		List<Map<String, String>> audit = shipmentsService.getShipmentAudit(shipmentId);
		log.info("Returning {} audit event(s) for shipmentId={}", audit.size(), shipmentId);
		return audit;
	}

	// ─────────────────────────────────────────────
	// INVENTORY CHECK
	// ─────────────────────────────────────────────

	/**
	 * AJAX endpoint: checks inventory availability for a list of shipment lines.
	 * Groups lines by (lineNo, itemId, UOM), then returns which warehouses have
	 * enough stock to fulfil each group.
	 */
	@PostMapping("/shipments/checkInventoryAvailability")
	@ResponseBody
	public List<Map<String, String>> checkInventoryAvailability(@RequestParam String customerId, @RequestParam String zipCode,
			@RequestBody List<ShipmentLines> lines) {

		log.info("POST /shipments/checkInventoryAvailability → customerId={}, zipCode={}, lineCount={}", customerId, zipCode, lines.size());
		List<Map<String, String>> result = shipmentsService.checkInventoryAvailability(customerId, lines, zipCode);
		log.info("Inventory check for customerId={} returned {} warehouse-line combination(s)", customerId,
				result.size());
		return result;
	}

	/**
	 * checks inventory availability for a list of shipment lines when
	 * pick/pack/ship action is performed.
	 */
	@GetMapping("/shipments/checkInventoryAvailabilityBeforeProcessingShipment/{shipmentId}")
	public String checkInventoryAvailabilityBeforeProcessingShipment(@PathVariable String shipmentId,
			@RequestParam String customerId, @RequestParam String warehouseId, RedirectAttributes redirectAttributes) {

		log.info(
				"POST /shipments/checkInventoryAvailabilityBeforeProcessingShipment → shipmentId={}, customerId={}, warehouseId={} ",
				shipmentId, customerId, warehouseId);
		boolean hasShortage = shipmentsService.hasShortageToUpdateShipmentStatus(shipmentId, customerId, warehouseId);
		log.info(
				"hasShortage={} retruned from Service to Controller check shipmentId={}, customerId={}, warehouseId={} ",
				hasShortage, shipmentId, customerId, warehouseId);

		String message = "Shipment Cannot be processed due to shortage of inventory. Refer shipment lines for shortage item & quantity.",
				bgColor = "#d95f6c", textColor = "#ffffff";

		// if no shortage was detected for any lines, we are changing hasShortage flag
		// for shipment to N
		if (!hasShortage) {

			String result = shipmentsService.setHasShortageForShipmentId(shipmentId, "N");

			if (result.equals("SUCCESS")) {
				log.info(
						"shipmentsService.setHasShortageForShipmentId has returned {} for setting HasShortage to N for shipmentId={} ",
						result, shipmentId);
				message = "Inventory available. You can process the shipemt now";
				bgColor = "#d4edda";
				textColor = "#155724";

			} else {
				log.error(
						"shipmentsService.setHasShortageForShipmentId has returned error as ={} for setting HasShortage to N for shipmentId={} ",
						result, shipmentId);
			}

		}

		redirectAttributes.addFlashAttribute("bgColor", bgColor);
		redirectAttributes.addFlashAttribute("textColor", textColor);
		redirectAttributes.addFlashAttribute("msg", message);

		return "redirect:/shipments/showShipmentDetails/" + shipmentId;
	}

	/**
	 * Creates a shipment header + lines + delivery address in one call. This is the
	 * primary API endpoint for creating a fully-formed shipment.
	 */
	@PostMapping("/shipments/createShipmentWithLinesAndAddress")
	@ResponseBody
	public String createShipmentWithLinesAndAddress(@RequestParam String customerId, @RequestParam String warehouseId,
			@RequestBody CreateShipmentRequestWithLinesAndAddress request) {

		log.info("POST /shipments/createShipmentWithLinesAndAddress → customerId={}, warehouseId={}, lineCount={}",
				customerId, warehouseId, request.getLines().size());

		String result = shipmentsService.createShipmentWithLinesAndAddress(customerId, warehouseId, request);

		if (result.startsWith("SUCCESSFULLY")) {
			log.info("createShipmentWithLinesAndAddress succeeded: {}", result);
		} else {
			log.warn("createShipmentWithLinesAndAddress failed: {}", result);
		}

		return result;
	}

	// ─────────────────────────────────────────────
	// PRIVATE HELPERS
	// ─────────────────────────────────────────────

	/**
	 * Populates model attributes that are shared across the basic and advanced
	 * shipment list views, including the shipments list, customer dropdown,
	 * warehouse dropdown (scoped to the selected customer if one is chosen), and
	 * whether any filter has been applied.
	 *
	 * @param model         Spring MVC model to populate
	 * @param shipments     the filtered (or full) list of shipments to display
	 * @param customerId    selected customer, or "ALL" if none
	 * @param warehouseId   selected warehouse, or "ALL" / null if none
	 * @param filterApplied true if the user submitted a filter form
	 */
	private void populateShipmentListModel(Model model, List<Shipments> shipments, String customerId,
			String warehouseId, boolean filterApplied) {

		model.addAttribute("shipments", shipments);
		model.addAttribute("selectedCustomer", customerId);
		model.addAttribute("selectedWarehouse", warehouseId);
		model.addAttribute("customers", customersService.getAllCustomers());
		model.addAttribute("filterApplied", filterApplied);

		// Scope the warehouse dropdown to the selected customer; show all if none
		// selected
		if (customerId == null || customerId.equals("ALL")) {
			model.addAttribute("warehouses", warehousesService.getAllWarehouses());
		} else {
			model.addAttribute("warehouses", shipmentsService.getWarehousesByCustomer(customerId));
		}
	}

	/**
	 * Fetches the shipments that are allocated for a specific item and shows them
	 **/
	@GetMapping("/shipments/showShipmentsAllocatedForItem/{itemCustomerUomWarehouseId}")
	public String showShipmentsAllocatedForItem(@PathVariable String itemCustomerUomWarehouseId, Model model) {

		log.info("ShipmentsController:: /shipments/showShipmentsAllocatedForItem/{itemCustomerUomWarehouseId}: "
				+ itemCustomerUomWarehouseId);

		String[] parts = itemCustomerUomWarehouseId.split("_");
		String itemId = parts[0];
		String customerId = parts[1];
		String itemUom = parts[2];

		List<Map<String, Object>> lmso = shipmentsService.getShipmentsAlloactedForItem(customerId, itemId, itemUom);

		log.info("ShipmentsController:: /shipments/showShipmentsAllocatedForItem/: lmso: " + lmso.toString());

		model.addAttribute("CustomerItemItemUom", List.of(customerId, itemId, itemUom));

		model.addAttribute("shipments", lmso);

		return "show-shipments-allocated-for-item";
	}

}