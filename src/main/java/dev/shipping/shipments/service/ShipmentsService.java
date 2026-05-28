package dev.shipping.shipments.service;

import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
import dev.shipping.shipments.model.Shipments;
import dev.shipping.shipments.model.Warehouses;
import dev.shipping.shipments.repo.CustomerWarehousesRepository;
import dev.shipping.shipments.repo.CustomersRepository;
import dev.shipping.shipments.repo.ShipmentsRepository;
import dev.shipping.shipments.repo.WarehousesRepository;
import jakarta.persistence.TypedQuery;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class ShipmentsService {

	@Autowired
	private EntityManager entityManager;

	private final ShipmentsRepository shipmentsRepo;
	private final CustomersRepository customersRepo;
	private final CustomerWarehousesRepository customerWarehousesRepo;
	private final WarehousesRepository warehousesRepo;
	private final SnsPublisherService snsService;
	private final DynamoDbService dynamoDbService;
	private final SqsSenderService sqsService;

	public ShipmentsService(ShipmentsRepository shipmentsRepo, CustomersRepository customersRepo,
			CustomerWarehousesRepository customerWarehousesRepo, WarehousesRepository warehousesRepo,
			DynamoDbService dynamoDbService, SnsPublisherService snsService, SqsSenderService sqsService) {
		this.shipmentsRepo = shipmentsRepo;
		this.customersRepo = customersRepo;
		this.customerWarehousesRepo = customerWarehousesRepo;
		this.warehousesRepo = warehousesRepo;
		this.dynamoDbService = dynamoDbService;
		this.snsService = snsService;
		this.sqsService = sqsService;
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public List<Shipments> getAllShipments() {
		return shipmentsRepo.findAll();
	}

	/**
	 * Returns null if not found — controller decides how to handle (redirect vs
	 * show).
	 */
	public Optional<Shipments> getShipmentById(String shipmentId) {
		return shipmentsRepo.findById(shipmentId) ;
	}

	/**
	 * Returns only Active customers whose validUpto is after the start of today.
	 */
	public List<Customers> getActiveAndValidCustomers() {
		LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
		return customersRepo.findByCustomerStatusAndValidUpto("Active", startOfToday);
	}

	public List<Warehouses> getWarehousesByCustomer(String customerId) {
		return customerWarehousesRepo.findAllocatedWarehousesByCustomerId(customerId);
	}

	public List<Map<String, String>> getShipmentAudit(String shipmentId) {
		return dynamoDbService.getShipmentAudiyByShipmentIdAsPartitionKey("ShipmentStatusUpdateEvents", shipmentId);
	}

	// ─────────────────────────────────────────────
	// CREATE
	// ─────────────────────────────────────────────
	/*
	 * Validates customer and warehouse, saves shipment, publishes SNS notification.
	 */

	@Transactional
	public String[] createShipment(Shipments shipment, String customerId, String warehouseId) {
		String errorMessage = "";
		boolean hasError = false;

		Optional<Customers> customer = customersRepo.findById(customerId);
		if (customer.isEmpty()) {
			return new String[] { "FAILED", "Customer: " + customerId + " is not found\n" };
		}

		// Check customer active status and contract validity
		boolean isCustomerActiveAndHasValidContract = false;
		if (customer.isPresent()) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
			LocalDateTime validUptoDateTime = LocalDateTime.parse(customer.get().getValidUpto(), formatter);
			LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
			String customerStatus = customer.get().getCustomerStatus();
			isCustomerActiveAndHasValidContract = validUptoDateTime.isAfter(startOfToday)
					&& customerStatus.equals("Active");
		}

		if (!isCustomerActiveAndHasValidContract) {
			return new String[] { "FAILED", "Customer: " + customerId
					+ " is either Disabled or Contacrt has expired!! &nbsp;&nbsp; <a target=\"_blank\" style=\"color: #4580ed;\" href='/customers/showCustomerDetails/"
					+ customerId + "'>View " + customerId + "</a>" };
		}

		//check if customer is configured with the selected warehouse
		Optional<CustomerWarehouses> customerWarehouses = customerWarehousesRepo
				.findById(customerId + "_" + warehouseId);

		if (customerWarehouses.isEmpty()) {
			return new String[] { "FAILED", "Customer: " + customerId + " is not configured to ship from " + warehouseId
					+ " warehouse !! &nbsp;&nbsp; <a target=\"_blank\" style=\"color: #4580ed;\" href='/customers/showCustomerDetails/"
					+ customerId + "'>View " + customerId + "</a>" };
		}

		// If no errors are found, then proceed to save the shipment 
		shipment.setCustomerId(customerId);
		shipment.setWarehouseId(warehouseId);

		Shipments savedShipment = shipmentsRepo.save(shipment);

		if (savedShipment != null && savedShipment.getShipmentId() != null) {
			snsService.publishShipmentStatus(savedShipment.getShipmentId(), "1100 - Created", "SHIPMENT_CREATED");
			sqsService.sendShipmentStatus(savedShipment.getShipmentId(), "1100 - Created", "SHIPMENT_CREATED");
			return new String[] { "SUCCESS", savedShipment.getShipmentId() };
		} else {
			return new String[] { "FAILED", "Failed to create shipment!" };
		}
	}

	// ─────────────────────────────────────────────
	// UPDATE STATUS
	// ─────────────────────────────────────────────

	/**
	 * Validates all preconditions (customer active + contract valid, warehouse
	 * active, customer-warehouse link exists), then applies the status transition
	 * and pushes to SQS.
	 *
	 * Returns "SHIPMENT_STATUS_UPDATED_SUCCESSFULLY" on success, or a descriptive
	 * error message (with HTML links) on failure.
	 */

	@Transactional
	public String updateShipmentStatus(String shipmentId, Integer shipStatus, String action, String customerId,
			String warehouseId, String cancellationReason) {

		Shipments shipment = shipmentsRepo.findById(shipmentId)
				.orElseThrow(() -> new RuntimeException("Shipment not found"));

		Optional<Customers> singleCustomer = customersRepo.findById(customerId);
		Optional<Warehouses> singleWarehouse = warehousesRepo.findById(warehouseId);
		Optional<CustomerWarehouses> customerWarehouses = customerWarehousesRepo
				.findById(customerId + "_" + warehouseId);

		boolean isWarehouseActive = singleWarehouse.isPresent()
				&& singleWarehouse.get().getWarehouseStatus().equals("Active");

		// Check customer active status and contract validity
		boolean isCustomerActiveAndHasValidContract = false;
		if (singleCustomer.isPresent()) {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
			LocalDateTime validUptoDateTime = LocalDateTime.parse(singleCustomer.get().getValidUpto(), formatter);
			LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
			String customerStatus = singleCustomer.get().getCustomerStatus();
			isCustomerActiveAndHasValidContract = validUptoDateTime.isAfter(startOfToday)
					&& customerStatus.equals("Active");
		}

		boolean allConditionsMet = singleCustomer.isPresent() && customerWarehouses.isPresent() && isWarehouseActive
				&& isCustomerActiveAndHasValidContract;

		if (allConditionsMet) {
			String shipmentStatusAndDesc, reason, updatedStatus = "SHIPMENT_STATUS_UPDATED";

			switch (action) {
			case "PICK":
				shipment.setShipStatus(1200);
				shipmentStatusAndDesc = "1200 - PICKED";
				updatedStatus = reason = "SHIPMENT_PICKED";
				break;
			case "PACK":
				shipment.setShipStatus(1300);
				shipmentStatusAndDesc = "1300 - PACKED";
				updatedStatus = reason = "SHIPMENT_PACKED";
				break;
			case "SHIP":
				shipment.setShipStatus(1400);
				shipmentStatusAndDesc = "1400 - SHIPPED";
				updatedStatus = reason = "SHIPMENT_SHIPPED";
				break;
			case "CANCEL":
				shipment.setShipStatus(9000);
				shipmentStatusAndDesc = "9000 - CANCELLED";
				updatedStatus = "SHIPMENT_CANCELLED";
				reason = cancellationReason; // setting cancellation reason entered by user in dynamodb reason col
				break;
			default:
				throw new RuntimeException("Invalid action: " + action);
			}

			sqsService.sendShipmentStatus(shipmentId, shipmentStatusAndDesc, reason);
			shipmentsRepo.save(shipment);
			return updatedStatus + "_SUCCESSFULLY";

		} else {
			// Build a specific error message for each failed condition
			StringBuilder errorMessage = new StringBuilder(
					"Shipment# " + shipmentId + " cannot be updated because of below reason(s) \n");

			if (!singleCustomer.isPresent()) {
				errorMessage.append("Customer: ").append(customerId).append(" is not found !!");
			}

			if (!isCustomerActiveAndHasValidContract) {
				errorMessage.append("\nEither the Customer is Disabled or the Customer's contract has expired!!")
						.append(" Please check and update it >>> ")
						.append("&nbsp;&nbsp; <a style=\"color: #edff2b;\" href='/customers/showCustomerDetails/")
						.append(customerId).append("'>View ").append(customerId).append("</a>");
			}

			if (!customerWarehouses.isPresent()) {
				errorMessage.append("\nThe customer does not have shipping enabled from this warehouse: ")
						.append(warehouseId)
						.append("&nbsp;&nbsp;&nbsp;&nbsp;<a style=\"color: #edff2b;\" href='/customers/showCustomerDetails/")
						.append(customerId).append("'>View ").append(customerId).append("</a>");
			}

			if (!isWarehouseActive) {
				errorMessage.append("\nThe warehouse is Inactive/Disabled").append(
						"&nbsp;&nbsp;&nbsp;&nbsp;<a style=\"color: #edff2b;\" href='/warehouses/viewOrEditWarehouse/")
						.append(warehouseId).append("'>View ").append(warehouseId).append("</a>");
			}

			return errorMessage.toString();
		}
	}

	public List<Shipments> getShipmentsByCustomer(String customerId) {
		return shipmentsRepo.findShipmentsByCustomer(customerId);
	}

	public List<Shipments> getShipmentsByCustomerAndWarehouse(String customerId, String warehouseId) {
		return shipmentsRepo.findShipmentsByCustomerAndWarehouse(customerId, warehouseId);
	}

	// Dynamically setting the conditions and running a custom query in service
	// instead of calling individual methods in Repo
	@Transactional
	public List<Shipments> getShipmentDetails(String customerId, String warehouseId, String shipStatus) {

		StringBuilder query = new StringBuilder("SELECT s FROM Shipments s");

		// Dynamically build WHERE clause
		List<String> conditions = new ArrayList<>();

		if (!customerId.equals("ALL"))
			conditions.add("s.customerId = :customerId");
		if (!warehouseId.equals("ALL"))
			conditions.add("s.warehouseId = :warehouseId");
		if (!shipStatus.equals("ALL"))
			conditions.add("s.shipStatus = :shipStatus");

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// Create query
		TypedQuery<Shipments> typedQuery = entityManager.createQuery(query.toString(), Shipments.class);

		// Bind only non-null parameters
		if (!customerId.equals("ALL"))
			typedQuery.setParameter("customerId", customerId);
		if (!warehouseId.equals("ALL"))
			typedQuery.setParameter("warehouseId", warehouseId);
		if (!shipStatus.equals("ALL"))
			typedQuery.setParameter("shipStatus", shipStatus);

		List<Shipments> resultList = typedQuery.getResultList();

		System.out.println("final Query: " + query.toString() + "\nresultList: " + resultList.toString());

		return resultList;
	}

}