package dev.shipping.shipments.service;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.CreateShipmentRequestWithLinesAndAddress;
import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.ShipmentLines;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.AddressRepository;
import dev.shipping.shipments.repo.CustomerWarehousesRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.InventoryRepository;
import dev.shipping.shipments.repo.ShipmentLinesRepository;
import dev.shipping.shipments.repo.ShipmentsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import utils.MyCustomUtils;

import org.hibernate.transform.Transformers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Core business logic for shipment operations.
 *
 * Responsibilities: - Validate customers, warehouses, and their relationships
 * before any write - Create / update shipments, shipment lines, and delivery
 * addresses - Manage inventory allocated-quantity bookkeeping - Build dynamic
 * JPQL queries for filtered list views - Orchestrate SNS/SQS notifications on
 * status changes
 *
 * All public mutating methods are @Transactional so that a failure
 * mid-operation rolls back every DB write made in that call.
 */
@Service
public class ShipmentsService {

	private static final Logger log = LoggerFactory.getLogger(ShipmentsService.class);

	/** Date format used when customer.validUpto is stored as a string in the DB. */
	private static final DateTimeFormatter CUSTOMER_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

	@Autowired
	private EntityManager entityManager;

	private final ShipmentsRepository shipmentsRepo;
	private final ShipmentLinesRepository shipmentLinesRepo;
	private final InventoryRepository inventoryRepo;
	private final CustomersRepository customersRepo;
	private final CustomerWarehousesRepository customerWarehousesRepo;
	private final WarehousesRepository warehousesRepo;
	private final SnsPublisherService snsService;
	private final DynamoDbService dynamoDbService;
	private final SqsSenderService sqsService;
	private final ShipmentLinesService shipmentLinesService;
	private final AddressRepository addressRepo;
	private final AddressService addressService;

	public ShipmentsService(ShipmentsRepository shipmentsRepo, CustomersRepository customersRepo,
			CustomerWarehousesRepository customerWarehousesRepo, WarehousesRepository warehousesRepo,
			InventoryRepository inventoryRepo, ShipmentLinesRepository shipmentLinesRepo,
			DynamoDbService dynamoDbService, SnsPublisherService snsService, SqsSenderService sqsService,
			ShipmentLinesService shipmentLinesService, AddressRepository addressRepo, AddressService addressService) {
		this.shipmentsRepo = shipmentsRepo;
		this.customersRepo = customersRepo;
		this.customerWarehousesRepo = customerWarehousesRepo;
		this.warehousesRepo = warehousesRepo;
		this.inventoryRepo = inventoryRepo;
		this.shipmentLinesRepo = shipmentLinesRepo;
		this.dynamoDbService = dynamoDbService;
		this.snsService = snsService;
		this.sqsService = sqsService;
		this.shipmentLinesService = shipmentLinesService;
		this.addressRepo = addressRepo;
		this.addressService = addressService;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	/** Returns all shipments with no ordering guarantee (used internally). */
	public List<Shipments> getAllShipments() {
		return shipmentsRepo.findAll();
	}

	/**
	 * Returns all shipments ordered newest-first, used for the default list view.
	 */
	public List<Shipments> getAllShipmentsByCreatedTimeDesc() {
		return shipmentsRepo.getAllShipmentsByCreatedTimeDesc();
	}

	/**
	 * Looks up a single shipment by its ID; returns empty Optional if not found.
	 */
	public Optional<Shipments> getShipmentById(String shipmentId) {
		return shipmentsRepo.findById(shipmentId);
	}

	/** Looks up the delivery address linked to a shipment. */
	public Optional<Address> getShipingAddressById(String addressId) {
		return addressRepo.findById(addressId);
	}

	/**
	 * Returns only customers whose status is "Active" AND whose contract expiry
	 * (validUpto) is after the start of today. Used to populate the customer
	 * dropdown on the create-shipment form.
	 */
	public List<Customers> getActiveAndValidCustomers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return customersRepo.findByCustomerStatusAndValidUpto("Active", startOfToday);
	}

	/** Returns all warehouses configured for a given customer. */
	public List<Warehouses> getWarehousesByCustomer(String customerId) {
		return customerWarehousesRepo.findAllocatedWarehousesByCustomerId(customerId);
	}

	/** Returns all shipments belonging to a specific customer. */
	public List<Shipments> getShipmentsByCustomer(String customerId) {
		return shipmentsRepo.findShipmentsByCustomer(customerId);
	}

