package dev.shipping.shipments.service;

import dev.shipping.shipments.model.Address;
import dev.shipping.shipments.model.CustomerWarehouses;
import dev.shipping.shipments.model.Customers;
import dev.shipping.shipments.model.Inventory;
import dev.shipping.shipments.model.Items;
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

	public ShipmentsService(ShipmentsRepository shipmentsRepo, CustomersRepository customersRepo,
			CustomerWarehousesRepository customerWarehousesRepo, WarehousesRepository warehousesRepo,
			InventoryRepository inventoryRepo, ShipmentLinesRepository shipmentLinesRepo,
			DynamoDbService dynamoDbService, SnsPublisherService snsService, SqsSenderService sqsService,
			ShipmentLinesService shipmentLinesService, AddressRepository addressRepo) {
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
	}

	// ─────────────────────────────────────────────
	// READ
	// ─────────────────────────────────────────────

	public List<Shipments> getAllShipments() {
		return shipmentsRepo.findAll();
	}

	public List<Shipments> getAllShipmentsByCreatedTimeDesc() {
		return shipmentsRepo.getAllShipmentsByCreatedTimeDesc();
	}

	/**
	 * Returns null if not found — controller decides how to handle (redirect vs
	 * show).
	 */
	public Optional<Shipments> getShipmentById(String shipmentId) {
		return shipmentsRepo.findById(shipmentId);
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

		// check if customer is configured with the selected warehouse
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
			snsService.publishShipmentStatus(savedShipment.getShipmentId(), "1100 - CREATED",
					"SHIPMENT_CREATED_SUCCESSFULLY");
			sqsService.sendShipmentStatus(savedShipment.getShipmentId(), "1100 - CREATED",
					"SHIPMENT_CREATED_SUCCESSFULLY");
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

		// Immediately return if the customer is not found
		if (!singleCustomer.isPresent()) {
			return "Customer: " + customerId + " is not found !!";
		}

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
			String shipmentStatusAndDesc, comment, updatedStatus = "SHIPMENT_STATUS_UPDATED";

			switch (action) {
			case "PICK":
				shipment.setShipStatus(1200);
				shipmentStatusAndDesc = "1200 - PICKED";
				updatedStatus = comment = "SHIPMENT_PICKED_SUCCESSFULLY";
				break;
			case "PACK":
				shipment.setShipStatus(1300);
				shipmentStatusAndDesc = "1300 - PACKED";
				updatedStatus = comment = "SHIPMENT_PACKED_SUCCESSFULLY";
				break;
			case "SHIP":
				shipment.setShipStatus(1400);
				shipmentStatusAndDesc = "1400 - SHIPPED";
				updatedStatus = comment = "SHIPMENT_SHIPPED_SUCCESSFULLY";
				updatedQuantityFromInventoryWhenShipmentIsShippedOrCancelled(shipmentId, 1400);
				break;
			case "CANCEL":
				shipment.setShipStatus(9000);
				shipmentStatusAndDesc = "9000 - CANCELLED";
				updatedStatus = "SHIPMENT_CANCELLED";
				comment = "SHIPMENT_CANCELLED with reason: " + cancellationReason; // setting cancellation comment
																					// entered by user in dynamodb
																					// comment col
				updatedQuantityFromInventoryWhenShipmentIsShippedOrCancelled(shipmentId, 9000);
				break;
			default:
				throw new RuntimeException("Invalid action: " + action);
			}

			sqsService.sendShipmentStatus(shipmentId, shipmentStatusAndDesc, comment);
			shipmentsRepo.save(shipment);
			return updatedStatus;

		} else {
			// Build a specific error message for each failed condition
			StringBuilder errorMessage = new StringBuilder(
					"Shipment# " + shipmentId + " cannot be updated because of below comment(s) \n");

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

	private void updatedQuantityFromInventoryWhenShipmentIsShippedOrCancelled(String shipmentId, int shipStatus) {
		List<ShipmentLines> shipmentLines = shipmentLinesRepo.getShipmentLinesByShipmentId(shipmentId);
		String itemCustomerUomWarehouseId, itemId, customerId, itemUom, warehouseId;
		int shipmentLineQuantity, currentAllocatedQuantity, currentInventoryQuantity;

		String[] keyParts = shipmentId.split("_");

		customerId = keyParts[0];
		warehouseId = keyParts[1];

		for (ShipmentLines sl : shipmentLines) {
			itemId = sl.getItemId();
			itemUom = sl.getItemUom();
			shipmentLineQuantity = sl.getQuantity();

			itemCustomerUomWarehouseId = itemId + "_" + customerId + "_" + itemUom + "_" + warehouseId;

			Inventory inv = inventoryRepo.findById(itemCustomerUomWarehouseId).get();

			currentAllocatedQuantity = inv.getAllocatedQuantity();
			inv.setAllocatedQuantity(currentAllocatedQuantity - shipmentLineQuantity);

			if (shipStatus == 1400) {
				currentInventoryQuantity = inv.getQuantity();
				inv.setQuantity(currentInventoryQuantity - shipmentLineQuantity);
			}

			inventoryRepo.save(inv);

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
	public List<Shipments> getShipmentList(String shipmentId, String customerId, String warehouseId,
			String shipStatus) {

		StringBuilder query = new StringBuilder("SELECT s FROM Shipments s");

		// Dynamically build WHERE clause
		List<String> conditions = new ArrayList<>();

		if (!shipmentId.equals("ALL"))
			conditions.add("s.shipmentId = :shipmentId");
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
		if (!shipmentId.equals("ALL"))
			typedQuery.setParameter("shipmentId", shipmentId);
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

	// Dynamically setting the conditions and running a custom query in service
	// instead of calling individual methods in Repo
	@Transactional
	public List<Shipments> getShipmentListByAdvancedFilters(String shipmentId, String customerId, String warehouseId,
			String statusFrom, String statusTo, String dateFrom, String dateTo, String itemId) {

		StringBuilder query = new StringBuilder("SELECT s FROM Shipments s");

		// Dynamically build WHERE clause
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

		// shipmentlines table is yet to be created
		/*
		 * THIS WILL BE UNCOMMENTED WHEN SHIPMENTLINES TABLE IS CREATED if
		 * (!itemId.equals("ALL")) conditions.
		 * add("s.shipmentId in ( select sl.shipmentId from shipmentlines sl where sl.itemId= :itemId )"
		 * );
		 * 
		 */

		// Append WHERE + AND automatically
		if (!conditions.isEmpty()) {
			query.append(" WHERE ").append(String.join(" AND ", conditions));
		}

		// Create query
		TypedQuery<Shipments> typedQuery = entityManager.createQuery(query.toString(), Shipments.class);

		// Bind only non-null parameters
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

		/// Converting the string to DB compatible LocalDateTime and taking
		/// it as start-of-day, so that the string 2026-05-29 will be
		/// considered as 2026-05-29 00:00:00
		if (!dateFrom.equals("ALL"))
			typedQuery.setParameter("dateFrom", LocalDate.parse(dateFrom).atStartOfDay());

		// Converting the string to DB compatible LocalDateTime and taking it as
		// start-of-day of next day, so that the string 2026-05-29 will be considered as
		// 2026-05-30 00:00:00, so that it includes data of 2026-05-29 as well
		if (!dateTo.equals("ALL"))
			typedQuery.setParameter("dateTo", LocalDate.parse(dateTo).plusDays(1).atStartOfDay());

		/*
		 * THIS WILL BE UNCOMMENTED WHEN SHIPMENTLINES TABLE IS CREATED if
		 * (!itemId.equals("ALL")) typedQuery.setParameter("itemId", itemId);
		 */
		System.out.println("getShipmentListByAdvancedFilters()::: FinalQuery: \n" + query.toString());

		List<Shipments> resultList = typedQuery.getResultList();

		System.out.println("final Query: " + query.toString() + "\nresultList: " + resultList.toString());

		return resultList;

	}

	public List<Inventory> checkInventoryAvailability(String customerId, String itemId, String itemUom,
			int requestedQty) {

		List<Inventory> inventory = inventoryRepo.getInventoryDetailsToVerifyAvailability(customerId, itemId, itemUom);
		List<Inventory> inventoryAvailableInWarehouses = new ArrayList<Inventory>();

		for (Inventory inv : inventory) {
			if (inv.getAvailableQuantity() >= requestedQty) {
				inventoryAvailableInWarehouses.add(inv);
			}
		}

		return inventoryAvailableInWarehouses;
	}

	public void updateInventoryAllocatedQuantity(String itemCustomerUomWarehouseId, int shipmentLineQuantity) {

		Inventory inv = inventoryRepo.findById(itemCustomerUomWarehouseId).get();
		int currentAllocatedQuantity = inv.getAllocatedQuantity();
		inv.setAllocatedQuantity(currentAllocatedQuantity + shipmentLineQuantity);
		inventoryRepo.save(inv);
		// inventoryRepo.updateInventoryAllocatedQuantity(itemCustomerUomWarehouseId,
		// shipmentLineQuantity);

	}

	public String createShipmentLines(String customerId, String warehouseId, String shipmentId,
			List<ShipmentLines> lines) {

		int shipmentLineNo = 0, quantity = 0;
		String itemId = "NO_ITEM_ID", itemUom = "NO_ITEM_UOM", itemCustomerUomWarehouseId = null,
				createdShipmentLineId = null;
		for (ShipmentLines line : lines) {

			shipmentLineNo = line.getLineNo();
			itemId = line.getItemId();
			itemUom = line.getItemUom();
			quantity = line.getQuantity();

			ShipmentLines shipmentLinesToCreate = new ShipmentLines();
			shipmentLinesToCreate.setShipmentId(shipmentId);
			shipmentLinesToCreate.setLineNo(shipmentLineNo);
			shipmentLinesToCreate.setItemId(itemId);
			shipmentLinesToCreate.setItemUom(itemUom);
			shipmentLinesToCreate.setQuantity(quantity);
			shipmentLinesToCreate.setShortageQuantity(0);

			createdShipmentLineId = shipmentLinesService.createShipmentLines(shipmentLinesToCreate);

			if (!(createdShipmentLineId == null)) {
				System.out.println("SHIPMENT_LINE_CREATED_WITH_ID: " + createdShipmentLineId
						+ "\nUPDATING INVENTORY_ALLOCATED_QTY");
				itemCustomerUomWarehouseId = itemId + "_" + customerId + "_" + itemUom + "_" + warehouseId;
				updateInventoryAllocatedQuantity(itemCustomerUomWarehouseId, quantity);
			} else {
				System.out.println("FAILED_TO_CREATE_SHIPMENT_LINE");
				return "SHIPMENT_CREATEION_FAILED : FAILED_TO_CREATE_SHIPMENT_LINE FOR ITEM: " + itemId + ", UOM: "
						+ itemUom + ", QTY: " + quantity;
			}
		}
		return "SHIPMENT_LINES_CREATED_SUCCESSFULLY";
	}

	public void createAddress(String shipmentId, Address deliveryAddress) {

		System.out.println("ShipmentsService createAddress():: deliveryAddress: " + deliveryAddress.toString());

		Address address = new Address();

		address.setAddress1(deliveryAddress.getAddress1());
		address.setAddress2(deliveryAddress.getAddress2());
		address.setCountry(deliveryAddress.getCountry());
		address.setDistrict(deliveryAddress.getDistrict());
		address.setTaluk(deliveryAddress.getTaluk());
		address.setFirstName(deliveryAddress.getFirstName());
		address.setLastName(deliveryAddress.getLastName());
		address.setState(deliveryAddress.getState());
		address.setZipCode(deliveryAddress.getZipCode());

		String addressId = addressRepo.save(address).getAddressId();

		if (!(addressId.isEmpty()) && addressId != null) {
			Optional<Shipments> optShipment = shipmentsRepo.findById(shipmentId);

			if (optShipment.isPresent()) {
				Shipments shipment = optShipment.get();
				shipment.setAddressId(addressId);
				shipmentsRepo.save(shipment);
			}

		}

	}

	public Optional<Address> getShipingAddressById(String addressId) {
		return addressRepo.findById(addressId);
	}

	public String getFormattedAddress(Address address) {
		StringBuilder sb = new StringBuilder();

		sb.append(address.getFirstName())
		  .append(", ")
		  .append(address.getLastName())
		  .append("<br />")
		  .append(address.getAddress1());

		if (address.getAddress2() != null && !address.getAddress2().isBlank()) {
		    sb.append("<br />").append(address.getAddress2());
		}

		sb.append("<br />")
		  .append(address.getTaluk()).append(", ")
		  .append(address.getDistrict()).append(", ")
		  .append(address.getState()).append(", ")
		  .append(address.getCountry()).append(" - ")
		  .append(address.getZipCode());

		String formattedAddress = sb.toString();
		return formattedAddress;
	}

}