	/** Returns shipments belonging to a specific customer AND warehouse. */
	public List<Shipments> getShipmentsByCustomerAndWarehouse(String customerId, String warehouseId) {
		return shipmentsRepo.findShipmentsByCustomerAndWarehouse(customerId, warehouseId);
	}

	/**
	 * Retrieves the status-change audit trail for a shipment from DynamoDB. Each
	 * entry represents one status event (e.g. CREATED, PICKED, SHIPPED).
	 */
	public List<Map<String, String>> getShipmentAudit(String shipmentId) {
		log.info("Fetching audit trail for shipmentId={}", shipmentId);
		List<Map<String, String>> audit = dynamoDbService
				.getShipmentAudiyByShipmentIdAsPartitionKey("ShipmentStatusUpdateEvents", shipmentId);
		log.info("Found {} audit event(s) for shipmentId={}", audit.size(), shipmentId);
		return audit;
	}

	// ─────────────────────────────────────────────
	// CREATE SHIPMENT
	// ─────────────────────────────────────────────

	/**
	 * Validates the customer and warehouse, persists the shipment header, then
	 * publishes a "CREATED" event to both SNS and SQS.
	 *
	 * Validation order: 1. Customer must exist 2. Customer must be Active with a
	 * non-expired contract 3. Customer must be linked to the requested warehouse
	 *
	 * @return String[2]: [0] = "SUCCESS" | "FAILED", [1] = new shipment ID on
	 *         success, or a user-readable error message (may contain HTML) on
	 *         failure.
	 */
	@Transactional
	public String[] createShipment(Shipments shipment, String customerId, String warehouseId) {

		log.info("createShipment() → customerId={}, warehouseId={}", customerId, warehouseId);

		// ── 1. Customer existence check ──────────────────────────────────────
		Optional<Customers> customer = customersRepo.findById(customerId);
		if (customer.isEmpty()) {
			log.warn("createShipment() failed: customer not found → customerId={}", customerId);
			return new String[] { "FAILED", "Customer: " + customerId + " is not found" };
		}

		// ── 2. Customer active + contract validity check ──────────────────────
		if (!isCustomerActiveAndValid(customer.get())) {
			log.warn("createShipment() failed: customer inactive or contract expired → customerId={}", customerId);
			return new String[] { "FAILED",
					"Customer: " + customerId + " is either Disabled or the contract has expired!"
							+ " &nbsp;&nbsp; <a target=\"_blank\" style=\"color: #4580ed;\""
							+ " href='/customers/showCustomerDetails/" + customerId + "'>View " + customerId + "</a>" };
		}

		// ── 3. Customer-warehouse link check ─────────────────────────────────
		Optional<CustomerWarehouses> customerWarehouses = customerWarehousesRepo
				.findById(customerId + "_" + warehouseId);
		if (customerWarehouses.isEmpty()) {
			log.warn("createShipment() failed: customer-warehouse link missing → customerId={}, warehouseId={}",
					customerId, warehouseId);
			return new String[] { "FAILED",
					"Customer: " + customerId + " is not configured to ship from " + warehouseId + " warehouse!"
							+ " &nbsp;&nbsp; <a target=\"_blank\" style=\"color: #4580ed;\""
							+ " href='/customers/showCustomerDetails/" + customerId + "'>View " + customerId + "</a>" };
		}

		// ── 4. Persist ────────────────────────────────────────────────────────
		shipment.setCustomerId(customerId);
		shipment.setWarehouseId(warehouseId);

		Shipments savedShipment = shipmentsRepo.save(shipment);

		if (savedShipment != null && savedShipment.getShipmentId() != null) {
			log.info("Shipment persisted → shipmentId={}", savedShipment.getShipmentId());

			// Notify downstream systems asynchronously via SNS and SQS
			snsService.publishShipmentStatus(savedShipment.getShipmentId(), "1100 - CREATED",
					"SHIPMENT_CREATED_SUCCESSFULLY");
			sqsService.sendShipmentStatus(savedShipment.getShipmentId(), "1100 - CREATED",
					"SHIPMENT_CREATED_SUCCESSFULLY");

			return new String[] { "SUCCESS", savedShipment.getShipmentId() };
		}

		// Should not normally reach here; repo.save() typically throws on DB error
		log.error("createShipment() failed: shipmentsRepo.save() returned null for customerId={}, warehouseId={}",
				customerId, warehouseId);
		return new String[] { "FAILED", "Failed to create shipment!" };
	}

	/**
	 * Full orchestrator: creates a shipment header, all lines, and the delivery
	 * address in one transaction. This is the primary path for the UI form and the
	 * REST API.
	 */
	@Transactional
	public String createShipmentWithLinesAndAddress(String customerId, String warehouseId,
			CreateShipmentRequestWithLinesAndAddress request) {

		List<ShipmentLines> lines = request.getLines();
		Address deliveryAddress = request.getDeliveryAddress();

		log.info("createShipmentWithLinesAndAddress() → customerId={}, warehouseId={}, lineCount={}", customerId,
				warehouseId, lines.size());

		// ── Step 1: Create shipment header ───────────────────────────────────
		String[] result = createShipment(new Shipments(), customerId, warehouseId);

		if (!result[0].equals("SUCCESS")) {
			log.warn("createShipmentWithLinesAndAddress() aborted: header creation failed");
			return "SHIPMENT_CREATION_FAILED!! <br />" + result[1];
		}

		String shipmentId = result[1];

		// ── Step 2: Create shipment lines and update allocated inventory ──────
		String linesResult = createShipmentLines(customerId, warehouseId, shipmentId, lines);

		if (!linesResult.contains("SUCCESS")) {
			log.warn("createShipmentWithLinesAndAddress() failed on lines → shipmentId={}: {}", shipmentId,
					linesResult);
			return linesResult;
		}

		// ── Step 3: Persist delivery address and link it to the shipment ──────
		createAddress(shipmentId, deliveryAddress);

		log.info("createShipmentWithLinesAndAddress() completed → shipmentId={}", shipmentId);
		return "SUCCESSFULLY_CREATED_SHIPMENT WITH ID|" + shipmentId;
	}

	// ─────────────────────────────────────────────
	// UPDATE SHIPMENT STATUS
	// ─────────────────────────────────────────────

	/**
	 * Validates all preconditions for a status change, then applies the transition.
	 *
	 * Preconditions checked: - Customer exists, is Active, and has a valid contract
	 * - Warehouse exists and is Active - A customer-warehouse link exists
	 *
	 * Supported actions and their resulting status codes: - PICK → 1200 - PACK →
	 * 1300 - SHIP → 1400 (also decrements on-hand inventory) - CANCEL → 9000 (also
	 * releases allocated inventory)
	 *
	 * @return a string starting with "SUCCESS..." on success, or a descriptive
	 *         error message (may contain HTML links) if any precondition fails.
	 */
	@Transactional
	public String updateShipmentStatus(String shipmentId, Integer shipStatus, String action, String customerId,
			String warehouseId, String cancellationReason) {

		log.info("updateShipmentStatus() → shipmentId={}, action={}, customerId={}, warehouseId={}", shipmentId, action,
				customerId, warehouseId);

		// Throws RuntimeException (→ 500) if the shipment row is missing entirely
		Shipments shipment = shipmentsRepo.findById(shipmentId)
				.orElseThrow(() -> new RuntimeException("Shipment not found: " + shipmentId));

		// ── Validate customer ─────────────────────────────────────────────────
		Optional<Customers> customer = customersRepo.findById(customerId);
		if (customer.isEmpty()) {
			log.warn("updateShipmentStatus() failed: customer not found → customerId={}", customerId);
			return "Customer: " + customerId + " is not found!";
		}

		// ── Validate warehouse and customer-warehouse link ────────────────────
		Optional<Warehouses> warehouse = warehousesRepo.findById(warehouseId);
		Optional<CustomerWarehouses> customerWarehouses = customerWarehousesRepo
				.findById(customerId + "_" + warehouseId);

		boolean isWarehouseActive = warehouse.isPresent() && "Active".equals(warehouse.get().getWarehouseStatus());
		boolean isCustomerValid = isCustomerActiveAndValid(customer.get());
		boolean isCustomerWarehouseLinked = customerWarehouses.isPresent();

		log.debug("updateShipmentStatus() precondition check → isCustomerValid={}, isWarehouseActive={}, isLinked={}",
				isCustomerValid, isWarehouseActive, isCustomerWarehouseLinked);

		if (isCustomerValid && isWarehouseActive && isCustomerWarehouseLinked) {
			// All checks passed — apply the status transition
			return applyStatusTransition(shipment, shipmentId, action, cancellationReason);
		}

		// At least one precondition failed — build a detailed error for the UI
		log.warn("updateShipmentStatus() precondition(s) failed → shipmentId={}, action={}", shipmentId, action);
		return buildStatusUpdateErrorMessage(shipmentId, customerId, warehouseId, isCustomerValid, isWarehouseActive,
				isCustomerWarehouseLinked);
	}

	// ─────────────────────────────────────────────
	// FILTER QUERIES
	// ─────────────────────────────────────────────

	/**
	 * Builds and executes a dynamic JPQL query for the basic filter form. Any field
	 * passed as "ALL" is excluded from the WHERE clause entirely, so the query only
	 * filters on the fields the user actually specified.
	 */
	@Transactional
	public List<Shipments> getShipmentList(String shipmentId, String customerId, String warehouseId,
			String shipStatus) {

		StringBuilder query = new StringBuilder("SELECT s FROM Shipments s");
		List<String> conditions = new ArrayList<>();

		// Only include a condition for fields the user actually filtered on
		if (!shipmentId.equals("ALL"))
			conditions.add("s.shipmentId = :shipmentId");
		if (!customerId.equals("ALL"))
			conditions.add("s.customerId = :customerId");
		if (!warehouseId.equals("ALL"))
			conditions.add("s.warehouseId = :warehouseId");
		if (!shipStatus.equals("ALL"))
			conditions.add("s.shipStatus = :shipStatus");

		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		log.debug("getShipmentList() → JPQL: {}", query);

		TypedQuery<Shipments> typedQuery = entityManager.createQuery(query.toString(), Shipments.class);

		if (!shipmentId.equals("ALL"))
			typedQuery.setParameter("shipmentId", shipmentId);
		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!warehouseId.equals("ALL"))
			typedQuery.setParameter("warehouseId", warehouseId);
		if (!shipStatus.equals("ALL"))
			typedQuery.setParameter("shipStatus", shipStatus);

		List<Shipments> results = typedQuery.getResultList();
		log.info("getShipmentList() returned {} result(s)", results.size());
		return results;
	}

	/**
	 * Builds and executes a dynamic JPQL query for the advanced filter form.
	 * Supports all basic filters plus: - statusFrom / statusTo: inclusive
	 * status-code range - dateFrom / dateTo: creation date range (string →
	 * LocalDateTime conversion) - itemId: filters to shipments that contain a
	 * specific item
	 *
	 * Date handling: - dateFrom is treated as start-of-day (e.g. 2026-05-01 →
	 * 2026-05-01 00:00:00) - dateTo is treated as start-of-next-day (e.g.
	 * 2026-05-01 → 2026-05-02 00:00:00) so that all records created on dateTo
	 * itself are included.
	 */
	@Transactional
	public List<Shipments> getShipmentListByAdvancedFilters(String shipmentId, String customerId, String warehouseId,
			String statusFrom, String statusTo, String dateFrom, String dateTo, String itemId) {

		StringBuilder query = new StringBuilder("SELECT s FROM Shipments s");
		List<String> conditions = new ArrayList<>();

		if (!shipmentId.equals("ALL"))
			conditions.add("s.shipmentId = :shipmentId");
		if (!customerId.equals("ALL"))
			conditions.add("s.customerId = :customerId");
		if (!warehouseId.equals("ALL"))
			conditions.add("s.warehouseId = :warehouseId");
		if (!statusFrom.equals("ALL"))
			conditions.add("s.shipStatus >= :statusFrom");
		if (!statusTo.equals("ALL"))
			conditions.add("s.shipStatus <= :statusTo");
		if (!dateFrom.equals("ALL"))
			conditions.add("s.createdAt >= :dateFrom");
		if (!dateTo.equals("ALL"))
			conditions.add("s.createdAt <= :dateTo");
		// Subquery: include only shipments that have a line for the specified item
		if (!itemId.equals("ALL"))
			conditions.add("s.shipmentId IN (SELECT sl.shipmentId FROM ShipmentLines sl WHERE sl.itemId = :itemId)");

		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		log.debug("getShipmentListByAdvancedFilters() → JPQL: {}", query);

		TypedQuery<Shipments> typedQuery = entityManager.createQuery(query.toString(), Shipments.class);

		if (!shipmentId.equals("ALL"))
			typedQuery.setParameter("shipmentId", shipmentId);
		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!warehouseId.equals("ALL"))
			typedQuery.setParameter("warehouseId", warehouseId);
		if (!statusFrom.equals("ALL"))
			typedQuery.setParameter("statusFrom", statusFrom);
		if (!statusTo.equals("ALL"))
			typedQuery.setParameter("statusTo", statusTo);
		// dateFrom → 2026-05-01 00:00:00 (inclusive lower bound)
		if (!dateFrom.equals("ALL"))
			typedQuery.setParameter("dateFrom", LocalDate.parse(dateFrom).atStartOfDay());
		// dateTo → next day 00:00:00 (exclusive upper bound, makes the range inclusive
		// of dateTo)
		if (!dateTo.equals("ALL"))
			typedQuery.setParameter("dateTo", LocalDate.parse(dateTo).plusDays(1).atStartOfDay());
		if (!itemId.equals("ALL"))
			typedQuery.setParameter("itemId", itemId);

		List<Shipments> results = typedQuery.getResultList();
		log.info("getShipmentListByAdvancedFilters() returned {} result(s)", results.size());
		return results;
	}

	// ─────────────────────────────────────────────
	// INVENTORY
	// ─────────────────────────────────────────────

	/**
	 * Checks inventory availability for a list of proposed shipment lines.
	 *
	 * Algorithm: 1. Group lines by (lineNo, itemId, UOM) and sum their quantities —
	 * this handles duplicate line entries for the same item-UOM pair. 2. For each
	 * group, query inventory for warehouses that have enough available stock to
	 * fulfil the total requested quantity. 3. Flatten the results into a list of
	 * maps suitable for a JSON response.
	 *
	 * @return list of {lineNumber, itemId, itemUom, requestedQty, availableQty,
	 *         warehouseId, warehouseName} for each line that has sufficient stock.
	 */
	public List<Map<String, String>> checkInventoryAvailability(String customerId, List<ShipmentLines> lines) {

		log.info("checkInventoryAvailability() → customerId={}, lineCount={}", customerId, lines.size());

		// ── Step 1: Group and sum quantities per unique line key ───────────────
		// Key format: "lineNo::itemId::itemUom"
		Map<String, Integer> groupedByLine = lines.stream()
				.collect(Collectors.groupingBy(
						line -> line.getLineNo() + "::" + safeItemId(line) + "::" + line.getItemUom(),
						Collectors.summingInt(ShipmentLines::getQuantity)));

		log.debug("checkInventoryAvailability() → grouped line keys: {}", groupedByLine.keySet());

		// ── Step 2: Find warehouses that can fulfil each line ─────────────────
		Map<String, List<Inventory>> inventoryByLine = new HashMap<>();
		for (Map.Entry<String, Integer> entry : groupedByLine.entrySet()) {
			String[] parts = entry.getKey().split("::");
			String lineNo = parts[0];
			String itemId = parts[1];
			String itemUom = parts[2];
			int totalQty = entry.getValue();

			log.debug("Checking inventory for lineNo={}, itemId={}, itemUom={}, requiredQty={}", lineNo, itemId,
					itemUom, totalQty);

			List<Inventory> available = getWarehousesWithSufficientInventory(customerId, itemId, itemUom, totalQty);

			if (available != null && !available.isEmpty()) {
				inventoryByLine.put(lineNo, available);
				log.debug("lineNo={} → {} warehouse(s) with sufficient stock", lineNo, available.size());
			} else {
				log.warn("No warehouse has sufficient inventory for lineNo={}, itemId={}, itemUom={}, requiredQty={}",
						lineNo, itemId, itemUom, totalQty);
			}
		}

		// ── Step 3: Flatten to a list of response maps ────────────────────────
		List<Map<String, String>> result = new ArrayList<>();
		for (Map.Entry<String, List<Inventory>> entry : inventoryByLine.entrySet()) {
			String lineNo = entry.getKey();
			for (Inventory inv : entry.getValue()) {
				String itemId = inv.getItemId() != null ? inv.getItemId() : "";
				String itemUom = inv.getItemUom() != null ? inv.getItemUom() : "";
				String warehouseId = inv.getWarehouseId() != null ? inv.getWarehouseId() : "";
				String requestedQty = String
						.valueOf(groupedByLine.getOrDefault(lineNo + "::" + itemId + "::" + itemUom, 0));
				String availableQty = String.valueOf(inv.getAvailableQuantity());
				String warehouseName = warehousesRepo.findById(warehouseId).map(Warehouses::getWarehouseName)
						.orElse("");

				result.add(Map.of("lineNumber", lineNo, "itemId", itemId, "itemUom", itemUom, "requestedQty",
						requestedQty, "availableQty", availableQty, "warehouseId", warehouseId, "warehouseName",
						warehouseName));
			}
		}

		log.info("checkInventoryAvailability() → returning {} warehouse-line combination(s)", result.size());
		return result;
	}

	/**
	 * Increments the allocatedQuantity for a specific inventory record. Called
	 * immediately after a shipment line is created, to "reserve" stock.
	 *
	 * @param itemCustomerUomWarehouseId composite inventory PK:
	 *                                   itemId_customerId_uom_warehouseId
	 * @param shipmentLineQuantity       quantity to add to the allocated counter
	 */
	public void updateInventoryAllocatedQuantity(String itemCustomerUomWarehouseId, int shipmentLineQuantity) {
		log.debug("updateInventoryAllocatedQuantity() → key={}, qty={}", itemCustomerUomWarehouseId,
				shipmentLineQuantity);
		Inventory inv = inventoryRepo.findById(itemCustomerUomWarehouseId).get();
		inv.setAllocatedQuantity(inv.getAllocatedQuantity() + shipmentLineQuantity);
		inventoryRepo.save(inv);
	}

	// ─────────────────────────────────────────────
	// SHIPMENT LINES & ADDRESS
	// ─────────────────────────────────────────────

	/**
	 * Persists all shipment lines for a given shipment and updates the allocated
	 * inventory quantity for each line.
	 *
	 * Stops and returns an error message on the first line that fails to save,
	 * relying on the caller's @Transactional boundary to roll back everything.
	 */
	public String createShipmentLines(String customerId, String warehouseId, String shipmentId,
			List<ShipmentLines> lines) {

		log.info("createShipmentLines() → shipmentId={}, lineCount={}", shipmentId, lines.size());

		for (ShipmentLines line : lines) {
			// Build a clean entity; don't reuse the incoming object to avoid
			// accidental field bleed from the request payload
			ShipmentLines toCreate = new ShipmentLines();
			toCreate.setShipmentId(shipmentId);
			toCreate.setLineNo(line.getLineNo());
			toCreate.setItemId(line.getItemId());
			toCreate.setItemUom(line.getItemUom());
			toCreate.setQuantity(line.getQuantity());
			toCreate.setShortageQuantity(0); // default; updated later during picking

			String createdId = shipmentLinesService.createShipmentLines(toCreate);
			if (createdId == null) {
				log.error("createShipmentLines() failed to persist line → shipmentId={}, itemId={}, uom={}, qty={}",
						shipmentId, line.getItemId(), line.getItemUom(), line.getQuantity());
				return "SHIPMENT_CREATION_FAILED: FAILED_TO_CREATE_SHIPMENT_LINE FOR ITEM: " + line.getItemId()
						+ ", UOM: " + line.getItemUom() + ", QTY: " + line.getQuantity();
			}

			log.debug("Shipment line created → lineId={}, shipmentId={}, itemId={}", createdId, shipmentId,
					line.getItemId());

			// Reserve this quantity in inventory so it isn't allocated to another shipment
			String inventoryKey = line.getItemId() + "_" + customerId + "_" + line.getItemUom() + "_" + warehouseId;
			updateInventoryAllocatedQuantity(inventoryKey, line.getQuantity());
		}

		log.info("createShipmentLines() completed successfully → shipmentId={}", shipmentId);
		return "SHIPMENT_LINES_CREATED_SUCCESSFULLY";
	}

	/**
	 * Resolves or creates a delivery address and links it to the shipment. Uses a
	 * hash of the address fields as the PK to deduplicate identical addresses. If
	 * address creation fails, the shipment is left without an addressId (non-fatal;
	 * the caller's transaction will still commit).
	 */
	public void createAddress(String shipmentId, Address deliveryAddress) {

		log.info("createAddress() → shipmentId={}", shipmentId);

		// buildAddressFields normalises the address and returns Map<hash → Address>
		Map<String, Address> addrMap = MyCustomUtils.buildAddressFields(deliveryAddress);
		String addrHash = addrMap.keySet().iterator().next();
		Address address = addrMap.getOrDefault(addrHash, new Address());

		String addressId = addressService.createAddress(addrHash, address);

		if (addressId == null || addressId.isEmpty()) {
			log.warn("createAddress() → address service returned empty ID for shipmentId={}", shipmentId);
			return;
		}

		log.info("Address persisted → addressId={}, linking to shipmentId={}", addressId, shipmentId);

		// Link the new address to the shipment and set the human-readable shipTo field
		shipmentsRepo.findById(shipmentId).ifPresent(shipment -> {
			shipment.setAddressId(addressId);
			shipment.setShipTo(deliveryAddress.getState() + ", IN");
			shipmentsRepo.save(shipment);
		});
	}

	// ─────────────────────────────────────────────
	// PRIVATE HELPERS
	// ─────────────────────────────────────────────

	/**
	 * Returns true if the customer's status is "Active" AND their contract expiry
	 * date (validUpto) is after the start of today.
	 */
	private boolean isCustomerActiveAndValid(Customers customer) {
		LocalDateTime validUpto = LocalDateTime.parse(customer.getValidUpto(), CUSTOMER_DATE_FMT);
		boolean isValid = validUpto.isAfter(LocalDate.now().atStartOfDay())
				&& "Active".equals(customer.getCustomerStatus());
		log.debug("isCustomerActiveAndValid() → customerId={}, validUpto={}, result={}", customer.getCustomerId(),
				validUpto, isValid);
		return isValid;
	}

	/**
	 * Applies the requested status transition to the shipment entity, sends the new
	 * status to SQS, and persists the updated shipment.
	 *
	 * SHIP and CANCEL also trigger inventory release via releaseInventory().
	 *
	 * @return the updatedStatus string (e.g. "SHIPMENT_PICKED_SUCCESSFULLY")
	 * @throws RuntimeException if the action string is not one of the known values
	 */
	private String applyStatusTransition(Shipments shipment, String shipmentId, String action,
			String cancellationReason) {

		log.info("applyStatusTransition() → shipmentId={}, action={}", shipmentId, action);

		String statusAndDesc, updatedStatus, comment;

		switch (action) {
		case "PICK":
			shipment.setShipStatus(1200);
			statusAndDesc = "1200 - PICKED";
			updatedStatus = comment = "SHIPMENT_PICKED_SUCCESSFULLY";
			break;

		case "PACK":
			shipment.setShipStatus(1300);
			statusAndDesc = "1300 - PACKED";
			updatedStatus = comment = "SHIPMENT_PACKED_SUCCESSFULLY";
			break;

		case "SHIP":
			shipment.setShipStatus(1400);
			statusAndDesc = "1400 - SHIPPED";
			updatedStatus = comment = "SHIPMENT_SHIPPED_SUCCESSFULLY";
			// Decrement both allocated qty AND on-hand qty
			releaseInventory(shipmentId, 1400);
			break;

		case "CANCEL":
			shipment.setShipStatus(9000);
			statusAndDesc = "9000 - CANCELLED";
			updatedStatus = "SHIPMENT_CANCELLED";
			comment = "SHIPMENT_CANCELLED with reason: " + cancellationReason;
			// Only release allocated qty; on-hand qty stays the same (goods not shipped)
			releaseInventory(shipmentId, 9000);
			break;

		default:
			log.error("applyStatusTransition() received unknown action={} for shipmentId={}", action, shipmentId);
			throw new RuntimeException("Invalid action: " + action);
		}

		sqsService.sendShipmentStatus(shipmentId, statusAndDesc, comment);
		shipmentsRepo.save(shipment);

		log.info("applyStatusTransition() completed → shipmentId={}, newStatus={}", shipmentId, statusAndDesc);
		return updatedStatus;
	}

	/**
	 * Releases inventory when a shipment is shipped (status 1400) or cancelled
	 * (status 9000).
	 *
	 * For BOTH ship and cancel: - allocatedQuantity is decremented by the shipment
	 * line quantity (the reservation made at shipment creation is lifted).
	 *
	 * For SHIP only (1400): - quantity (on-hand) is also decremented, because goods
	 * have physically left the warehouse.
	 *
	 * The shipmentId encodes customerId and warehouseId as the first two
	 * "_"-separated segments, e.g. "CUST001_WH01_20240601_001".
	 */
	private void releaseInventory(String shipmentId, int shipStatus) {

		log.info("releaseInventory() → shipmentId={}, shipStatus={}", shipmentId, shipStatus);

		// Parse customerId and warehouseId out of the composite shipmentId key
		String[] keyParts = shipmentId.split("_");
		String customerId = keyParts[0];
		String warehouseId = keyParts[1];

		List<ShipmentLines> lines = shipmentLinesRepo.getShipmentLinesByShipmentId(shipmentId);
		log.debug("releaseInventory() → {} line(s) to process for shipmentId={}", lines.size(), shipmentId);

		for (ShipmentLines sl : lines) {
			String inventoryKey = sl.getItemId() + "_" + customerId + "_" + sl.getItemUom() + "_" + warehouseId;
			Inventory inv = inventoryRepo.findById(inventoryKey).get();

			// Always release the allocation reservation
			int newAllocated = inv.getAllocatedQuantity() - sl.getQuantity();
			inv.setAllocatedQuantity(newAllocated);

			if (shipStatus == 1400) {
				// Goods shipped: reduce physical on-hand stock as well
				int newQty = inv.getQuantity() - sl.getQuantity();
				inv.setQuantity(newQty);
				log.debug("releaseInventory() SHIP → inventoryKey={}, newAllocated={}, newOnHand={}", inventoryKey,
						newAllocated, newQty);
			} else {
				log.debug("releaseInventory() CANCEL → inventoryKey={}, newAllocated={}", inventoryKey, newAllocated);
			}

			inventoryRepo.save(inv);
		}
	}

	/**
	 * Queries inventory for a specific item-UOM combination and filters to only
	 * those warehouse records where availableQuantity >= requestedQty.
	 *
	 * availableQuantity is a derived field: quantity - allocatedQuantity.
	 */
	private List<Inventory> getWarehousesWithSufficientInventory(String customerId, String itemId, String itemUom,
			int requestedQty) {
		return inventoryRepo.getInventoryDetailsToVerifyAvailability(customerId, itemId, itemUom).stream()
				.filter(inv -> inv.getAvailableQuantity() >= requestedQty).collect(Collectors.toList());
	}

	/**
	 * Builds a user-facing HTML error message listing every failed precondition for
	 * a status update, with deep-links to the relevant admin pages.
	 */
	private String buildStatusUpdateErrorMessage(String shipmentId, String customerId, String warehouseId,
			boolean isCustomerValid, boolean isWarehouseActive, boolean isCustomerWarehouseLinked) {

		StringBuilder msg = new StringBuilder(
				"Shipment# " + shipmentId + " cannot be updated because of the following reason(s):\n");

		if (!isCustomerValid) {
			msg.append("\nThe customer is either Disabled or the contract has expired.")
					.append(" Please check >>> &nbsp;&nbsp;<a style=\"color: #edff2b;\""
							+ " href='/customers/showCustomerDetails/")
					.append(customerId).append("'>View ").append(customerId).append("</a>");
		}
		if (!isCustomerWarehouseLinked) {
			msg.append("\nThe customer does not have shipping enabled from warehouse: ").append(warehouseId)
					.append(" &nbsp;&nbsp;<a style=\"color: #edff2b;\"" + " href='/customers/showCustomerDetails/")
					.append(customerId).append("'>View ").append(customerId).append("</a>");
		}
		if (!isWarehouseActive) {
			msg.append("\nThe warehouse is Inactive/Disabled.")
					.append(" &nbsp;&nbsp;<a style=\"color: #edff2b;\"" + " href='/warehouses/viewOrEditWarehouse/")
					.append(warehouseId).append("'>View ").append(warehouseId).append("</a>");
		}

		return msg.toString();
	}

	/**
	 * Returns the itemId from a ShipmentLines object, substituting "UNKNOWN" if the
	 * field is null. Used to build safe composite map keys.
	 */
	private String safeItemId(ShipmentLines line) {
		return line.getItemId() != null ? line.getItemId() : "UNKNOWN";
	}

	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public List<Map<String, Object>> getShipmentsAlloactedForItem(String customerId, String itemId, String itemUom) {

	  String sql = """
	            SELECT s.shipment_id, s.customer_id, sl.item_id, sl.item_uom, sl.quantity, s.warehouse_id, s.ship_status 
	            FROM shipments s
	            JOIN shipment_lines sl ON s.shipment_id = sl.shipment_id
	            WHERE s.customer_id = :customerId
	              AND sl.item_id    = :itemId
	              AND sl.item_uom   = :itemUom
	              AND s.ship_status < 1400
	            """;

	    org.hibernate.query.NativeQuery<Map<String, Object>> nativeQuery = entityManager.createNativeQuery(sql)
	            .setParameter("customerId", customerId)
	            .setParameter("itemId", itemId)
	            .setParameter("itemUom", itemUom)
	            .unwrap(org.hibernate.query.NativeQuery.class);

	    return nativeQuery
	            .setTupleTransformer((tuple, aliases) -> {
	                Map<String, Object> row = new LinkedHashMap<>();
	                for (int i = 0; i < aliases.length; i++) {
	                    row.put(aliases[i], tuple[i]);
	                }
	                return row;
	            })
	            .getResultList();
	}

}